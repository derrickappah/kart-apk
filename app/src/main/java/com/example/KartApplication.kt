package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.remote.KartAppContainer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class KartApplication : Application(), ImageLoaderFactory {
    lateinit var container: KartAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebaseAppCheck()
        container = KartAppContainer(this)
    }

    private fun initFirebaseAppCheck() {
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
                val factory = if (BuildConfig.DEBUG) {
                    Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                        .getMethod("getInstance")
                        .invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                } else {
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                    .installAppCheckProviderFactory(factory)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val dohClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
        val cacheTtlMs = TimeUnit.MINUTES.toMillis(5)

        val resilientDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (sysErr: UnknownHostException) {
                    val now = System.currentTimeMillis()
                    dnsCache[hostname]?.let { (cachedAt, addrs) ->
                        if (now - cachedAt < cacheTtlMs && addrs.isNotEmpty()) return addrs
                    }
                    val dohAddresses = queryCloudflareDoh(dohClient, hostname)
                    if (dohAddresses.isNotEmpty()) {
                        dnsCache[hostname] = now to dohAddresses
                        dohAddresses
                    } else {
                        throw sysErr
                    }
                }
            }
        }

        val imageHttpClient = OkHttpClient.Builder()
            .dns(resilientDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
    }

    private fun queryCloudflareDoh(client: OkHttpClient, hostname: String): List<InetAddress> {
        return runCatching {
            val encodedHost = java.net.URLEncoder.encode(hostname.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://1.1.1.1/dns-query?name=$encodedHost&type=A")
                .header("Accept", "application/dns-json")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return emptyList()
                val raw = res.body?.string().orEmpty()
                if (raw.isBlank()) return emptyList()
                val answers = JSONObject(raw).optJSONArray("Answer") ?: return emptyList()
                buildList {
                    for (i in 0 until answers.length()) {
                        val item = answers.optJSONObject(i) ?: continue
                        if (item.optInt("type") == 1) {
                            val ip = item.optString("data").trim()
                            if (ip.isNotEmpty()) {
                                runCatching { InetAddress.getByName(ip) }.getOrNull()?.let(::add)
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
