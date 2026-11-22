package com.example.domain.model

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object KartFormatters {
    private val priceFormatter = DecimalFormat("#,##0.00")
    private val wholePriceFormatter = DecimalFormat("#,##0")
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
            wholePriceFormatter.format(safe)
        } else {
            priceFormatter.format(safe)
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

    fun parseIsoTimestamp(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
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
                val parsed = sdf.parse(iso)
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
            val j = (abs(state) % (i + 1)).toInt()
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

    fun extractMessageImageUrl(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.startsWith("[IMAGE:") && trimmed.endsWith("]")) {
            return trimmed.removePrefix("[IMAGE:").removeSuffix("]").trim()
        }
        if (trimmed.startsWith("https://") &&
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

    fun messageSnippet(content: String?): String {
        val raw = content?.trim().orEmpty()
        if (raw.isEmpty()) return "No messages yet"
        return if (extractMessageImageUrl(raw) != null) "📷 Photo" else raw
    }
}
