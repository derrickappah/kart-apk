package com.example.domain.model

import android.util.Log
import com.example.BuildConfig
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Centralizes error-to-user-message mapping so raw exceptions, hostnames, SQL/PostgREST
 * codes, HTML error pages, and HTTP status codes never leak to the UI.
 * In debug builds, technical details are logged to Logcat under [LOG_TAG].
 */
object KartErrorFormatter {

    private const val LOG_TAG = "KartError"

    const val DEFAULT_FALLBACK = "Something went wrong. Please try again."
    const val OFFLINE_MESSAGE = "No internet connection. Check your connection and try again."
    const val TIMEOUT_MESSAGE = "The connection timed out. Please try again."
    const val SSL_MESSAGE = "Could not establish a secure connection. Please try again."
    const val CONNECTION_LOST_MESSAGE = "Connection lost. Please check your internet and try again."
    const val SERVER_ERROR_MESSAGE = "Something went wrong on our end. Please try again in a moment."
    const val SESSION_EXPIRED_MESSAGE = "Your session expired. Please sign in again."
    const val FORBIDDEN_MESSAGE = "You don't have permission to do that."
    const val RATE_LIMIT_MESSAGE = "Too many requests. Please wait a moment and try again."

    private val TRAILING_HTTP_CODE_REGEX = Regex("""\s*\(\d{3}\)\.?$""")

    private val OFFLINE_PATTERNS = listOf(
        "unable to resolve host",
        "no address associated with hostname",
        "failed to connect to",
        "network is unreachable",
        "software caused connection abort",
        "eai_nodata",
        "econnrefused",
        "enetunreach",
        "unknownhostexception",
        "connectexception"
    )

    private val TIMEOUT_PATTERNS = listOf(
        "timed out",
        "timeout",
        "sockettimeoutexception"
    )

    private val SSL_PATTERNS = listOf(
        "ssl",
        "certpath",
        "trust anchor",
        "handshake failed",
        "pkix"
    )

    private val CONNECTION_LOST_PATTERNS = listOf(
        "connection reset",
        "connection abort",
        "broken pipe",
        "unexpected end of stream",
        "eofexception"
    )

    private val TECHNICAL_PATTERNS = listOf(
        "www.kart.cx",
        "kart.cx",
        "supabase.co",
        "pgrst",
        "sqlstate",
        "postgrest",
        "<!doctype",
        "<html",
        "java.",
        "javax.",
        "okhttp",
        "retrofit",
        "exception:",
        "nullpointerexception",
        "illegalstateexception",
        "illegalargumentexception",
        "jsonencodingexception",
        "jsondataexception"
    )

    private val SESSION_PATTERNS = listOf(
        "jwt expired",
        "invalid jwt",
        "pgrst301"
    )

    fun fromThrowable(
        error: Throwable,
        fallback: String = DEFAULT_FALLBACK
    ): String {
        if (BuildConfig.DEBUG) {
            runCatching { Log.w(LOG_TAG, fallback, error) }
        }

        val causes = generateSequence(error) { it.cause }.take(5).toList()
        for (cause in causes) {
            when (cause) {
                is UnknownHostException,
                is ConnectException,
                is NoRouteToHostException -> return OFFLINE_MESSAGE
                is SocketTimeoutException -> return TIMEOUT_MESSAGE
                is SSLException -> return SSL_MESSAGE
                is HttpException -> return fromHttpStatus(cause.code(), fallback = fallback)
            }
        }
        if (causes.any { it is IOException }) {
            val msg = causes.firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            return sanitizeMessage(msg, fallback = CONNECTION_LOST_MESSAGE)
        }

        return sanitizeMessage(error.message, fallback = fallback)
    }

    fun fromHttpStatus(
        code: Int,
        rawMessage: String? = null,
        fallback: String = DEFAULT_FALLBACK
    ): String {
        if (BuildConfig.DEBUG && code >= 400) {
            runCatching { Log.w(LOG_TAG, "HTTP $code ($fallback): ${rawMessage.orEmpty()}") }
        }
        return when (code) {
            401 -> {
                val cleaned = sanitizeRawOnly(rawMessage)
                if (cleaned != null && cleaned.contains("credential", ignoreCase = true)) {
                    cleaned
                } else {
                    SESSION_EXPIRED_MESSAGE
                }
            }
            403 -> {
                val cleaned = sanitizeRawOnly(rawMessage)
                if (cleaned != null && !cleaned.startsWith("Request failed", ignoreCase = true)) {
                    cleaned
                } else {
                    FORBIDDEN_MESSAGE
                }
            }
            408 -> TIMEOUT_MESSAGE
            429 -> RATE_LIMIT_MESSAGE
            in 500..599 -> SERVER_ERROR_MESSAGE
            else -> sanitizeMessage(rawMessage, fallback = fallback, httpCode = code)
        }
    }

    fun sanitizeMessage(
        raw: String?,
        fallback: String = DEFAULT_FALLBACK,
        httpCode: Int? = null
    ): String {
        if (httpCode != null && (httpCode in setOf(401, 403, 408, 429) || httpCode in 500..599)) {
            return fromHttpStatus(httpCode, raw, fallback)
        }
        return sanitizeRawOnly(raw) ?: fallback
    }

    fun isConnectivityError(error: Throwable): Boolean {
        val causes = generateSequence(error) { it.cause }.take(5).toList()
        if (causes.any {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is NoRouteToHostException ||
                    it is SocketTimeoutException ||
                    it is IOException
            }
        ) {
            return true
        }
        val lower = error.message?.lowercase().orEmpty()
        return OFFLINE_PATTERNS.any { it in lower } ||
            TIMEOUT_PATTERNS.any { it in lower } ||
            CONNECTION_LOST_PATTERNS.any { it in lower }
    }

    private fun sanitizeRawOnly(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = trimmed.lowercase()

        if (OFFLINE_PATTERNS.any { it in lower }) return OFFLINE_MESSAGE
        if (TIMEOUT_PATTERNS.any { it in lower }) return TIMEOUT_MESSAGE
        if (SSL_PATTERNS.any { it in lower }) return SSL_MESSAGE
        if (CONNECTION_LOST_PATTERNS.any { it in lower }) return CONNECTION_LOST_MESSAGE
        if (SESSION_PATTERNS.any { it in lower }) return SESSION_EXPIRED_MESSAGE

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return null
        if (TECHNICAL_PATTERNS.any { it in lower }) return null

        val stripped = trimmed.replace(TRAILING_HTTP_CODE_REGEX, "").trim()
        if (stripped.isBlank()) return null

        return if (stripped.endsWith(".") || stripped.endsWith("!") || stripped.endsWith("?")) {
            stripped
        } else {
            stripped
        }
    }
}
