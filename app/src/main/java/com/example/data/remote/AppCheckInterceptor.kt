package com.example.data.remote

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Attaches Play Integrity backed Firebase App Check tokens on high-value routes
 * and supplies automatic Idempotency-Key headers on financial POST requests when omitted.
 *
 * FirebaseAppCheck is resolved lazily inside [tokenProvider] so unit tests and environments
 * without google-services.json never fail at construction time.
 */
class AppCheckInterceptor(
    private val tokenProvider: suspend () -> String? = defaultTokenProvider
) : Interceptor {

    companion object {
        val PROTECTED_PREFIXES = listOf(
            "/api/auth/",
            "/api/orders/create",
            "/api/orders/pay-with-wallet",
            "/api/paystack/initiate",
            "/api/wallet/deposit",
            "/api/wallet/withdraw",
            "/api/promotions/create",
            "/api/subscriptions/create"
        )

        private val defaultTokenProvider: suspend () -> String? = {
            withTimeoutOrNull(4_000L) {
                runCatching {
                    FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
                }.getOrNull()
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val requiresAppCheck = PROTECTED_PREFIXES.any { path.startsWith(it) }

        val builder = original.newBuilder()

        if (requiresAppCheck) {
            val token = runCatching {
                runBlocking {
                    tokenProvider()
                }
            }.getOrNull()

            if (!token.isNullOrBlank()) {
                builder.header("X-Firebase-AppCheck", token)
            }
        }

        if ("POST".equals(original.method, ignoreCase = true) &&
            requiresAppCheck &&
            original.header("Idempotency-Key").isNullOrBlank()
        ) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString())
        }

        return chain.proceed(builder.build())
    }
}
