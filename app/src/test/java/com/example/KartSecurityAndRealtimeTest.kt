package com.example

import com.example.data.local.TokenCipher
import com.example.data.remote.SupabaseRealtimeClient
import com.example.domain.model.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
