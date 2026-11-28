package com.example.domain.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object KartFormatters {
    private val priceFormatter = ThreadLocal.withInitial { DecimalFormat("#,##0.00") }
    private val wholePriceFormatter = ThreadLocal.withInitial { DecimalFormat("#,##0") }
    private val dynamicCampusAbbreviations = ConcurrentHashMap<String, String>()
    private val campusStopWords = setOf("of", "and", "for", "the", "in", "at", "on", "&", "-")
    private val parenAcronymRegex = Regex("\\(([A-Za-z0-9-]{2,10})\\)")

    private val builtInCampusAbbreviations: Map<String, String> = mapOf(
        "university of ghana" to "UG",
        "university of ghana (legon)" to "UG",
        "university of ghana, legon" to "UG",
        "university of ghana legon" to "UG",
        "ug legon" to "UG",
        "ug" to "UG",
        "legon" to "UG",
        "university of ghana city campus" to "UG CITY",
        "kwame nkrumah university of science and technology" to "KNUST",
        "knust (kumasi)" to "KNUST",
        "knust kumasi" to "KNUST",
        "knust" to "KNUST",
        "university of cape coast" to "UCC",
        "university of cape coast (ucc)" to "UCC",
        "ucc" to "UCC",
        "ashesi university" to "ASHESI",
        "ashesi university college" to "ASHESI",
        "ashesi" to "ASHESI",
        "university of professional studies" to "UPSA",
        "university of professional studies, accra" to "UPSA",
        "university of professional studies accra" to "UPSA",
        "upsa" to "UPSA",
        "ghana institute of management and public administration" to "GIMPA",
        "gimpa" to "GIMPA",
        "central university" to "CU",
        "central university college" to "CU",
        "central university (miotso)" to "CU",
        "university for development studies" to "UDS",
        "uds" to "UDS",
        "university of mines and technology" to "UMaT",
        "umat" to "UMaT",
        "accra technical university" to "ATU",
        "atu" to "ATU",
        "academic city university" to "ACU",
        "academic city university college" to "ACU",
        "academic city" to "ACU",
        "lancaster university ghana" to "LUG",
        "lancaster university" to "LUG",
        "lug" to "LUG",
        "university of education, winneba" to "UEW",
        "university of education winneba" to "UEW",
        "uew" to "UEW",
        "ghana communication technology university" to "GCTU",
        "ghana technology university college" to "GCTU",
        "gctu" to "GCTU",
        "gtuc" to "GCTU",
        "university of health and allied sciences" to "UHAS",
        "uhas" to "UHAS",
        "university of energy and natural resources" to "UENR",
        "uenr" to "UENR",
        "university of environment and sustainable development" to "UESD",
        "uesd" to "UESD",
        "kumasi technical university" to "KsTU",
        "kstu" to "KsTU",
        "koforidua technical university" to "KTU",
        "ktu" to "KTU",
        "takoradi technical university" to "TTU",
        "ttu" to "TTU",
        "cape coast technical university" to "CCTU",
        "cctu" to "CCTU",
        "ho technical university" to "HTU",
        "htu" to "HTU",
        "sunyani technical university" to "STU",
        "stu" to "STU",
        "tamale technical university" to "TaTU",
        "tatu" to "TaTU",
        "bolgatanga technical university" to "BTU",
        "btu" to "BTU",
        "wa technical university" to "WTU",
        "wtu" to "WTU",
        "valley view university" to "VVU",
        "vvu" to "VVU",
        "methodist university ghana" to "MUG",
        "methodist university college" to "MUG",
        "mug" to "MUG",
        "presbyterian university, ghana" to "PUG",
        "presbyterian university ghana" to "PUG",
        "presbyterian university college" to "PUG",
        "pug" to "PUG",
        "pentecost university" to "PU",
        "pentecost university college" to "PU",
        "wisconsin international university college" to "WIUC",
        "wisconsin university" to "WIUC",
        "wiuc" to "WIUC",
        "regional maritime university" to "RMU",
        "rmu" to "RMU",
        "university of media, arts and communication" to "UniMAC",
        "ghana institute of journalism" to "GIJ",
        "unimac" to "UniMAC",
        "gij" to "GIJ",
        "all nations university" to "ANU",
        "anu" to "ANU",
        "catholic university of ghana" to "CUG",
        "cug" to "CUG",
        "regent university college" to "RUC",
        "regent university college of science and technology" to "RUC",
        "garden city university college" to "GCUC",
        "gcuc" to "GCUC",
        "bluecrest university college" to "BLUECREST",
        "bluecrest college" to "BLUECREST",
        "knutsford university college" to "KUC",
        "knutsford university" to "KUC",
        "islamic university college, ghana" to "IUCG",
        "islamic university college" to "IUCG",
        "dominion university college" to "DUC",
        "mountcrest university college" to "MCUC",
        "webster university ghana" to "WEBSTER",
        "accra institute of technology" to "AIT",
        "ghana institute of languages" to "GIL",
        "c. k. tedam university of technology and applied sciences" to "CKT-UTAS",
        "simon diedong dombo university of business and integrated development studies" to "SDD-UBIDS",
        "akenten appiah-menka university of skills training and entrepreneurial development" to "AAMUSTED"
    )

    fun registerCampusAbbreviations(campuses: List<CampusRow>) {
        campuses.forEach { row ->
            val abbr = row.abbreviation?.trim().orEmpty()
            val name = row.name.trim()
            if (name.isNotEmpty() && abbr.isNotEmpty()) {
                dynamicCampusAbbreviations[name.lowercase(Locale.US)] = abbr
            }
        }
    }

    fun abbreviateCampus(campus: String?): String {
        val raw = campus?.trim().orEmpty()
        if (raw.isEmpty()) return "ON CAMPUS"
        val normalized = raw.lowercase(Locale.US)

        dynamicCampusAbbreviations[normalized]?.let { return it }
        builtInCampusAbbreviations[normalized]?.let { return it }

        // Check normalized prefix/contains match for slight naming variations (e.g. "University of Ghana - Legon")
        dynamicCampusAbbreviations.entries.firstOrNull { (key, _) ->
            normalized.startsWith(key) || key.startsWith(normalized)
        }?.value?.let { return it }

        builtInCampusAbbreviations.entries.firstOrNull { (key, _) ->
            key.length >= 6 && (normalized.startsWith(key) || normalized.contains(key))
        }?.value?.let { return it }

        // Extract an uppercase acronym in parentheses if present, e.g. "Campus Name (XYZ)"
        parenAcronymRegex.find(raw)?.groupValues?.getOrNull(1)?.let { inside ->
            val letters = inside.filter { it.isLetter() }
            if (letters.length >= 2 && letters.count { it.isUpperCase() } >= letters.length - 1) {
                return inside
            }
        }

        // Keep already-short single tokens (e.g. "UPSA", "GIMPA", "Ashesi")
        if (!raw.contains(' ') && raw.length <= 10) return raw

        // Fallback: initials of significant words for any unrecognized long campus name
        val initials = raw
            .replace(Regex("\\([^)]*\\)"), " ")
            .split(Regex("[\\s,/]+"))
            .filter { token ->
                val lower = token.lowercase(Locale.US)
                token.isNotBlank() && lower !in campusStopWords && token.any { it.isLetter() }
            }
            .map { it.first().uppercaseChar() }
            .joinToString("")

        return if (initials.length in 2..8) initials else raw
    }

    fun formatPrice(price: Double?): String {
        val safe = price ?: 0.0
        return if (safe % 1.0 == 0.0) {
            wholePriceFormatter.get()!!.format(safe)
        } else {
            priceFormatter.get()!!.format(safe)
        }
    }

    fun formatCedis(price: Double?): String = "₵ ${formatPrice(price)}"

    fun toSentenceCase(input: String?): String {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return trimmed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    private val fractionalSecondsRegex = Regex("\\.(\\d+)")

    fun parseIsoTimestamp(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        // Normalize fractional seconds to exactly 3 digits (milliseconds) because SimpleDateFormat
        // treats `S` as a raw millisecond integer rather than fractional seconds.
        val normalized = fractionalSecondsRegex.replace(iso.trim()) { match ->
            val digits = match.groupValues[1]
            val millis = when {
                digits.length >= 3 -> digits.substring(0, 3)
                else -> digits.padEnd(3, '0')
            }
            ".$millis"
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(normalized)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next format
            }
        }
        return 0L
    }

    fun timeAgo(iso: String?): String {
        val ts = parseIsoTimestamp(iso)
        if (ts == 0L) return "Recently"
        val diffSec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            diffSec < 604800 -> "${diffSec / 86400}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(ts))
            }
        }
    }

    fun formatChatTime(iso: String?): String {
        val ts = parseIsoTimestamp(iso)
        if (ts == 0L) return ""
        val diffMs = System.currentTimeMillis() - ts
        return when {
            diffMs < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
            diffMs < 604_800_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ts))
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        }
    }

    fun parseReviewComment(raw: String?): Pair<String, List<String>> {
        if (raw.isNullOrBlank()) return "" to emptyList()
        val regex = Regex("\\[Tags:\\s*(.*?)\\]")
        val match = regex.find(raw) ?: return raw.trim() to emptyList()
        val tags = match.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val text = raw.replace(match.value, "").trim()
        return text to tags
    }

    fun getFairTimeSeed(windowMinutes: Int = 30, seedOffset: Int = 0): Long {
        val bucket = System.currentTimeMillis() / (windowMinutes * 60_000L)
        return bucket + seedOffset * 9973L
    }

    fun <T> seededShuffle(items: List<T>, seed: Long): List<T> {
        if (items.size <= 1) return items
        val list = items.toMutableList()
        var state = seed xor 0x5DEECE66DL
        for (i in list.lastIndex downTo 1) {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            val j = ((state ushr 1) % (i + 1)).toInt()
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }

    fun getFairRotatedPromotions(
        ads: List<Product>,
        windowMinutes: Int = 30,
        seedOffset: Int = 0
    ): List<Product> = seededShuffle(ads, getFairTimeSeed(windowMinutes, seedOffset))

    fun getDivergentFeaturedPromotions(
        activeAds: List<Product>,
        bannerProducts: List<Product>,
        windowMinutes: Int = 30,
        seedOffset: Int = 1
    ): List<Product> {
        if (activeAds.isEmpty()) return emptyList()
        if (bannerProducts.isEmpty()) {
            return getFairRotatedPromotions(activeAds, windowMinutes, seedOffset + 1)
        }
        val bannerIds = bannerProducts.map { it.id }.toSet()
        val nonBanner = activeAds.filterNot { it.id in bannerIds }
        val rotatedNonBanner = getFairRotatedPromotions(nonBanner, windowMinutes, seedOffset + 2)
        val shiftedBanner = if (bannerProducts.size > 1) {
            bannerProducts.drop(1) + bannerProducts.first()
        } else {
            bannerProducts
        }
        return rotatedNonBanner + shiftedBanner
    }

    fun sanitizeDecimalInput(input: String, maxDecimals: Int = 2): String {
        val sb = StringBuilder()
        var dotSeen = false
        var decimals = 0
        for (ch in input) {
            when {
                ch.isDigit() -> {
                    if (dotSeen) {
                        if (decimals < maxDecimals) {
                            sb.append(ch)
                            decimals++
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                ch == '.' && !dotSeen -> {
                    if (sb.isEmpty()) sb.append('0')
                    sb.append('.')
                    dotSeen = true
                }
            }
        }
        return sb.toString()
    }

    enum class ImageVariant(val width: Int, val format: String, val quality: Int) {
        CARD(width = 480, format = "webp", quality = 75),
        DETAIL(width = 1200, format = "webp", quality = 82),
        AVATAR(width = 256, format = "webp", quality = 78)
    }

    @JvmStatic
    fun isTrustedMediaUrl(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty() || !url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { java.net.URI(url).host?.lowercase(Locale.US) }.getOrNull() ?: return false
        val configuredSupabaseHost = runCatching {
            java.net.URI(com.example.BuildConfig.SUPABASE_URL).host?.lowercase(Locale.US)
        }.getOrNull()
        return host == "kart.cx" ||
            host == "www.kart.cx" ||
            host == "cdn.kart.cx" ||
            host.endsWith(".kart.cx") ||
            (configuredSupabaseHost != null && host == configuredSupabaseHost) ||
            host.endsWith(".supabase.co") ||
            host.endsWith(".supabase.in") ||
            host.endsWith(".r2.dev") ||
            host.endsWith(".r2.cloudflarestorage.com") ||
            host == "res.cloudinary.com" ||
            host == "lh3.googleusercontent.com" ||
            host == "api.dicebear.com" ||
            host == "images.unsplash.com"
    }

    /**
     * Appends Cloudflare Image Resizing query params when serving from cdn.kart.cx,
     * minimizing mobile data usage on feed cards while keeping crisp 1200px zoom on detail views.
     */
    @JvmStatic
    fun buildOptimizedImageUrl(rawUrl: String?, variant: ImageVariant = ImageVariant.CARD): String {
        if (rawUrl.isNullOrBlank()) return ""
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        if (!parsed.isHttps || !parsed.host.equals("cdn.kart.cx", ignoreCase = true)) {
            return trimmed
        }

        return parsed.newBuilder()
            .removeAllQueryParameters("width")
            .removeAllQueryParameters("format")
            .removeAllQueryParameters("quality")
            .addQueryParameter("width", variant.width.toString())
            .addQueryParameter("format", variant.format)
            .addQueryParameter("quality", variant.quality.toString())
            .build()
            .toString()
    }

    fun sanitizeSocialUrl(raw: String?, platform: String): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return when (platform.lowercase(Locale.US)) {
            "instagram" -> {
                if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
                    val host = uri.host?.lowercase(Locale.US).orEmpty()
                    if (uri.scheme.equals("https", true) && (host == "instagram.com" || host == "www.instagram.com")) {
                        trimmed
                    } else null
                } else {
                    val handle = trimmed.removePrefix("@").filter { it.isLetterOrDigit() || it == '.' || it == '_' }
                    if (handle.isNotBlank()) "https://instagram.com/$handle" else null
                }
            }
            "snapchat" -> {
                if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
                    val host = uri.host?.lowercase(Locale.US).orEmpty()
                    if (uri.scheme.equals("https", true) && (host == "snapchat.com" || host == "www.snapchat.com")) {
                        trimmed
                    } else null
                } else {
                    val handle = trimmed.removePrefix("@").filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                    if (handle.isNotBlank()) "https://snapchat.com/add/$handle" else null
                }
            }
            "whatsapp" -> {
                val digits = trimmed.filter(Char::isDigit)
                if (digits.length in 9..15) "https://wa.me/$digits" else null
            }
            else -> null
        }
    }

    fun isActiveSubscription(sub: UserSubscription?): Boolean {
        if (sub == null) return false
        if (!sub.status.equals("Active", ignoreCase = true)) return false
        val endTs = parseIsoTimestamp(sub.endDate)
        return endTs == 0L || endTs > System.currentTimeMillis()
    }

    fun parseBooleanSetting(raw: Any?, default: Boolean): Boolean = when (raw) {
        null -> default
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> {
            when (raw.trim().trim('"').lowercase(Locale.US)) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> default
            }
        }
        is Map<*, *> -> {
            when {
                raw.containsKey("enabled") -> parseBooleanSetting(raw["enabled"], default)
                raw.containsKey("value") -> parseBooleanSetting(raw["value"], default)
                else -> default
            }
        }
        else -> default
    }

    data class VoiceMessagePayload(
        val url: String,
        val durationSeconds: Int
    )

    fun extractMessageVoice(content: String): VoiceMessagePayload? {
        val trimmed = content.trim()
        if (trimmed.startsWith("[VOICE:") && trimmed.endsWith("]")) {
            val inner = trimmed.removePrefix("[VOICE:").removeSuffix("]").trim()
            val parts = inner.split("|", limit = 2)
            val url = parts.getOrNull(0)?.trim().orEmpty()
            val dur = (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0).coerceIn(0, 600)
            if (url.isNotBlank() && isTrustedMediaUrl(url)) return VoiceMessagePayload(url, dur)
            return null
        }
        if (isTrustedMediaUrl(trimmed) &&
            (trimmed.endsWith(".m4a", true) ||
                trimmed.endsWith(".aac", true) ||
                trimmed.endsWith(".mp3", true) ||
                trimmed.endsWith(".ogg", true) ||
                trimmed.endsWith(".wav", true))
        ) {
            return VoiceMessagePayload(trimmed, 0)
        }
        return null
    }

    fun formatDuration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return "%d:%02d".format(mins, secs)
    }

    fun extractMessageImageUrl(content: String): String? {
        val trimmed = content.trim()
        if (extractMessageVoice(trimmed) != null) return null
        if (trimmed.startsWith("[IMAGE:") && trimmed.endsWith("]")) {
            val url = trimmed.removePrefix("[IMAGE:").removeSuffix("]").trim()
            return url.takeIf { isTrustedMediaUrl(it) }
        }
        if (isTrustedMediaUrl(trimmed) &&
            (trimmed.contains("/storage/v1/object/") ||
                trimmed.endsWith(".jpg", true) ||
                trimmed.endsWith(".jpeg", true) ||
                trimmed.endsWith(".png", true) ||
                trimmed.endsWith(".webp", true))
        ) {
            return trimmed
        }
        return null
    }

    data class ProductMessagePayload(
        val productId: String,
        val title: String,
        val price: Double,
        val imageUrl: String?,
        val caption: String
    )

    fun encodeMessageProduct(product: Product, caption: String = ""): String {
        val encodedTitle = java.net.URLEncoder.encode(product.title.trim(), "UTF-8")
        val safeImage = product.primaryImage?.takeIf { isTrustedMediaUrl(it) }.orEmpty()
        val encodedImage = java.net.URLEncoder.encode(safeImage, "UTF-8")
        val header = "[PRODUCT:${product.id}|${product.price}|$encodedTitle|$encodedImage]"
        val trimmedCaption = caption.trim()
        return if (trimmedCaption.isEmpty()) header else "$header\n$trimmedCaption"
    }

    fun extractMessageProduct(content: String): ProductMessagePayload? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("[PRODUCT:")) return null
        val closingIdx = trimmed.indexOf(']')
        if (closingIdx <= "[PRODUCT:".length) return null
        val inner = trimmed.substring("[PRODUCT:".length, closingIdx)
        val parts = inner.split("|", limit = 4)
        if (parts.size < 3) return null
        val productId = parts[0].trim()
        val price = parts[1].trim().toDoubleOrNull() ?: return null
        val title = runCatching { java.net.URLDecoder.decode(parts[2].trim(), "UTF-8") }.getOrDefault(parts[2].trim())
        val rawImage = parts.getOrNull(3)?.trim().orEmpty()
        val decodedImage = if (rawImage.isNotEmpty()) {
            runCatching { java.net.URLDecoder.decode(rawImage, "UTF-8") }.getOrDefault(rawImage)
        } else ""
        val safeImage = decodedImage.takeIf { it.isNotBlank() && isTrustedMediaUrl(it) }
        if (productId.isBlank() || title.isBlank()) return null
        val caption = trimmed.substring(closingIdx + 1).trim()
        return ProductMessagePayload(
            productId = productId,
            title = title,
            price = price,
            imageUrl = safeImage,
            caption = caption
        )
    }

    data class ReplyMessagePayload(
        val replyToId: String,
        val senderName: String,
        val snippet: String,
        val body: String
    )

    fun encodeMessageReply(
        replyToId: String,
        senderName: String,
        snippet: String,
        body: String
    ): String {
        val safeId = replyToId.trim().replace("|", "").replace("]", "")
        val encodedSender = java.net.URLEncoder.encode(senderName.trim().take(60), "UTF-8")
        val encodedSnippet = java.net.URLEncoder.encode(snippet.trim().replace("\n", " ").take(140), "UTF-8")
        val header = "[REPLY:$safeId|$encodedSender|$encodedSnippet]"
        val trimmedBody = body.trim()
        return if (trimmedBody.isEmpty()) header else "$header\n$trimmedBody"
    }

    fun extractMessageReply(content: String): ReplyMessagePayload? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("[REPLY:")) return null
        val closingIdx = trimmed.indexOf(']')
        if (closingIdx <= "[REPLY:".length) return null
        val inner = trimmed.substring("[REPLY:".length, closingIdx)
        val parts = inner.split("|", limit = 3)
        if (parts.size < 3) return null
        val replyToId = parts[0].trim()
        val senderName = runCatching { java.net.URLDecoder.decode(parts[1].trim(), "UTF-8") }.getOrDefault(parts[1].trim())
        val snippet = runCatching { java.net.URLDecoder.decode(parts[2].trim(), "UTF-8") }.getOrDefault(parts[2].trim())
        val body = trimmed.substring(closingIdx + 1).trim()
        if (senderName.isBlank() && snippet.isBlank()) return null
        return ReplyMessagePayload(
            replyToId = replyToId,
            senderName = senderName.ifBlank { "Message" },
            snippet = snippet.ifBlank { "Message" },
            body = body
        )
    }

    fun messageSnippet(content: String?): String {
        val raw = content?.trim().orEmpty()
        if (raw.isEmpty()) return "No messages yet"
        extractMessageReply(raw)?.let { reply ->
            return messageSnippet(reply.body)
        }
        if (extractMessageVoice(raw) != null || raw.startsWith("[VOICE:")) return "🎤 Voice message"
        if (extractMessageImageUrl(raw) != null || raw.startsWith("[IMAGE:")) return "📷 Photo"
        extractMessageProduct(raw)?.let { payload ->
            return if (payload.caption.isNotBlank()) "🛍️ ${payload.title}: ${payload.caption}"
            else "🛍️ ${payload.title}"
        }
        if (raw.startsWith("[PRODUCT:")) return "🛍️ Product"
        return raw
    }
}
