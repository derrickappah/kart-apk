package com.example.data.remote

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.SessionStore
import com.example.data.repository.KartRepository
import com.example.domain.model.UserSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class KartAppContainer(context: Context) {
    val sessionStore = SessionStore(context.applicationContext)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val hasValidSupabaseKey: Boolean
        get() = supabaseAnonKey.isNotBlank() && supabaseAnonKey != "YOUR_SUPABASE_ANON_KEY"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val refreshLock = Any()

    private val bareHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun extractJwtExp(jwt: String): Long? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val exp = JSONObject(String(payloadBytes, Charsets.UTF_8)).optLong("exp", 0L)
            exp.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun isJwtExpiredOrExpiringSoon(jwt: String, leewaySeconds: Long = 60L): Boolean {
        val exp = extractJwtExp(jwt) ?: return true
        return exp <= (System.currentTimeMillis() / 1000L) + leewaySeconds
    }

    private sealed interface RefreshResult {
        data class Success(val session: UserSession) : RefreshResult
        data object AuthRejected : RefreshResult
        data object TransientFailure : RefreshResult
    }

    private fun refreshSessionLocked(staleSession: UserSession): RefreshResult {
        if (!hasValidSupabaseKey) return RefreshResult.TransientFailure
        if (staleSession.refreshToken.isBlank()) return RefreshResult.AuthRejected
        return synchronized(refreshLock) {
            val latest = runBlocking { sessionStore.currentSession() }
            if (latest.isLoggedIn &&
                latest.accessToken != staleSession.accessToken &&
                !isJwtExpiredOrExpiringSoon(latest.accessToken)
            ) {
                return@synchronized RefreshResult.Success(latest)
            }
            val refreshToken = latest.refreshToken.ifBlank { staleSession.refreshToken }
            if (refreshToken.isBlank()) return@synchronized RefreshResult.AuthRejected

            try {
                val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=refresh_token"
                val jsonBody = JSONObject().put("refresh_token", refreshToken).toString()
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer $supabaseAnonKey")
                    .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                bareHttpClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@synchronized if (res.code in setOf(400, 401, 403)) {
                            RefreshResult.AuthRejected
                        } else {
                            RefreshResult.TransientFailure
                        }
                    }
                    val raw = res.body?.string().orEmpty()
                    if (raw.isBlank()) return@synchronized RefreshResult.TransientFailure
                    val json = JSONObject(raw)
                    val newAccess = json.optString("access_token", "")
                    if (newAccess.isBlank()) return@synchronized RefreshResult.TransientFailure
                    val newRefresh = json.optString("refresh_token", "").ifBlank { refreshToken }
                    val userObj = json.optJSONObject("user")
                    val updated = UserSession(
                        accessToken = newAccess,
                        refreshToken = newRefresh,
                        userId = userObj?.optString("id", "").orEmpty().ifBlank { latest.userId },
                        email = userObj?.optString("email", "").orEmpty().ifBlank { latest.email }
                    )
                    runBlocking { sessionStore.saveSession(updated) }
                    RefreshResult.Success(updated)
                }
            } catch (_: Exception) {
                RefreshResult.TransientFailure
            }
        }
    }

    private fun resolveFreshSession(): UserSession {
        val current = runBlocking { sessionStore.currentSession() }
        if (current.isLoggedIn && isJwtExpiredOrExpiringSoon(current.accessToken)) {
            when (val result = refreshSessionLocked(current)) {
                is RefreshResult.Success -> return result.session
                RefreshResult.AuthRejected -> {
                    // Only clear session when the auth server definitively rejects the refresh token
                    if (isJwtExpiredOrExpiringSoon(current.accessToken, leewaySeconds = 0L)) {
                        runBlocking { sessionStore.clearSession() }
                        return UserSession()
                    }
                }
                RefreshResult.TransientFailure -> {
                    // Keep existing session intact during offline / transient network errors
                }
            }
        }
        return current
    }

    private fun applyKartWebAuthHeaders(builder: Request.Builder, session: UserSession) {
        if (!session.isLoggedIn) return
        builder.header("Authorization", "Bearer ${session.accessToken}")
        // Attach Supabase SSR auth cookie so Next.js route handlers recognize the user session
        val projectRef = BuildConfig.SUPABASE_URL
            .removePrefix("https://")
            .substringBefore(".")
        val nowSeconds = System.currentTimeMillis() / 1000L
        val expiresAt = extractJwtExp(session.accessToken) ?: (nowSeconds + 3600L)
        val expiresIn = (expiresAt - nowSeconds).coerceAtLeast(60L)
        val cookieJson = JSONObject().apply {
            put("access_token", session.accessToken)
            put("refresh_token", session.refreshToken)
            put("token_type", "bearer")
            put("expires_in", expiresIn)
            put("expires_at", expiresAt)
            put("user", JSONObject().apply {
                put("id", session.userId)
                put("email", session.email)
            })
        }.toString()
        val encoded = Base64.encodeToString(
            cookieJson.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        builder.header("Cookie", "sb-$projectRef-auth-token=base64-$encoded")
    }

    private val supabaseAuthInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isPublicAuthEndpoint = path.startsWith("/auth/v1/token") ||
            path.startsWith("/auth/v1/signup") ||
            path.startsWith("/auth/v1/recover") ||
            path.startsWith("/auth/v1/verify")
        val hasExplicitAuth = !original.header("Authorization").isNullOrBlank()
        val session = if (isPublicAuthEndpoint || hasExplicitAuth) UserSession() else resolveFreshSession()
        val builder = original.newBuilder()
            .header("Accept", "application/json")
        if (hasValidSupabaseKey) {
            builder.header("apikey", supabaseAnonKey)
            if (!hasExplicitAuth) {
                val bearer = session.accessToken.takeIf { it.isNotBlank() } ?: supabaseAnonKey
                builder.header("Authorization", "Bearer $bearer")
            }
        }
        chain.proceed(builder.build())
    }

    private val kartWebAuthInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isPublicWebEndpoint = (original.method.equals("GET", ignoreCase = true) &&
            path.startsWith("/api/marketplace/products")) ||
            path.startsWith("/api/ads/track") ||
            path.endsWith("/increment-views") ||
            path.endsWith("/increment-shares")
        val builder = original.newBuilder()
            .header("Accept", "application/json")
        if (!isPublicWebEndpoint) {
            val session = resolveFreshSession()
            applyKartWebAuthHeaders(builder, session)
        }
        chain.proceed(builder.build())
    }

    private fun createTokenRefreshAuthenticator(isKartWeb: Boolean) = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.header("X-Kart-Auth-Retried") != null) return null
            if (!isKartWeb) {
                val path = response.request.url.encodedPath
                val isPublicAuthEndpoint = path.startsWith("/auth/v1/token") ||
                    path.startsWith("/auth/v1/signup") ||
                    path.startsWith("/auth/v1/recover") ||
                    path.startsWith("/auth/v1/verify")
                if (isPublicAuthEndpoint) return null
            }
            val current = runBlocking { sessionStore.currentSession() }
            if (!current.isLoggedIn || current.refreshToken.isBlank()) return null
            if (!isKartWeb) {
                val reqAuth = response.request.header("Authorization")
                if (!reqAuth.isNullOrBlank() && reqAuth != "Bearer ${current.accessToken}") {
                    // Request used an explicit token (e.g. password recovery token), do not swap in user's session
                    return null
                }
            }
            val refreshed = when (val result = refreshSessionLocked(current)) {
                is RefreshResult.Success -> result.session
                is RefreshResult.AuthRejected -> {
                    runBlocking { sessionStore.clearSession() }
                    return null
                }
                is RefreshResult.TransientFailure -> return null
            }
            val builder = response.request.newBuilder()
                .header("X-Kart-Auth-Retried", "1")
            if (isKartWeb) {
                applyKartWebAuthHeaders(builder, refreshed)
            } else {
                builder.header("Authorization", "Bearer ${refreshed.accessToken}")
            }
            return builder.build()
        }
    }

    private val supabaseClient = OkHttpClient.Builder()
        .addInterceptor(supabaseAuthInterceptor)
        .authenticator(createTokenRefreshAuthenticator(isKartWeb = false))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val kartWebClient = OkHttpClient.Builder()
        .addInterceptor(kartWebAuthInterceptor)
        .authenticator(createTokenRefreshAuthenticator(isKartWeb = true))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    val supabaseApi: SupabaseApi = Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
        .client(supabaseClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SupabaseApi::class.java)

    val kartWebApi: KartWebApi = Retrofit.Builder()
        .baseUrl(BuildConfig.KART_API_BASE_URL.trimEnd('/') + "/")
        .client(kartWebClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(KartWebApi::class.java)

    val repository = KartRepository(
        supabaseApi = supabaseApi,
        kartWebApi = kartWebApi,
        sessionStore = sessionStore,
        hasSupabaseKey = { hasValidSupabaseKey }
    )
}
