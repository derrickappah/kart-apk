package com.example.data.remote

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.SessionStore
import com.example.data.repository.KartRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    private val supabaseAuthInterceptor = Interceptor { chain ->
        val session = runBlocking { sessionStore.currentSession() }
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/json")
        if (hasValidSupabaseKey) {
            builder.header("apikey", supabaseAnonKey)
            val bearer = session.accessToken.takeIf { it.isNotBlank() } ?: supabaseAnonKey
            builder.header("Authorization", "Bearer $bearer")
        }
        chain.proceed(builder.build())
    }

    private val kartWebAuthInterceptor = Interceptor { chain ->
        val session = runBlocking { sessionStore.currentSession() }
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/json")
        if (session.isLoggedIn) {
            builder.header("Authorization", "Bearer ${session.accessToken}")
            // Attach Supabase SSR auth cookie so Next.js route handlers recognize the user session
            val projectRef = BuildConfig.SUPABASE_URL
                .removePrefix("https://")
                .substringBefore(".")
            val cookieJson = """{"access_token":"${session.accessToken}","refresh_token":"${session.refreshToken}","token_type":"bearer","user":{"id":"${session.userId}","email":"${session.email}"}}"""
            val encoded = Base64.encodeToString(
                cookieJson.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            builder.header("Cookie", "sb-$projectRef-auth-token=base64-$encoded")
        }
        chain.proceed(builder.build())
    }

    private val supabaseClient = OkHttpClient.Builder()
        .addInterceptor(supabaseAuthInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val kartWebClient = OkHttpClient.Builder()
        .addInterceptor(kartWebAuthInterceptor)
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
