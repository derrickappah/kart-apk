package com.example.notifications

import com.example.KartApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
            if (session.isLoggedIn) {
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
        val channelId = if (type == "message" || type == "chat") {
            KartNotificationHelper.CHANNEL_MESSAGES
        } else {
            KartNotificationHelper.CHANNEL_UPDATES
        }

        val deepLink = data["deep_link"] ?: data["link"]
        val notifId = (data["id"] ?: "${title}_${System.currentTimeMillis()}").hashCode()

        serviceScope.launch {
            val session = app.container.sessionStore.currentSession()
            val pushAllowed = app.container.sessionStore.isPushEnabled()
            if (!session.isLoggedIn || !pushAllowed) return@launch

            val targetUserId = data["user_id"]?.trim()
            if (!targetUserId.isNullOrBlank() && targetUserId != session.userId) return@launch

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
