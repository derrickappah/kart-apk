package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object KartNotificationHelper {
    const val CHANNEL_MESSAGES = "kart_messages"
    const val CHANNEL_UPDATES = "kart_updates"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New campus chat messages from buyers and sellers"
            enableVibration(true)
        }

        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "Orders & Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Order updates, reviews, follows, and account alerts"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(messagesChannel, updatesChannel))
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    internal fun sanitizeNotificationDeepLinkString(deepLink: String?, channelId: String): String {
        val fallback = if (channelId == CHANNEL_MESSAGES) {
            "https://www.kart.cx/messages"
        } else {
            "https://www.kart.cx/notifications"
        }
        val raw = deepLink?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        val candidate = if (raw.startsWith("http://", true) || raw.startsWith("https://", true) || raw.contains("://")) {
            raw
        } else {
            "https://www.kart.cx/${raw.trimStart('/')}"
        }
        val parsed = runCatching { java.net.URI(candidate) }.getOrNull() ?: return fallback

        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        val hasUserInfo = !parsed.userInfo.isNullOrBlank()
        val isTrustedHost = scheme == "https" && !hasUserInfo && host in setOf("www.kart.cx", "kart.cx")
        if (!isTrustedHost) return fallback

        // Never allow notifications to trigger auth callbacks or payment verification links
        val path = parsed.path.orEmpty().lowercase()
        val query = parsed.rawQuery.orEmpty().lowercase()
        if (path.contains("auth-callback") ||
            path.contains("auth-tokens") ||
            path.contains("checkout-success") ||
            query.contains("refresh_token=")
        ) {
            return fallback
        }
        return candidate
    }

    internal fun sanitizeNotificationDeepLink(deepLink: String?, channelId: String): Uri =
        Uri.parse(sanitizeNotificationDeepLinkString(deepLink, channelId))

    fun showNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        deepLink: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = sanitizeNotificationDeepLink(deepLink, channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.kart_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
