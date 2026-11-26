package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class TypingEvent(
    val conversationId: String,
    val userId: String,
    val isTyping: Boolean
)

/**
 * Lightweight OkHttp WebSocket client for Supabase Realtime (Phoenix v1 protocol).
 * Subscribes to INSERT events on `public.messages`, global user presence (`realtime:online_users`),
 * and per-conversation typing broadcast channels (`realtime:chat:<conversationId>`).
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

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 32)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null
    private val connectionGen = AtomicInteger(0)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var tokenProvider: (suspend () -> String)? = null
    private var reconnectAttempt = 0
    @Volatile
    private var currentUserId: String = ""
    @Volatile
    private var currentAccessToken: String = ""
    @Volatile
    private var activeConversationChannelId: String? = null
    @Volatile
    private var presenceJoinRef: String? = null

    fun connect(
        accessToken: String,
        userId: String = "",
        refreshTokenProvider: (suspend () -> String)? = null
    ) {
        if (supabaseAnonKey.isBlank() || accessToken.isBlank()) return
        currentUserId = userId
        tokenProvider = refreshTokenProvider ?: { accessToken }
        reconnectAttempt = 0
        openSocket(accessToken)
    }

    private fun openSocket(accessToken: String) {
        if (supabaseAnonKey.isBlank() || accessToken.isBlank()) return
        currentAccessToken = accessToken
        presenceJoinRef = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Reconnect")
        webSocket = null
        val myGen = connectionGen.incrementAndGet()

        val wsBase = supabaseUrl
            .trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (connectionGen.get() != myGen) {
                    webSocket.close(1000, "Superseded")
                    return
                }
                reconnectAttempt = 0
                Log.d(TAG, "WebSocket opened")
                val joinPayload = buildJoinMessage(accessToken, refCounter.getAndIncrement())
                webSocket.send(joinPayload)
                val uid = currentUserId
                if (uid.isNotBlank()) {
                    val pRef = refCounter.getAndIncrement()
                    presenceJoinRef = pRef.toString()
                    webSocket.send(buildPresenceJoinMessage(accessToken, uid, pRef))
                }
                val convId = activeConversationChannelId
                if (!convId.isNullOrBlank()) {
                    webSocket.send(buildChatChannelJoinMessage(accessToken, convId, refCounter.getAndIncrement()))
                }
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (connectionGen.get() != myGen) return
                logFrameSummary(text)
                if (isPresenceJoinOk(text, presenceJoinRef)) {
                    presenceJoinRef = null
                    val uid = currentUserId
                    if (uid.isNotBlank()) {
                        webSocket.send(buildPresenceTrackMessage(uid, refCounter.getAndIncrement()))
                    }
                }
                parseConversationIdFromEvent(text)?.let { convId ->
                    _messageEvents.tryEmit(convId)
                }
                parsePresenceState(text)?.let { online ->
                    _onlineUserIds.value = online
                }
                applyPresenceDiff(_onlineUserIds.value, text)?.let { updated ->
                    _onlineUserIds.value = updated
                }
                parseTypingEvent(text)?.let { typing ->
                    _typingEvents.tryEmit(typing)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (connectionGen.get() != myGen) return
                Log.w(TAG, "WebSocket failure: ${t.message}")
                heartbeatJob?.cancel()
                scheduleReconnect(myGen)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (connectionGen.get() != myGen) return
                Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                heartbeatJob?.cancel()
                if (code != 1000) scheduleReconnect(myGen)
            }
        })
    }

    private fun scheduleReconnect(expectedGen: Int) {
        val provider = tokenProvider ?: return
        reconnectJob?.cancel()
        val delayMs = computeBackoffDelayMs(reconnectAttempt++)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (connectionGen.get() != expectedGen) return@launch
            val freshToken = runCatching { provider() }.getOrDefault("")
            if (freshToken.isNotBlank() && connectionGen.get() == expectedGen) {
                openSocket(freshToken)
            }
        }
    }

    fun joinConversationChannel(conversationId: String) {
        if (conversationId.isBlank()) return
        val prev = activeConversationChannelId
        if (prev == conversationId) return
        val ws = webSocket
        if (!prev.isNullOrBlank() && ws != null) {
            ws.send(buildLeaveMessage("realtime:chat:$prev", refCounter.getAndIncrement()))
        }
        activeConversationChannelId = conversationId
        val token = currentAccessToken
        if (ws != null && token.isNotBlank()) {
            ws.send(buildChatChannelJoinMessage(token, conversationId, refCounter.getAndIncrement()))
        }
    }

    fun leaveConversationChannel(conversationId: String) {
        if (conversationId.isBlank()) return
        if (activeConversationChannelId == conversationId) {
            activeConversationChannelId = null
            webSocket?.send(buildLeaveMessage("realtime:chat:$conversationId", refCounter.getAndIncrement()))
        }
    }

    fun sendTyping(conversationId: String, userId: String, isTyping: Boolean) {
        if (conversationId.isBlank() || userId.isBlank()) return
        val ws = webSocket ?: return
        ws.send(buildTypingBroadcastMessage(conversationId, userId, isTyping, refCounter.getAndIncrement()))
        ws.send(buildGlobalTypingBroadcastMessage(conversationId, userId, isTyping, refCounter.getAndIncrement()))
    }

    fun disconnect() {
        connectionGen.incrementAndGet()
        tokenProvider = null
        currentUserId = ""
        currentAccessToken = ""
        activeConversationChannelId = null
        presenceJoinRef = null
        _onlineUserIds.value = emptySet()
        reconnectJob?.cancel()
        reconnectJob = null
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
        private const val TAG = "KartRealtime"

        private fun logFrameSummary(rawJson: String) {
            runCatching {
                val root = JSONObject(rawJson)
                val event = root.optString("event")
                val topic = root.optString("topic")
                if (event == "phx_reply" && topic != "phoenix") {
                    val status = root.optJSONObject("payload")?.optString("status")
                    Log.d(TAG, "phx_reply topic=$topic status=$status")
                } else if (event == "phx_error" || event == "phx_close") {
                    Log.w(TAG, "$event topic=$topic")
                }
            }
        }

        internal fun computeBackoffDelayMs(attempt: Int): Long {
            val cappedAttempt = attempt.coerceIn(0, 4)
            return (2_000L shl cappedAttempt).coerceAtMost(30_000L)
        }

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
                .put("join_ref", ref.toString())
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

        internal fun buildPresenceJoinMessage(accessToken: String, userId: String, ref: Int): String {
            val broadcastConfig = JSONObject()
                .put("ack", false)
                .put("self", false)
            val presenceConfig = JSONObject()
                .put("key", userId)
                .put("enabled", true)
            val config = JSONObject()
                .put("broadcast", broadcastConfig)
                .put("presence", presenceConfig)
                .put("private", false)
            val payload = JSONObject()
                .put("config", config)
                .put("access_token", accessToken)
            return JSONObject()
                .put("topic", "realtime:online_users")
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", ref.toString())
                .put("join_ref", ref.toString())
                .toString()
        }

        internal fun buildPresenceTrackMessage(userId: String, ref: Int): String {
            val innerPayload = JSONObject().put("user_id", userId)
            val payload = JSONObject()
                .put("type", "presence")
                .put("event", "track")
                .put("payload", innerPayload)
            return JSONObject()
                .put("topic", "realtime:online_users")
                .put("event", "presence")
                .put("payload", payload)
                .put("ref", ref.toString())
                .toString()
        }

        internal fun isPresenceJoinOk(rawJson: String, expectedRef: String?): Boolean {
            if (expectedRef.isNullOrBlank()) return false
            return runCatching {
                val root = JSONObject(rawJson)
                root.optString("topic") == "realtime:online_users" &&
                    root.optString("event") == "phx_reply" &&
                    root.optString("ref") == expectedRef &&
                    root.optJSONObject("payload")?.optString("status") == "ok"
            }.getOrDefault(false)
        }

        internal fun buildChatChannelJoinMessage(accessToken: String, conversationId: String, ref: Int): String {
            val broadcastConfig = JSONObject()
                .put("ack", false)
                .put("self", false)
            val config = JSONObject()
                .put("broadcast", broadcastConfig)
                .put("presence", JSONObject().put("key", ""))
                .put("private", false)
            val payload = JSONObject()
                .put("config", config)
                .put("access_token", accessToken)
            return JSONObject()
                .put("topic", "realtime:chat:$conversationId")
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", ref.toString())
                .put("join_ref", ref.toString())
                .toString()
        }

        internal fun buildLeaveMessage(topic: String, ref: Int): String {
            return JSONObject()
                .put("topic", topic)
                .put("event", "phx_leave")
                .put("payload", JSONObject())
                .put("ref", ref.toString())
                .toString()
        }

        internal fun buildTypingBroadcastMessage(
            conversationId: String,
            userId: String,
            isTyping: Boolean,
            ref: Int
        ): String {
            val innerPayload = JSONObject()
                .put("user_id", userId)
                .put("is_typing", isTyping)
            val payload = JSONObject()
                .put("type", "broadcast")
                .put("event", "typing")
                .put("payload", innerPayload)
            return JSONObject()
                .put("topic", "realtime:chat:$conversationId")
                .put("event", "broadcast")
                .put("payload", payload)
                .put("ref", ref.toString())
                .toString()
        }

        internal fun buildGlobalTypingBroadcastMessage(
            conversationId: String,
            userId: String,
            isTyping: Boolean,
            ref: Int
        ): String {
            val innerPayload = JSONObject()
                .put("conversation_id", conversationId)
                .put("user_id", userId)
                .put("is_typing", isTyping)
            val payload = JSONObject()
                .put("type", "broadcast")
                .put("event", "typing")
                .put("payload", innerPayload)
            return JSONObject()
                .put("topic", "realtime:online_users")
                .put("event", "broadcast")
                .put("payload", payload)
                .put("ref", ref.toString())
                .toString()
        }

        internal fun parsePresenceState(rawJson: String): Set<String>? {
            return runCatching {
                val root = JSONObject(rawJson)
                if (root.optString("event") != "presence_state") return null
                val payload = root.optJSONObject("payload") ?: return emptySet()
                val keys = mutableSetOf<String>()
                val iter = payload.keys()
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (key.isNotBlank()) keys.add(key)
                }
                keys
            }.getOrNull()
        }

        internal fun applyPresenceDiff(current: Set<String>, rawJson: String): Set<String>? {
            return runCatching {
                val root = JSONObject(rawJson)
                if (root.optString("event") != "presence_diff") return null
                val payload = root.optJSONObject("payload") ?: return null
                val next = current.toMutableSet()
                payload.optJSONObject("joins")?.keys()?.forEach { key ->
                    if (key.isNotBlank()) next.add(key)
                }
                payload.optJSONObject("leaves")?.keys()?.forEach { key ->
                    next.remove(key)
                }
                next
            }.getOrNull()
        }

        internal fun parseTypingEvent(rawJson: String): TypingEvent? {
            return runCatching {
                val root = JSONObject(rawJson)
                if (root.optString("event") != "broadcast") return null
                val topic = root.optString("topic", "")
                val payload = root.optJSONObject("payload") ?: return null
                if (payload.optString("event") != "typing") return null
                val data = payload.optJSONObject("payload") ?: return null
                val conversationId = when {
                    topic.startsWith("realtime:chat:") -> topic.removePrefix("realtime:chat:")
                    topic == "realtime:online_users" -> data.optString("conversation_id", "")
                    else -> ""
                }
                if (conversationId.isBlank()) return null
                val userId = data.optString("user_id", "")
                if (userId.isBlank()) return null
                val isTyping = data.optBoolean("is_typing", false)
                TypingEvent(conversationId = conversationId, userId = userId, isTyping = isTyping)
            }.getOrNull()
        }
    }
}

/**
 * Dedicated Supabase Realtime WebSocket client for the Admin Console.
 * Subscribes to postgres_changes on orders, profiles, products, and moderation queues.
 */
class SupabaseAdminRealtimeClient(
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

    private val _tableEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val tableEvents: SharedFlow<String> = _tableEvents.asSharedFlow()

    private val _connected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val connected: kotlinx.coroutines.flow.StateFlow<Boolean> = _connected

    @Volatile
    private var webSocket: WebSocket? = null
    private val connectionGen = AtomicInteger(0)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var tokenProvider: (suspend () -> String)? = null
    private var reconnectAttempt = 0

    fun connect(accessToken: String, refreshTokenProvider: (suspend () -> String)? = null) {
        if (supabaseAnonKey.isBlank() || accessToken.isBlank()) return
        tokenProvider = refreshTokenProvider ?: { accessToken }
        reconnectAttempt = 0
        openSocket(accessToken)
    }

    private fun openSocket(accessToken: String) {
        if (supabaseAnonKey.isBlank() || accessToken.isBlank()) return
        _connected.value = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Admin reconnect")
        webSocket = null
        val myGen = connectionGen.incrementAndGet()

        val wsBase = supabaseUrl
            .trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (connectionGen.get() != myGen) {
                    webSocket.close(1000, "Superseded")
                    return
                }
                reconnectAttempt = 0
                _connected.value = true
                webSocket.send(buildAdminJoinMessage(accessToken, refCounter.getAndIncrement()))
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (connectionGen.get() != myGen) return
                parseChangedTable(text)?.let { table ->
                    _tableEvents.tryEmit(table)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (connectionGen.get() != myGen) return
                _connected.value = false
                heartbeatJob?.cancel()
                scheduleReconnect(myGen)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (connectionGen.get() != myGen) return
                _connected.value = false
                heartbeatJob?.cancel()
                if (code != 1000) scheduleReconnect(myGen)
            }
        })
    }

    private fun scheduleReconnect(expectedGen: Int) {
        val provider = tokenProvider ?: return
        reconnectJob?.cancel()
        val delayMs = SupabaseRealtimeClient.computeBackoffDelayMs(reconnectAttempt++)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (connectionGen.get() != expectedGen) return@launch
            val freshToken = runCatching { provider() }.getOrDefault("")
            if (freshToken.isNotBlank() && connectionGen.get() == expectedGen) {
                openSocket(freshToken)
            }
        }
    }

    fun disconnect() {
        connectionGen.incrementAndGet()
        tokenProvider = null
        reconnectJob?.cancel()
        reconnectJob = null
        _connected.value = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "Admin disconnect")
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
        private val WATCHED_TABLES = listOf(
            "orders",
            "profiles",
            "products",
            "verification_requests",
            "withdrawal_requests",
            "refund_requests",
            "reports"
        )

        internal fun buildAdminJoinMessage(accessToken: String, ref: Int): String {
            val filters = JSONArray()
            WATCHED_TABLES.forEach { table ->
                filters.put(
                    JSONObject()
                        .put("event", "*")
                        .put("schema", "public")
                        .put("table", table)
                )
            }
            val config = JSONObject().put("postgres_changes", filters)
            val payload = JSONObject()
                .put("config", config)
                .put("access_token", accessToken)
            return JSONObject()
                .put("topic", "realtime:public:admin_console")
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", ref.toString())
                .toString()
        }

        internal fun parseChangedTable(rawJson: String): String? {
            return runCatching {
                val root = JSONObject(rawJson)
                if (root.optString("event") != "postgres_changes") return null
                val data = root.optJSONObject("payload")?.optJSONObject("data") ?: return null
                data.optString("table", "").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
}
