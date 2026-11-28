package com.example.notifications

import com.example.KartApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * In-process bus notifying foreground ViewModels immediately when an FCM push arrives,
 * eliminating aggressive 15-second unread polling loops.
 */
object KartPushEventBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun emit(type: String) {
        _events.tryEmit(type)
    }
}

/**
 * Handles incoming Firebase Cloud Messaging (FCM) push notifications when the app is
 * in the foreground, background, or terminated, and syncs refreshed FCM tokens to Supabase.
 */
class KartFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? KartApplication ?: return
        serviceScope.launch {
            val session = app.container.sessionStore.currentSession()
            val pushAllowed = app.container.sessionStore.isPushEnabled()
            if (session.isLoggedIn && pushAllowed) {
                app.container.repository.registerFcmToken(session.userId, token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val app = application as? KartApplication ?: return
        val data = remoteMessage.data
        val title = data["title"]
            ?: remoteMessage.notification?.title
            ?: "Kart"
        val body = data["body"]
            ?: remoteMessage.notification?.body
            ?: return

        val type = data["type"]?.lowercase().orEmpty()
        val isMessage = type == "message" || type == "chat"
        KartPushEventBus.emit(if (isMessage) "message" else "update")
        val channelId = if (isMessage) {
            KartNotificationHelper.CHANNEL_MESSAGES
        } else {
            KartNotificationHelper.CHANNEL_UPDATES
        }

        val deepLink = data["deep_link"] ?: data["link"]
        val rawId = data["id"]?.trim().orEmpty()
        val notifId = when {
            isMessage -> 1001
            rawId.isNotBlank() -> rawId.hashCode()
            else -> "${title}_${body}".hashCode()
        }

        runCatching {
            runBlocking {
                val session = app.container.sessionStore.currentSession()
                val pushAllowed = app.container.sessionStore.isPushEnabled()
                if (!session.isLoggedIn || !pushAllowed) return@runBlocking

                val targetUserId = data["user_id"]?.trim()
                if (!targetUserId.isNullOrBlank() && targetUserId != session.userId) return@runBlocking

                KartNotificationHelper.showNotification(
                    context = applicationContext,
                    notificationId = notifId,
                    channelId = channelId,
                    title = title,
                    body = body,
                    deepLink = deepLink
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
