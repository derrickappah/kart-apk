package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight OkHttp WebSocket client for Supabase Realtime (Phoenix v1 protocol).
 * Subscribes to INSERT events on `public.messages` and emits affected conversation IDs.
 */
class SupabaseRealtimeClient(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refCounter = AtomicInteger(1)

    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _messageEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    fun connect(accessToken: String) {
        if (supabaseAnonKey.isBlank() || accessToken.isBlank()) return
        disconnect()

        val wsBase = supabaseUrl
            .trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinPayload = buildJoinMessage(accessToken, refCounter.getAndIncrement())
                webSocket.send(joinPayload)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseConversationIdFromEvent(text)?.let { convId ->
                    _messageEvents.tryEmit(convId)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
            }
        })
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000L)
                val hb = JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", JSONObject())
                    .put("ref", refCounter.getAndIncrement().toString())
                ws.send(hb.toString())
            }
        }
    }

    companion object {
        internal fun buildJoinMessage(accessToken: String, ref: Int): String {
            val changeFilter = JSONObject()
                .put("event", "INSERT")
                .put("schema", "public")
                .put("table", "messages")
            val config = JSONObject()
                .put("postgres_changes", JSONArray().put(changeFilter))
            val payload = JSONObject()
                .put("config", config)
                .put("access_token", accessToken)
            return JSONObject()
                .put("topic", "realtime:public:messages")
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", ref.toString())
                .toString()
        }

        internal fun parseConversationIdFromEvent(rawJson: String): String? {
            return runCatching {
                val root = JSONObject(rawJson)
                if (root.optString("event") != "postgres_changes") return null
                val data = root.optJSONObject("payload")?.optJSONObject("data") ?: return null
                val record = data.optJSONObject("record") ?: return null
                record.optString("conversation_id", "").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
}
