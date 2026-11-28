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
    fun realtimeClient_parsesPostgresInsertFallbackAndIgnoresHeartbeat() {
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
        val heartbeat = """{"topic":"phoenix","event":"phx_reply","payload":{"status":"ok"}}"""
        assertNull(SupabaseRealtimeClient.parseConversationIdFromEvent(heartbeat))
        assertNull(SupabaseRealtimeClient.parseConversationIdFromEvent("{bad json"))
    }

    @Test
    fun realtimeClient_buildsScopedChatPresenceJoinAndTrackWithoutGlobalRoom() {
        val join = SupabaseRealtimeClient.buildChatChannelJoinMessage(
            accessToken = "tok-123",
            conversationId = "conv-42",
            userId = "user-1",
            ref = 3
        )
        assertTrue(join.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertFalse(join.contains("realtime:online_users"))
        assertTrue(join.contains("\"event\":\"phx_join\""))
        assertTrue(join.contains("\"key\":\"user-1\""))
        assertTrue(join.contains("\"enabled\":true"))
        assertTrue(join.contains("\"self\":false"))
        assertTrue(join.contains("\"access_token\":\"tok-123\""))
        assertTrue(join.contains("\"ref\":\"3\""))
        assertTrue(join.contains("\"join_ref\":\"3\""))

        val track = SupabaseRealtimeClient.buildChatPresenceTrackMessage(
            conversationId = "conv-42",
            userId = "user-1",
            ref = 4
        )
        assertTrue(track.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertFalse(track.contains("realtime:online_users"))
        assertTrue(track.contains("\"event\":\"presence\""))
        assertTrue(track.contains("\"event\":\"track\""))
        assertTrue(track.contains("\"user_id\":\"user-1\""))

        val okReply = """{"topic":"realtime:chat:conv-42","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"3"}"""
        val trackReply = """{"topic":"realtime:chat:conv-42","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"4"}"""
        val errReply = """{"topic":"realtime:chat:conv-42","event":"phx_reply","payload":{"status":"error"},"ref":"3"}"""
        val otherReply = """{"topic":"realtime:chat:other","event":"phx_reply","payload":{"status":"ok"},"ref":"3"}"""
        assertTrue(SupabaseRealtimeClient.isChannelJoinOk(okReply, "realtime:chat:conv-42", expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isChannelJoinOk(trackReply, "realtime:chat:conv-42", expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isChannelJoinOk(okReply, "realtime:chat:conv-42", expectedRef = null))
        assertFalse(SupabaseRealtimeClient.isChannelJoinOk(errReply, "realtime:chat:conv-42", expectedRef = "3"))
        assertFalse(SupabaseRealtimeClient.isChannelJoinOk(otherReply, "realtime:chat:conv-42", expectedRef = "3"))
    }

    @Test
    fun realtimeClient_parsesPresenceStateAndAppliesPresenceDiff() {
        val stateJson = """
            {
              "topic": "realtime:chat:conv-42",
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
              "topic": "realtime:chat:conv-42",
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
    fun realtimeClient_buildsAndParsesScopedTypingAndNewMessageBroadcasts() {
        val typingStart = SupabaseRealtimeClient.buildTypingBroadcastMessage(
            conversationId = "conv-42",
            userId = "user-9",
            isTyping = true,
            ref = 6
        )
        assertTrue(typingStart.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertFalse(typingStart.contains("realtime:online_users"))
        assertTrue(typingStart.contains("\"event\":\"broadcast\""))
        assertTrue(typingStart.contains("\"event\":\"typing\""))
        assertTrue(typingStart.contains("\"user_id\":\"user-9\""))
        assertTrue(typingStart.contains("\"is_typing\":true"))

        val parsed = SupabaseRealtimeClient.parseTypingEvent(typingStart)
        assertNotNull(parsed)
        assertEquals("conv-42", parsed!!.conversationId)
        assertEquals("user-9", parsed.userId)
        assertTrue(parsed.isTyping)

        val typingStop = SupabaseRealtimeClient.buildTypingBroadcastMessage(
            conversationId = "conv-42",
            userId = "user-9",
            isTyping = false,
            ref = 7
        )
        val parsedStop = SupabaseRealtimeClient.parseTypingEvent(typingStop)
        assertNotNull(parsedStop)
        assertFalse(parsedStop!!.isTyping)

        val globalTypingRejected = """
            {
              "topic": "realtime:online_users",
              "event": "broadcast",
              "payload": {
                "type": "broadcast",
                "event": "typing",
                "payload": { "conversation_id": "conv-77", "user_id": "user-5", "is_typing": true }
              }
            }
        """.trimIndent()
        assertNull(SupabaseRealtimeClient.parseTypingEvent(globalTypingRejected))

        val newMsgBroadcast = SupabaseRealtimeClient.buildNewMessageBroadcastMessage(
            conversationId = "conv-42",
            messageId = "msg-99",
            senderId = "user-9",
            ref = 8
        )
        assertTrue(newMsgBroadcast.contains("\"topic\":\"realtime:chat:conv-42\""))
        assertTrue(newMsgBroadcast.contains("\"event\":\"new_message\""))
        assertTrue(newMsgBroadcast.contains("\"message_id\":\"msg-99\""))
        assertEquals("conv-42", SupabaseRealtimeClient.parseNewMessageBroadcast(newMsgBroadcast))
        assertNull(SupabaseRealtimeClient.parseNewMessageBroadcast(typingStart))
    }

    @Test
    fun adminRealtimeClient_watchesOnlyLowVolumeModerationQueues() {
        val watched = com.example.data.remote.SupabaseAdminRealtimeClient.WATCHED_TABLES
        assertEquals(
            listOf("verification_requests", "withdrawal_requests", "refund_requests", "reports"),
            watched
        )
        assertFalse(watched.contains("profiles"))
        assertFalse(watched.contains("products"))
        assertFalse(watched.contains("orders"))
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

    @Test
    fun chatCacheStore_persistsConversationsAndMessagesFiltersLocalAndHandlesCorruptFiles() {
        val tempDir = java.nio.file.Files.createTempDirectory("chat_cache_test").toFile()
        try {
            val store = com.example.data.local.ChatCacheStore(tempDir)
            assertTrue(store.loadConversations("u1").isEmpty())
            assertTrue(store.loadMessages("u1", "c1").isEmpty())

            val msg1 = com.example.domain.model.MessageRow(
                id = "msg-1",
                conversationId = "c1",
                senderId = "u1",
                content = "Hello",
                createdAt = "2026-11-26T10:00:00Z"
            )
            val pendingLocal = com.example.domain.model.MessageRow(
                id = "local_temp_1",
                conversationId = "c1",
                senderId = "u1",
                content = "Pending",
                createdAt = "2026-11-26T10:01:00Z"
            )
            store.saveMessages("u1", "c1", listOf(msg1, pendingLocal))
            val loadedMsgs = store.loadMessages("u1", "c1")
            assertEquals(1, loadedMsgs.size)
            assertEquals("msg-1", loadedMsgs.first().id)

            val conv = com.example.domain.model.EnrichedConversation(
                id = "c1",
                otherUser = com.example.domain.model.Profile(id = "u2", displayName = "Ama"),
                lastMessage = msg1,
                unreadCount = 2,
                updatedAt = "2026-11-26T10:00:00Z"
            )
            store.saveConversations("u1", listOf(conv))
            val loadedConvs = store.loadConversations("u1")
            assertEquals(1, loadedConvs.size)
            assertEquals("c1", loadedConvs.first().id)
            assertEquals("Ama", loadedConvs.first().otherUser.displayName)

            java.io.File(tempDir, "messages_u1_c1.json").writeText("{corrupt json")
            assertTrue(store.loadMessages("u1", "c1").isEmpty())

            store.clearAll()
            assertTrue(store.loadConversations("u1").isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun formatters_trustsCloudflareCdnAndR2HostsAndOptimizesCdnUrls() {
        assertTrue(KartFormatters.isTrustedMediaUrl("https://cdn.kart.cx/products/u1/img.webp"))
        assertTrue(KartFormatters.isTrustedMediaUrl("https://bucket.pub-abc123.r2.dev/item.jpg"))
        assertTrue(KartFormatters.isTrustedMediaUrl("https://acct.r2.cloudflarestorage.com/kart-media/id.png?X-Amz-Signature=abc"))
        assertFalse(KartFormatters.isTrustedMediaUrl("http://cdn.kart.cx/products/u1/img.webp"))

        val optimizedCard = KartFormatters.buildOptimizedImageUrl(
            "https://cdn.kart.cx/products/u1/item.jpg?v=2&width=999",
            KartFormatters.ImageVariant.CARD
        )
        assertTrue(optimizedCard.startsWith("https://cdn.kart.cx/products/u1/item.jpg?"))
        assertTrue(optimizedCard.contains("v=2"))
        assertTrue(optimizedCard.contains("width=480"))
        assertTrue(optimizedCard.contains("format=webp"))
        assertTrue(optimizedCard.contains("quality=75"))
        assertFalse(optimizedCard.contains("width=999"))

        val optimizedDetail = KartFormatters.buildOptimizedImageUrl(
            "https://cdn.kart.cx/products/u1/item.jpg",
            KartFormatters.ImageVariant.DETAIL
        )
        assertTrue(optimizedDetail.contains("width=1200"))
        assertTrue(optimizedDetail.contains("quality=82"))

        val optimizedAvatar = KartFormatters.buildOptimizedImageUrl(
            "https://cdn.kart.cx/profiles/u1/avatar.png",
            KartFormatters.ImageVariant.AVATAR
        )
        assertTrue(optimizedAvatar.contains("width=256"))
        assertTrue(optimizedAvatar.contains("quality=78"))

        val nonCdn = "https://demo.supabase.co/storage/v1/object/public/products/a.jpg"
        assertEquals(nonCdn, KartFormatters.buildOptimizedImageUrl(nonCdn, KartFormatters.ImageVariant.CARD))
        assertEquals("", KartFormatters.buildOptimizedImageUrl("   "))
    }

    @Test
    fun appCheckInterceptor_attachesTokenAndIdempotencyKeyOnlyOnProtectedRoutes() {
        val capturedRequests = mutableListOf<okhttp3.Request>()
        fun clientWith(interceptor: com.example.data.remote.AppCheckInterceptor): okhttp3.OkHttpClient {
            return okhttp3.OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor { chain ->
                    val req = chain.request()
                    capturedRequests.add(req)
                    okhttp3.Response.Builder()
                        .request(req)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create(null, "{}"))
                        .build()
                }
                .build()
        }

        val okClient = clientWith(com.example.data.remote.AppCheckInterceptor { "appcheck-tok-123" })
        okClient.newCall(
            okhttp3.Request.Builder()
                .url("https://www.kart.cx/api/orders/create")
                .post(okhttp3.RequestBody.create(null, "{}"))
                .build()
        ).execute().close()
        val orderPost = capturedRequests.last()
        assertEquals("appcheck-tok-123", orderPost.header("X-Firebase-AppCheck"))
        assertFalse(orderPost.header("Idempotency-Key").isNullOrBlank())

        okClient.newCall(
            okhttp3.Request.Builder()
                .url("https://www.kart.cx/api/wallet/deposit")
                .header("Idempotency-Key", "custom-key-42")
                .post(okhttp3.RequestBody.create(null, "{}"))
                .build()
        ).execute().close()
        val depositPost = capturedRequests.last()
        assertEquals("appcheck-tok-123", depositPost.header("X-Firebase-AppCheck"))
        assertEquals("custom-key-42", depositPost.header("Idempotency-Key"))

        okClient.newCall(
            okhttp3.Request.Builder()
                .url("https://www.kart.cx/api/auth/session")
                .get()
                .build()
        ).execute().close()
        val authGet = capturedRequests.last()
        assertEquals("appcheck-tok-123", authGet.header("X-Firebase-AppCheck"))
        assertNull(authGet.header("Idempotency-Key"))

        okClient.newCall(
            okhttp3.Request.Builder()
                .url("https://www.kart.cx/api/marketplace/products")
                .get()
                .build()
        ).execute().close()
        val publicGet = capturedRequests.last()
        assertNull(publicGet.header("X-Firebase-AppCheck"))
        assertNull(publicGet.header("Idempotency-Key"))

        val failingTokenClient = clientWith(com.example.data.remote.AppCheckInterceptor {
            throw IllegalStateException("Firebase not initialized")
        })
        failingTokenClient.newCall(
            okhttp3.Request.Builder()
                .url("https://www.kart.cx/api/orders/create")
                .post(okhttp3.RequestBody.create(null, "{}"))
                .build()
        ).execute().close()
        val fallbackReq = capturedRequests.last()
        assertNull(fallbackReq.header("X-Firebase-AppCheck"))
        assertFalse(fallbackReq.header("Idempotency-Key").isNullOrBlank())
    }

    @Test
    fun directR2Uploader_validatesSizeAndPerformsDirectPutWithPresignedHeaders() = kotlinx.coroutines.test.runTest {
        val capturedDirectPut = mutableListOf<okhttp3.Request>()
        val directClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedDirectPut.add(req)
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            }
            .build()

        val fakeCursorApi = createFakeCursorApi(
            presignHandler = { req ->
                assertEquals("products", req.bucket)
                assertEquals("image/webp", req.contentType)
                com.example.data.remote.PresignResponse(
                    provider = "r2",
                    uploadUrl = "https://acct.r2.cloudflarestorage.com/kart-media/products/u1/test.webp?X-Amz-Signature=sig",
                    method = "PUT",
                    objectKey = "products/u1/test.webp",
                    publicUrl = "https://cdn.kart.cx/products/u1/test.webp",
                    isPrivate = false,
                    headers = mapOf("x-amz-acl" to "private")
                )
            }
        )
        val uploader = com.example.data.upload.DirectR2Uploader(fakeCursorApi, directClient)

        val emptyFailure = runCatching { uploader.uploadBytes(ByteArray(0), "products", "image/webp") }.exceptionOrNull()
        assertTrue(emptyFailure is IllegalArgumentException)

        val tooLargeFailure = runCatching { uploader.uploadBytes(ByteArray(5 * 1024 * 1024 + 1), "products", "image/webp") }.exceptionOrNull()
        assertTrue(tooLargeFailure is IllegalArgumentException)

        val payload = ByteArray(1024) { 0x2A }
        val result = uploader.uploadBytes(payload, bucket = "products", mimeType = "image/webp")
        assertEquals("products/u1/test.webp", result.objectKey)
        assertEquals("https://cdn.kart.cx/products/u1/test.webp", result.publicUrl)
        assertFalse(result.isPrivate)
        assertEquals(1, capturedDirectPut.size)
        val putReq = capturedDirectPut.first()
        assertEquals("PUT", putReq.method)
        assertEquals("https://acct.r2.cloudflarestorage.com/kart-media/products/u1/test.webp?X-Amz-Signature=sig", putReq.url.toString())
        assertEquals("private", putReq.header("x-amz-acl"))
        assertNull(putReq.header("Authorization"))
        assertNull(putReq.header("Cookie"))
    }

    @OptIn(androidx.paging.ExperimentalPagingApi::class)
    @Test
    fun roomAndRemoteMediator_cachesPagesAppliesCdnResizeIsolatesFeedsAndHonorsTtl() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.KartDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            var nowMs = 1_000_000L
            var requestedCursors = mutableListOf<String?>()
            val fakeApi = createFakeCursorApi(
                marketplaceHandler = { cursor, _ ->
                    requestedCursors.add(cursor)
                    if (cursor == null) {
                        com.example.data.remote.MarketplacePageResponse(
                            products = listOf(
                                com.example.data.remote.ProductDto(
                                    id = "prod-1",
                                    title = "MacBook Air M2",
                                    price = 6500.0,
                                    imageUrl = "https://cdn.kart.cx/products/u1/mac.jpg",
                                    campus = "UG",
                                    category = "Electronics",
                                    condition = "Like New",
                                    sellerId = "seller-1"
                                )
                            ),
                            hasMore = true,
                            nextCursor = "cursor-page-2"
                        )
                    } else {
                        com.example.data.remote.MarketplacePageResponse(
                            products = listOf(
                                com.example.data.remote.ProductDto(
                                    id = "prod-2",
                                    title = "Desk Lamp",
                                    price = 120.0,
                                    imageUrl = "https://cdn.kart.cx/products/u1/lamp.jpg",
                                    campus = "UG",
                                    category = "Dorm Furniture",
                                    condition = "Good",
                                    sellerId = "seller-2"
                                )
                            ),
                            hasMore = false,
                            nextCursor = null
                        )
                    }
                }
            )

            val defaultFilter = com.example.data.paging.MarketplaceFilterSpec(sort = "newest")
            val electronicsFilter = com.example.data.paging.MarketplaceFilterSpec(category = "Electronics", sort = "newest")
            val mediator = com.example.data.paging.MarketplaceRemoteMediator(
                filter = defaultFilter,
                database = db,
                api = fakeApi,
                cacheTtlMs = 90_000L,
                nowMs = { nowMs }
            )

            assertEquals(
                androidx.paging.RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )

            val emptyState = androidx.paging.PagingState<Int, com.example.data.local.ProductEntity>(
                pages = emptyList(),
                anchorPosition = null,
                config = androidx.paging.PagingConfig(pageSize = 20),
                leadingPlaceholderCount = 0
            )
            val refreshRes = mediator.load(androidx.paging.LoadType.REFRESH, emptyState)
            assertTrue(refreshRes is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertFalse((refreshRes as androidx.paging.RemoteMediator.MediatorResult.Success).endOfPaginationReached)

            val keyAfterRefresh = db.remoteKeyDao().getByFeedKey(defaultFilter.feedKey())
            assertNotNull(keyAfterRefresh)
            assertEquals("cursor-page-2", keyAfterRefresh!!.nextCursor)
            assertTrue(keyAfterRefresh.hasMore)

            nowMs += 30_000L
            assertEquals(
                androidx.paging.RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize()
            )

            nowMs += 70_000L
            assertEquals(
                androidx.paging.RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator.initialize()
            )

            val appendRes = mediator.load(androidx.paging.LoadType.APPEND, emptyState)
            assertTrue(appendRes is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue((appendRes as androidx.paging.RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(listOf<String?>(null, "cursor-page-2"), requestedCursors)
            assertEquals(2, db.productDao().countByFeedKey(defaultFilter.feedKey()))

            // Same product ID in a separate feed filter must not evict default feed entries
            val electronicsMediator = com.example.data.paging.MarketplaceRemoteMediator(
                filter = electronicsFilter,
                database = db,
                api = fakeApi,
                cacheTtlMs = 90_000L,
                nowMs = { nowMs }
            )
            electronicsMediator.load(androidx.paging.LoadType.REFRESH, emptyState)
            assertEquals(2, db.productDao().countByFeedKey(defaultFilter.feedKey()))
            assertEquals(1, db.productDao().countByFeedKey(electronicsFilter.feedKey()))

            val loadedPage = db.productDao().pagingSource(defaultFilter.feedKey()).load(
                androidx.paging.PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
            ) as androidx.paging.PagingSource.LoadResult.Page
            assertEquals(2, loadedPage.data.size)
            val cachedUrl = loadedPage.data.first().imageUrl.orEmpty()
            assertTrue(cachedUrl.contains("width=480"))
            assertTrue(cachedUrl.contains("format=webp"))
            assertTrue(cachedUrl.contains("quality=75"))
        } finally {
            db.close()
        }
    }

    @Test
    fun imageCompression_downscalesLargeImageAndProducesValidJpeg() {
        val largeBitmap = android.graphics.Bitmap.createBitmap(2400, 1800, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(2400 * 1800) { i ->
            val r = (i * 37) and 0xFF
            val g = (i * 73) and 0xFF
            val b = (i * 19) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        largeBitmap.setPixels(pixels, 0, 2400, 0, 0, 2400, 1800)
        val rawStream = java.io.ByteArrayOutputStream()
        assertTrue(largeBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, rawStream))
        largeBitmap.recycle()
        val rawBytes = rawStream.toByteArray()

        val compressed = com.example.data.repository.KartRepository.compressImageBytes(
            rawBytes = rawBytes,
            maxDimensionPx = 1440,
            jpegQuality = 82
        )
        assertNotNull(compressed)
        val outBytes = compressed!!
        assertTrue(outBytes.size >= 12)
        assertEquals(0xFF.toByte(), outBytes[0])
        assertEquals(0xD8.toByte(), outBytes[1])
        assertEquals(0xFF.toByte(), outBytes[2])
        assertTrue(outBytes.size < rawBytes.size)

        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(outBytes, 0, outBytes.size, bounds)
        assertTrue(bounds.outWidth in 1..1440)
        assertTrue(bounds.outHeight in 1..1440)
    }

    private fun createFakeCursorApi(
        marketplaceHandler: suspend (cursor: String?, limit: Int) -> com.example.data.remote.MarketplacePageResponse = { _, _ ->
            com.example.data.remote.MarketplacePageResponse()
        },
        presignHandler: suspend (com.example.data.remote.PresignRequest) -> com.example.data.remote.PresignResponse = { req ->
            com.example.data.remote.PresignResponse(
                provider = "r2",
                uploadUrl = "https://acct.r2.cloudflarestorage.com/kart-media/${req.bucket}/file",
                objectKey = "${req.bucket}/file",
                publicUrl = "https://cdn.kart.cx/${req.bucket}/file"
            )
        }
    ): com.example.data.remote.KartCursorApi {
        return object : com.example.data.remote.KartCursorApi {
            override suspend fun getMarketplaceProducts(
                cursor: String?,
                limit: Int,
                search: String?,
                campus: String?,
                category: String?,
                condition: String?,
                minPrice: Double?,
                maxPrice: Double?,
                sort: String
            ): com.example.data.remote.MarketplacePageResponse = marketplaceHandler(cursor, limit)

            override suspend fun getOrders(cursor: String?, limit: Int, role: String, status: String?) =
                com.example.data.remote.OrdersCursorResponse()

            override suspend fun getWalletTransactions(cursor: String?, limit: Int, type: String?) =
                com.example.data.remote.WalletTransactionsCursorResponse()

            override suspend fun getWishlist(include: String, cursor: String?, limit: Int) =
                com.example.data.remote.WishlistCursorResponse()

            override suspend fun getNotifications(cursor: String?, limit: Int, unreadOnly: Boolean) =
                com.example.data.remote.NotificationsCursorResponse()

            override suspend fun getReviews(productId: String?, sellerId: String?, cursor: String?, limit: Int) =
                com.example.data.remote.ReviewsCursorResponse()

            override suspend fun getConversations(cursor: String?, limit: Int) =
                com.example.data.remote.ConversationsCursorResponse()

            override suspend fun getConversationMessages(conversationId: String, cursor: String?, limit: Int) =
                com.example.data.remote.MessagesCursorResponse()

            override suspend fun sendConversationMessage(conversationId: String, payload: Map<String, String>) =
                com.example.data.remote.SendMessageResponse()

            override suspend fun getPresenceBatch(userIdsCsv: String) =
                com.example.data.remote.PresenceBatchResponse()

            override suspend fun touchPresence() =
                com.example.data.remote.PresenceTouchResponse()

            override suspend fun getAdminConsolePage(
                resource: String,
                cursor: String?,
                limit: Int,
                q: String?,
                status: String?,
                includeKpis: Boolean
            ) = com.example.data.remote.AdminConsolePageResponse()

            override suspend fun requestPresignedUpload(body: com.example.data.remote.PresignRequest) =
                presignHandler(body)

            override suspend fun createOrder(idempotencyKey: String, body: Map<String, Any>) =
                emptyMap<String, Any?>()
        }
    }
}
