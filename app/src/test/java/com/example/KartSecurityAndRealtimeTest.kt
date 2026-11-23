package com.example

import com.example.data.local.TokenCipher
import com.example.data.remote.SupabaseRealtimeClient
import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.domain.model.UserSession
import com.example.domain.model.UserSubscription
import com.example.notifications.KartNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KartSecurityAndRealtimeTest {

    @Test
    fun tokenCipher_passesThroughLegacyPlaintextTokens() {
        val legacyJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.legacy.sig"
        assertEquals(legacyJwt, TokenCipher.decrypt(legacyJwt))
        assertEquals("", TokenCipher.decrypt(""))
        assertEquals("", TokenCipher.encrypt(""))
    }

    @Test
    fun tokenCipher_handlesMalformedEncryptedPayloadGracefully() {
        val corrupted = "${TokenCipher.PREFIX}not-valid-base64!!!"
        assertEquals("", TokenCipher.decrypt(corrupted))
    }

    @Test
    fun realtimeClient_buildsValidPhoenixJoinPayload() {
        val join = SupabaseRealtimeClient.buildJoinMessage("tok-123", ref = 7)
        assertTrue(join.contains("\"topic\":\"realtime:public:messages\""))
        assertTrue(join.contains("\"event\":\"phx_join\""))
        assertTrue(join.contains("\"access_token\":\"tok-123\""))
        assertTrue(join.contains("\"ref\":\"7\""))
    }

    @Test
    fun realtimeClient_parsesConversationIdFromInsertEvent() {
        val sampleEvent = """
            {
              "topic": "realtime:public:messages",
              "event": "postgres_changes",
              "payload": {
                "data": {
                  "type": "INSERT",
                  "record": {
                    "id": "msg-1",
                    "conversation_id": "conv-abc-99",
                    "content": "Is this still available?"
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals("conv-abc-99", SupabaseRealtimeClient.parseConversationIdFromEvent(sampleEvent))
    }

    @Test
    fun realtimeClient_ignoresNonPostgresEventsAndInvalidJson() {
        val heartbeat = """{"topic":"phoenix","event":"phx_reply","payload":{"status":"ok"}}"""
        assertNull(SupabaseRealtimeClient.parseConversationIdFromEvent(heartbeat))
        assertNull(SupabaseRealtimeClient.parseConversationIdFromEvent("{bad json"))
    }

    @Test
    fun userSession_isLoggedInRequiresTokenAndUserId() {
        assertFalse(UserSession().isLoggedIn)
        assertFalse(UserSession(accessToken = "tok").isLoggedIn)
        assertFalse(UserSession(userId = "u1").isLoggedIn)
        assertTrue(UserSession(accessToken = "tok", userId = "u1").isLoggedIn)
    }

    @Test
    fun formatters_sanitizeDecimalInputEnforcesSingleDotAndTwoDecimals() {
        assertEquals("123.45", KartFormatters.sanitizeDecimalInput("123.4567"))
        assertEquals("12.34", KartFormatters.sanitizeDecimalInput("12..3.4abc"))
        assertEquals("0.5", KartFormatters.sanitizeDecimalInput(".5"))
        assertEquals("", KartFormatters.sanitizeDecimalInput("-abc"))
    }

    @Test
    fun formatters_isTrustedMediaUrlRestrictsSchemeAndHosts() {
        assertTrue(KartFormatters.isTrustedMediaUrl("https://demo.supabase.co/storage/v1/object/sign/chat-media/a.jpg"))
        assertTrue(KartFormatters.isTrustedMediaUrl("https://www.kart.cx/images/item.webp"))
        assertTrue(KartFormatters.isTrustedMediaUrl("https://res.cloudinary.com/demo/image/upload/item.png"))
        assertFalse(KartFormatters.isTrustedMediaUrl("http://demo.supabase.co/storage/v1/object/public/a.jpg"))
        assertFalse(KartFormatters.isTrustedMediaUrl("https://evil.example.com/tracker.jpg"))
        assertFalse(KartFormatters.isTrustedMediaUrl("javascript:alert(1)"))
    }

    @Test
    fun formatters_extractMessageMediaRejectsUntrustedUrlsAndClampsVoiceDuration() {
        val trustedVoice = "[VOICE:https://demo.supabase.co/storage/v1/object/sign/chat-media/v.m4a|9999]"
        val parsedVoice = KartFormatters.extractMessageVoice(trustedVoice)
        assertNotNull(parsedVoice)
        assertEquals(600, parsedVoice!!.durationSeconds)

        val spoofedVoice = "[VOICE:https://attacker.example/track.m4a|12]"
        assertNull(KartFormatters.extractMessageVoice(spoofedVoice))

        val trustedImage = "[IMAGE:https://demo.supabase.co/storage/v1/object/sign/chat-media/pic.jpg]"
        assertEquals(
            "https://demo.supabase.co/storage/v1/object/sign/chat-media/pic.jpg",
            KartFormatters.extractMessageImageUrl(trustedImage)
        )

        val spoofedImage = "[IMAGE:https://attacker.example/pixel.png]"
        assertNull(KartFormatters.extractMessageImageUrl(spoofedImage))
    }

    @Test
    fun formatters_sanitizeSocialUrlValidatesHandlesAndBlocksUntrustedDomains() {
        assertEquals("https://instagram.com/kart_gh", KartFormatters.sanitizeSocialUrl("@kart_gh", "instagram"))
        assertEquals("https://www.instagram.com/kart_gh", KartFormatters.sanitizeSocialUrl("https://www.instagram.com/kart_gh", "instagram"))
        assertNull(KartFormatters.sanitizeSocialUrl("https://evil.example.com/kart_gh", "instagram"))
        assertNull(KartFormatters.sanitizeSocialUrl("http://instagram.com/kart_gh", "instagram"))

        assertEquals("https://snapchat.com/add/kart-user", KartFormatters.sanitizeSocialUrl("@kart-user", "snapchat"))
        assertNull(KartFormatters.sanitizeSocialUrl("https://phish.example/add/kart", "snapchat"))

        assertEquals("https://wa.me/233241234567", KartFormatters.sanitizeSocialUrl("+233 24 123 4567", "whatsapp"))
        assertNull(KartFormatters.sanitizeSocialUrl("12345", "whatsapp"))
    }

    @Test
    fun formatters_isActiveSubscriptionValidatesStatusAndExpiry() {
        val activeFuture = UserSubscription(
            id = "sub-1",
            status = "Active",
            endDate = "2099-12-31T23:59:59Z"
        )
        val activeExpired = UserSubscription(
            id = "sub-2",
            status = "Active",
            endDate = "2020-01-01T00:00:00Z"
        )
        val cancelledFuture = UserSubscription(
            id = "sub-3",
            status = "Cancelled",
            endDate = "2099-12-31T23:59:59Z"
        )
        assertTrue(KartFormatters.isActiveSubscription(activeFuture))
        assertFalse(KartFormatters.isActiveSubscription(activeExpired))
        assertFalse(KartFormatters.isActiveSubscription(cancelledFuture))
        assertFalse(KartFormatters.isActiveSubscription(null))
    }

    @Test
    fun notificationHelper_sanitizeDeepLinkAllowsSafePathsAndBlocksSensitiveRoutes() {
        val validRel = KartNotificationHelper.sanitizeNotificationDeepLink("/orders/ord-123", KartNotificationHelper.CHANNEL_UPDATES)
        assertEquals("https://www.kart.cx/orders/ord-123", validRel.toString())

        val validAbs = KartNotificationHelper.sanitizeNotificationDeepLink("https://kart.cx/messages?id=c1", KartNotificationHelper.CHANNEL_MESSAGES)
        assertEquals("https://kart.cx/messages?id=c1", validAbs.toString())

        val externalHost = KartNotificationHelper.sanitizeNotificationDeepLink("https://evil.example.com/phish", KartNotificationHelper.CHANNEL_UPDATES)
        assertEquals("https://www.kart.cx/notifications", externalHost.toString())

        val authCallback = KartNotificationHelper.sanitizeNotificationDeepLink("https://www.kart.cx/auth-callback?refresh_token=abc", KartNotificationHelper.CHANNEL_MESSAGES)
        assertEquals("https://www.kart.cx/messages", authCallback.toString())

        val checkoutSuccess = KartNotificationHelper.sanitizeNotificationDeepLink("https://www.kart.cx/checkout-success?reference=ref1", KartNotificationHelper.CHANNEL_UPDATES)
        assertEquals("https://www.kart.cx/notifications", checkoutSuccess.toString())
    }

    @Test
    fun product_availabilityFlagsHandleStockAndStatus() {
        val available = Product(id = "p1", status = "Active", stockQuantity = 3)
        assertTrue(available.isActiveListing)
        assertFalse(available.isOutOfStock)

        val zeroStock = Product(id = "p2", status = "Active", stockQuantity = 0)
        assertTrue(zeroStock.isOutOfStock)
        assertTrue(zeroStock.isActiveListing)

        val soldStatus = Product(id = "p3", status = "Sold", stockQuantity = 2)
        assertFalse(soldStatus.isOutOfStock)
        assertFalse(soldStatus.isActiveListing)

        val draftStatus = Product(id = "p4", status = "Inactive", stockQuantity = 5)
        assertFalse(draftStatus.isOutOfStock)
        assertFalse(draftStatus.isActiveListing)
    }
}
