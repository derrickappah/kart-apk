package com.example

import com.example.domain.model.KartErrorFormatter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class KartErrorFormatterTest {

    @Test
    fun `maps DNS and host resolution errors to friendly offline message`() {
        val err = UnknownHostException(
            """Unable to resolve host "www.kart.cx": No address associated with hostname"""
        )
        assertEquals(
            KartErrorFormatter.OFFLINE_MESSAGE,
            KartErrorFormatter.fromThrowable(err, "Could not load campus listings.")
        )
        assertTrue(KartErrorFormatter.isConnectivityError(err))
    }

    @Test
    fun `maps connect, timeout, SSL, and IO exceptions to friendly messages`() {
        assertEquals(
            KartErrorFormatter.OFFLINE_MESSAGE,
            KartErrorFormatter.fromThrowable(ConnectException("Failed to connect to /10.0.0.1:443"))
        )
        assertEquals(
            KartErrorFormatter.TIMEOUT_MESSAGE,
            KartErrorFormatter.fromThrowable(SocketTimeoutException("timeout"))
        )
        assertEquals(
            KartErrorFormatter.SSL_MESSAGE,
            KartErrorFormatter.fromThrowable(SSLException("CertPathValidatorException"))
        )
        assertEquals(
            KartErrorFormatter.CONNECTION_LOST_MESSAGE,
            KartErrorFormatter.fromThrowable(IOException("unexpected end of stream"))
        )
    }

    @Test
    fun `maps wrapped paging or repository exceptions without leaking hostnames`() {
        val wrappedMsg = IllegalStateException(
            """Unable to resolve host "www.kart.cx": No address associated with hostname"""
        )
        assertEquals(
            KartErrorFormatter.OFFLINE_MESSAGE,
            KartErrorFormatter.fromThrowable(wrappedMsg, "Couldn't load marketplace")
        )
        assertTrue(KartErrorFormatter.isConnectivityError(wrappedMsg))

        val wrappedCause = IllegalStateException(
            "Remote load failed",
            UnknownHostException("www.kart.cx")
        )
        assertEquals(
            KartErrorFormatter.OFFLINE_MESSAGE,
            KartErrorFormatter.fromThrowable(wrappedCause)
        )
        assertTrue(KartErrorFormatter.isConnectivityError(wrappedCause))
    }

    @Test
    fun `maps HTTP exceptions and status codes cleanly`() {
        fun httpEx(code: Int): HttpException {
            val body = """{"error":"internal"}""".toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(code, body))
        }

        assertEquals(KartErrorFormatter.SESSION_EXPIRED_MESSAGE, KartErrorFormatter.fromThrowable(httpEx(401)))
        assertEquals(KartErrorFormatter.FORBIDDEN_MESSAGE, KartErrorFormatter.fromThrowable(httpEx(403)))
        assertEquals(KartErrorFormatter.RATE_LIMIT_MESSAGE, KartErrorFormatter.fromThrowable(httpEx(429)))
        assertEquals(KartErrorFormatter.SERVER_ERROR_MESSAGE, KartErrorFormatter.fromThrowable(httpEx(502)))
        assertEquals(
            KartErrorFormatter.SERVER_ERROR_MESSAGE,
            KartErrorFormatter.fromHttpStatus(500, "Server is temporarily unavailable (500).")
        )
    }

    @Test
    fun `sanitizes HTML, hostnames, PostgREST, JWT, and JSON leaks while stripping HTTP codes`() {
        val fallback = "Could not load items."
        assertEquals(fallback, KartErrorFormatter.sanitizeMessage("<!DOCTYPE html><html>502 Bad Gateway</html>", fallback))
        assertEquals(fallback, KartErrorFormatter.sanitizeMessage("Request to supabase.co failed", fallback))
        assertEquals(fallback, KartErrorFormatter.sanitizeMessage("PGRST116: JSON object requested", fallback))
        assertEquals(KartErrorFormatter.SESSION_EXPIRED_MESSAGE, KartErrorFormatter.sanitizeMessage("JWT expired", fallback))
        assertEquals(fallback, KartErrorFormatter.sanitizeMessage("""{"code":"42501","message":"rls"}""", fallback))
        assertEquals(
            "Unable to load campus feed.",
            KartErrorFormatter.sanitizeMessage("Unable to load campus feed. (502)", fallback)
        )
    }

    @Test
    fun `preserves clear user-facing business error messages`() {
        assertEquals(
            "Invalid email or password.",
            KartErrorFormatter.sanitizeMessage("Invalid email or password.")
        )
        assertEquals(
            "Insufficient wallet balance.",
            KartErrorFormatter.sanitizeMessage("Insufficient wallet balance.")
        )
        assertEquals(
            "Listing not found or no longer active.",
            KartErrorFormatter.sanitizeMessage("Listing not found or no longer active.")
        )
        assertFalse(
            KartErrorFormatter.isConnectivityError(IllegalArgumentException("Price cannot be negative."))
        )
    }
}
