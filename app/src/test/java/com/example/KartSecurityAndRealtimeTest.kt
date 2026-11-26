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
    fun realtimeClient_buildsValidPresenceJoinAndTrackPayloads() {
        val join = SupabaseRealtimeClient.buildPresenceJoinMessage("tok-123", userId = "user-1", ref = 3)
        assertTrue(join.contains("\"topic\":\"realtime:online_users\""))
        assertTrue(join.contains("\"event\":\"phx_join\""))
        assertTrue(join.contains("\"key\":\"user-1\""))
        assertTrue(join.contains("\"enabled\":true"))
        assertTrue(join.contains("\"access_token\":\"tok-123\""))
        assertTrue(join.contains("\"ref\":\"3\""))
        assertTrue(join.contains("\"join_ref\":\"3\""))

        val track = SupabaseRealtimeClient.buildPresenceTrackMessage(userId = "user-1", ref = 4)
        assertTrue(track.contains("\"topic\":\"realtime:online_users\""))
        assertTrue(track.contains("\"event\":\"presence\""))
        assertTrue(track.contains("\"event\":\"track\""))
        assertTrue(track.contains("\"user_id\":\"user-1\""))

        val okReply = """{"topic":"realtime:online_users","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"3"}"""
        val trackReply = """{"topic":"realtime:online_users","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"4"}"""
        val errReply = """{"topic":"realtime:online_users","event":"phx_reply","payload":{"status":"error"},"ref":"3"}"""
        val otherReply = """{"topic":"realtime:chat:c1","event":"phx_reply","payload":{"status":"ok"},"ref":"3"}"""
        assertTrue(SupabaseRealtimeClient.isPresenceJoinOk(okReply, expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isPresenceJoinOk(trackReply, expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isPresenceJoinOk(okReply, expectedRef = null))
        assertFalse(SupabaseRealtimeClient.isPresenceJoinOk(errReply, expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isPresenceJoinOk(otherReply, expectedRef = "3"))
    }

    @Test
    fun realtimeClient_parsesPresenceStateAndAppliesPresenceDiff() {
        val stateJson = """
            {
              "topic": "realtime:online_users",
              "event": "presence_state",
              "payload": {
                "user-1": { "metas": [{ "phx_ref": "a" }] },
                "user-2": { "metas": [{ "phx_ref": "b" }] }
              }
            }
        """.trimIndent()
        val initial = SupabaseRealtimeClient.parsePresenceState(stateJson)
        assertEquals(setOf("user-1", "user-2"), initial)

        val diffJson = """
            {
              "topic": "realtime:online_users",
              "event": "presence_diff",
              "payload": {
                "joins": { "user-3": { "metas": [{ "phx_ref": "c" }] } },
                "leaves": { "user-1": { "metas": [{ "phx_ref": "a" }] } }
              }
            }
        """.trimIndent()
        val updated = SupabaseRealtimeClient.applyPresenceDiff(initial!!, diffJson)
        assertEquals(setOf("user-2", "user-3"), updated)
    }

    @Test
    fun realtimeClient_buildsAndParsesTypingBroadcastEvents() {
        val join = SupabaseRealtimeClient.buildChatChannelJoinMessage("tok-123", "conv-42", ref = 5)
        assertTrue(join.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertTrue(join.contains("\"event\":\"phx_join\""))
        assertTrue(join.contains("\"self\":false"))
        assertTrue(join.contains("\"join_ref\":\"5\""))

        val broadcast = SupabaseRealtimeClient.buildTypingBroadcastMessage(
            conversationId = "conv-42",
            userId = "user-9",
            isTyping = true,
            ref = 6
        )
        assertTrue(broadcast.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertTrue(broadcast.contains("\"event\":\"broadcast\""))
        assertTrue(broadcast.contains("\"event\":\"typing\""))
        assertTrue(broadcast.contains("\"user_id\":\"user-9\""))
        assertTrue(broadcast.contains("\"is_typing\":true"))

        val parsed = SupabaseRealtimeClient.parseTypingEvent(broadcast)
        assertNotNull(parsed)
        assertEquals("conv-42", parsed!!.conversationId)
        assertEquals("user-9", parsed.userId)
        assertTrue(parsed.isTyping)

        val stopBroadcast = SupabaseRealtimeClient.buildTypingBroadcastMessage(
            conversationId = "conv-42",
            userId = "user-9",
            isTyping = false,
            ref = 7
        )
        val parsedStop = SupabaseRealtimeClient.parseTypingEvent(stopBroadcast)
        assertNotNull(parsedStop)
        assertFalse(parsedStop!!.isTyping)

        val globalTyping = SupabaseRealtimeClient.buildGlobalTypingBroadcastMessage(
            conversationId = "conv-77",
            userId = "user-5",
            isTyping = true,
            ref = 8
        )
        assertTrue(globalTyping.contains("\"topic\":\"realtime:online_users\""))
        assertTrue(globalTyping.contains("\"conversation_id\":\"conv-77\""))
        val parsedGlobal = SupabaseRealtimeClient.parseTypingEvent(globalTyping)
        assertNotNull(parsedGlobal)
        assertEquals("conv-77", parsedGlobal!!.conversationId)
        assertEquals("user-5", parsedGlobal.userId)
        assertTrue(parsedGlobal.isTyping)
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

    @Test
    fun deepLinks_normalizeCustomSchemeHostAndPath() {
        assertEquals("/messages/conv-42", com.example.ui.navigation.normalizeDeepLinkPath(android.net.Uri.parse("kart-app://messages/conv-42")))
        assertEquals("/wallet", com.example.ui.navigation.normalizeDeepLinkPath(android.net.Uri.parse("kart-app://wallet?reference=ref1")))
        assertEquals("/marketplace/prod-9", com.example.ui.navigation.normalizeDeepLinkPath(android.net.Uri.parse("https://www.kart.cx/marketplace/prod-9")))
    }

    @Test
    fun notificationHelper_convertsSafeCustomSchemeLinksAndBlocksSensitiveOnes() {
        val safeCustom = KartNotificationHelper.sanitizeNotificationDeepLink("kart-app://messages/conv-9", KartNotificationHelper.CHANNEL_MESSAGES)
        assertEquals("https://www.kart.cx/messages/conv-9", safeCustom.toString())

        val blockedCustom = KartNotificationHelper.sanitizeNotificationDeepLink("kart-app://auth-callback?refresh_token=secret", KartNotificationHelper.CHANNEL_UPDATES)
        assertEquals("https://www.kart.cx/notifications", blockedCustom.toString())
    }

    @Test
    fun realtimeClient_backoffDelayCapsAtThirtySeconds() {
        assertEquals(2_000L, SupabaseRealtimeClient.computeBackoffDelayMs(0))
        assertEquals(4_000L, SupabaseRealtimeClient.computeBackoffDelayMs(1))
        assertEquals(8_000L, SupabaseRealtimeClient.computeBackoffDelayMs(2))
        assertEquals(16_000L, SupabaseRealtimeClient.computeBackoffDelayMs(3))
        assertEquals(30_000L, SupabaseRealtimeClient.computeBackoffDelayMs(4))
        assertEquals(30_000L, SupabaseRealtimeClient.computeBackoffDelayMs(10))
    }

    @Test
    fun authEmailValidation_rejectsMalformedAddresses() {
        assertTrue(com.example.ui.screens.isValidAuthEmail("student@knust.edu.gh"))
        assertTrue(com.example.ui.screens.isValidAuthEmail("  kwame.mensah+1@kart.cx "))
        assertFalse(com.example.ui.screens.isValidAuthEmail("notanemail"))
        assertFalse(com.example.ui.screens.isValidAuthEmail("a@b"))
        assertFalse(com.example.ui.screens.isValidAuthEmail("@kart.cx"))
        assertFalse(com.example.ui.screens.isValidAuthEmail("student@"))
    }

    @Test
    fun pkce_computesRfc7636S256ChallengeAndValidVerifier() {
        // RFC 7636 Appendix B test vector
        val rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, com.example.ui.screens.KartPkce.computeCodeChallengeS256(rfcVerifier))

        val generated = com.example.ui.screens.KartPkce.generateCodeVerifier()
        assertTrue(generated.length in 43..128)
        assertFalse(generated.contains("="))
        assertFalse(generated.contains("+"))
        assertFalse(generated.contains("/"))
    }

    @Test
    fun deliveryQr_validatesTokenFormatAndGeneratesBitmap() {
        val validToken = "kartqr1.eyJvcmRlcklkIjoiMTIzIn0.abcdef123456"
        assertTrue(com.example.ui.components.isValidDeliveryQrToken(validToken))
        assertFalse(com.example.ui.components.isValidDeliveryQrToken("https://evil.example/qr"))
        assertFalse(com.example.ui.components.isValidDeliveryQrToken("kartqr1.onlytwo"))
        val bmp = com.example.ui.components.generateQrBitmap(validToken, sizePx = 128)
        assertNotNull(bmp)
        assertEquals(128, bmp!!.width)
        assertEquals(128, bmp.height)
    }

    @Test
    fun httpErrorParser_suppressesRawHtmlAndExtractsJsonErrors() {
        val html404 = "<!DOCTYPE html><html lang=\"en\"><head><title>404</title></head></html>"
        val parsed404 = com.example.data.repository.KartRepository.parseHttpErrorBody(html404, 404)
        assertNotNull(parsed404)
        assertFalse(parsed404!!.contains("<html", ignoreCase = true))
        assertTrue(parsed404.contains("404"))

        val jsonErr = """{"error":"Only pending unpaid orders can be cancelled."}"""
        assertEquals(
            "Only pending unpaid orders can be cancelled.",
            com.example.data.repository.KartRepository.parseHttpErrorBody(jsonErr, 400)
        )
    }

    @Test
    fun paymentCallback_extractsOrderSubscriptionPromotionAndWalletParams() {
        val orderCb = com.example.ui.navigation.parsePaymentCallback(
            android.net.Uri.parse("kart-app://checkout-success?orderId=ord-uuid-123&trxref=order_ord-uuid-123_1732490000&reference=order_ord-uuid-123_1732490000")
        )
        assertEquals("ord-uuid-123", orderCb.orderId)
        assertEquals("order_ord-uuid-123_1732490000", orderCb.reference)
        assertFalse(orderCb.isSubscription)
        assertFalse(orderCb.isPromotion)
        assertFalse(orderCb.isWallet)

        val orderFromRefOnly = com.example.ui.navigation.parsePaymentCallback(
            android.net.Uri.parse("kart-app://checkout-success?trxref=order_a1b2-c3d4_1732490001")
        )
        assertEquals("a1b2-c3d4", orderFromRefOnly.orderId)
        assertEquals("order_a1b2-c3d4_1732490001", orderFromRefOnly.reference)

        val promoCb = com.example.ui.navigation.parsePaymentCallback(
            android.net.Uri.parse("kart-app://checkout-success?adId=ad-99&productId=prod-1&reference=ad_ad-99_1732490002")
        )
        assertEquals("ad-99", promoCb.adId)
        assertTrue(promoCb.isPromotion)
        assertEquals("ad_ad-99_1732490002", promoCb.reference)

        val subCb = com.example.ui.navigation.parsePaymentCallback(
            android.net.Uri.parse("kart-app://checkout-success?subscriptionId=sub-77&reference=sub_sub-77_1732490003_retry")
        )
        assertEquals("sub-77", subCb.subscriptionId)
        assertTrue(subCb.isSubscription)
        assertEquals("sub_sub-77_1732490003_retry", subCb.reference)

        val walletCb = com.example.ui.navigation.parsePaymentCallback(
            android.net.Uri.parse("https://www.kart.cx/dashboard/wallet?ref=wdp_user1_1732490004")
        )
        assertEquals("wdp_user1_1732490004", walletCb.reference)
        assertTrue(walletCb.isWallet)
    }
}
