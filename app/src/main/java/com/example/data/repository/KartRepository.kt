package com.example.data.repository

import android.content.ContentResolver
import android.net.Uri
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
import com.example.BuildConfig
import com.example.data.local.KartDatabase
import com.example.data.local.OrderEntity
import com.example.data.local.SessionStore
import com.example.data.paging.MarketplaceRemoteMediator
import com.example.data.paging.toFilterSpec
import com.example.data.remote.AdminConsolePageResponse
import com.example.data.remote.ConversationsCursorResponse
import com.example.data.remote.KartCursorApi
import com.example.data.remote.KartWebApi
import com.example.data.remote.MessagesCursorResponse
import com.example.data.remote.NotificationsCursorResponse
import com.example.data.remote.OrdersCursorResponse
import com.example.data.remote.PresenceBatchResponse
import com.example.data.remote.ReviewsCursorResponse
import com.example.data.remote.SupabaseApi
import com.example.data.remote.WalletTransactionsCursorResponse
import com.example.data.remote.WishlistCursorResponse
import com.example.data.upload.DirectR2Uploader
import com.example.domain.model.AccountDeletionRequestRow
import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.AdminOverviewStatsRpc
import com.example.domain.model.CampusRow
import com.example.domain.model.EnrichedConversation
import com.example.domain.model.FollowStatsRpc
import com.example.domain.model.KartErrorFormatter
import com.example.domain.model.KartFormatters
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.MarketplaceFilter
import com.example.domain.model.MarketplaceProductsResponse
import com.example.domain.model.MessageRow
import com.example.domain.model.NotificationItem
import com.example.domain.model.Order
import com.example.domain.model.PlatformFlags
import com.example.domain.model.Product
import com.example.domain.model.Profile
import com.example.domain.model.RefundRequestRow
import com.example.domain.model.ReportRow
import com.example.domain.model.Review
import com.example.domain.model.SellerSnippet
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.UserSession
import com.example.domain.model.UserSubscription
import com.example.domain.model.VerificationRequestRow
import com.example.domain.model.Wallet
import com.example.domain.model.WishlistRow
import com.example.domain.model.WithdrawalRequestRow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

data class HomeFeedData(
    val heroProducts: List<Product>,
    val featuredProducts: List<Product>,
    val latestProducts: List<Product>
)

data class ProductDetailBundle(
    val product: Product,
    val seller: Profile?,
    val reviews: List<Review>,
    val similarProducts: List<Product>,
    val isFollowingSeller: Boolean,
    val followerCount: Int,
    val canReview: Boolean = false,
    val hasUserReviewed: Boolean = false,
    val verifiedBuyerOrderId: String? = null
)

data class OrderDetailBundle(
    val order: Order,
    val hasPendingRefund: Boolean = false,
    val hasApprovedRefund: Boolean = false,
    val hasReviewed: Boolean = false
)

data class DeliveryQrData(
    val qrToken: String,
    val expiresAt: String
)

data class ProfileBundle(
    val profile: Profile,
    val listings: List<Product>,
    val reviews: List<Review>,
    val walletBalance: Double,
    val followersCount: Int,
    val followingCount: Int,
    val isFollowing: Boolean
)

data class SellerHubBundle(
    val listings: List<Product>,
    val orders: List<Order>,
    val promotions: List<ActiveAdvertisementRow>,
    val activeSubscription: UserSubscription?,
    val isSubscriptionFree: Boolean,
    val totalEarnings: Double,
    val itemsSold: Int,
    val activeListings: Int,
    val chartValues: List<Double>,
    val chartLabels: List<String>
)

data class AdminDashboardBundle(
    val users: List<Profile>,
    val products: List<Product>,
    val orders: List<Order>,
    val verifications: List<VerificationRequestRow>,
    val withdrawals: List<WithdrawalRequestRow>,
    val refunds: List<RefundRequestRow>,
    val reports: List<ReportRow>,
    val deletionRequests: List<AccountDeletionRequestRow> = emptyList(),
    val reviews: List<Review>,
    val advertisements: List<ActiveAdvertisementRow>,
    val campuses: List<CampusRow>,
    val subscriptionPlans: List<SubscriptionPlan> = emptyList(),
    val subscriptions: List<UserSubscription> = emptyList(),
    val usersById: Map<String, Profile> = emptyMap(),
    val ordersById: Map<String, Order> = emptyMap(),
    val productsById: Map<String, Product> = emptyMap(),
    val buyNowEnabled: Boolean = true,
    val isMaintenanceMode: Boolean = false,
    val promoDailyPrice: Double = 5.0,
    val promoWeeklyPrice: Double = 25.0,
    val promoFeaturedPrice: Double = 50.0,
    val platformFlags: PlatformFlags = PlatformFlags(),
    val serverStats: AdminOverviewStatsRpc? = null,
    val usersHasMore: Boolean = false,
    val productsHasMore: Boolean = false
)

class KartRepository(
    private val supabaseApi: SupabaseApi,
    private val kartWebApi: KartWebApi,
    val sessionStore: SessionStore,
    private val hasSupabaseKey: () -> Boolean,
    val chatCacheStore: com.example.data.local.ChatCacheStore? = null,
    private val kartCursorApi: KartCursorApi? = null,
    private val directR2Uploader: DirectR2Uploader? = null,
    private val databaseProvider: (() -> KartDatabase)? = null
) {
    private val trackedAdViews = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val trackedAdClicks = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val trackedProductViews = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val trackedProductShares = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile
    private var lastRegisteredFcmToken: String? = null

    companion object {
        private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024 // 5 MB
        private const val MAX_MESSAGE_LENGTH = 2000
        private const val FREE_LISTING_QUOTA = 3
        private val ALLOWED_PROFILE_FIELDS = setOf(
            "display_name", "username", "bio", "campus", "phone",
            "whatsapp_number", "instagram", "snapchat", "avatar_url"
        )
        private val ACTIVE_ORDER_STATUSES = setOf("paid", "shipped", "disputed")
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        const val KEY_BUY_NOW_ENABLED = "buy_now_enabled"
        const val KEY_PROMO_DAILY_PRICE = "promo_daily_price"
        const val KEY_PROMO_WEEKLY_PRICE = "promo_weekly_price"
        const val KEY_PROMO_FEATURED_PRICE = "promo_featured_price"
        const val KEY_MARKETPLACE_SERVICE_FEE = "marketplace_service_fee"
        const val KEY_TRANSACTION_FEE_FIXED = "transaction_fee_fixed"
        const val KEY_TRANSACTION_FEE_PERCENT = "transaction_fee_percent"
        const val KEY_WITHDRAWAL_FEE_FLAT = "withdrawal_fee_flat"
        const val KEY_MIN_DEPOSIT_AMOUNT = "min_deposit_amount"
        const val KEY_MIN_WITHDRAWAL_AMOUNT = "min_withdrawal_amount"
        const val KEY_MAX_LISTING_PRICE = "max_listing_price"
        const val KEY_PLATFORM_SUPPORT_EMAIL = "platform_support_email"
        const val KEY_WHATSAPP_SUPPORT_NUMBER = "whatsapp_support_number"

        internal fun parseHttpErrorBody(errBody: String, statusCode: Int): String? {
            val trimmed = errBody.trim()
            if (trimmed.isBlank()) return null
            val jsonMsg = runCatching {
                val json = org.json.JSONObject(trimmed)
                json.optString("error").ifBlank {
                    json.optString("message").ifBlank {
                        json.optString("details").ifBlank { null }
                    }
                }
            }.getOrNull()
            if (!jsonMsg.isNullOrBlank()) return jsonMsg

            val looksLikeHtml = trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true) ||
                trimmed.startsWith("<head", ignoreCase = true) ||
                trimmed.startsWith("<body", ignoreCase = true)
            if (looksLikeHtml) {
                return when (statusCode) {
                    404 -> "This feature isn't live on the server yet (404). Please deploy the latest backend."
                    401, 403 -> "Your session could not be authorized ($statusCode). Please sign in again."
                    in 500..599 -> "Server is temporarily unavailable ($statusCode). Please try again."
                    else -> "Unexpected server response ($statusCode). Please try again."
                }
            }
            return trimmed.take(180)
        }

        internal fun compressImageBytes(
            rawBytes: ByteArray,
            maxDimensionPx: Int = 1440,
            jpegQuality: Int = 82
        ): ByteArray? {
            if (rawBytes.isEmpty() || maxDimensionPx <= 0) return null
            return runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                val srcW = bounds.outWidth
                val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) return null

                var sampleSize = 1
                while ((srcW / sampleSize) > maxDimensionPx * 2 || (srcH / sampleSize) > maxDimensionPx * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val decoded = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return null
                var target = decoded
                try {
                    val curW = decoded.width
                    val curH = decoded.height
                    if (curW > maxDimensionPx || curH > maxDimensionPx) {
                        val ratio = kotlin.math.min(
                            maxDimensionPx.toDouble() / curW,
                            maxDimensionPx.toDouble() / curH
                        )
                        val dstW = (curW * ratio).toInt().coerceAtLeast(1)
                        val dstH = (curH * ratio).toInt().coerceAtLeast(1)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, dstW, dstH, true)
                        if (scaled !== decoded) {
                            decoded.recycle()
                            target = scaled
                        }
                    }
                    val out = java.io.ByteArrayOutputStream()
                    if (!target.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 95), out)) {
                        return null
                    }
                    val compressed = out.toByteArray()
                    compressed.takeIf { it.isNotEmpty() && it.size < rawBytes.size }
                } finally {
                    if (!target.isRecycled) target.recycle()
                }
            }.getOrNull()
        }
    }

    private fun requireKeyMessage(): String =
        "Configure SUPABASE_ANON_KEY in the AI Studio Secrets panel to sign in and access account data."

    private fun nowIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // --- Home & Marketplace Discovery ---
    suspend fun fetchHomeFeed(): ApiResult<HomeFeedData> {
        return try {
            var activeAds: List<Product> = emptyList()
            var latestProducts: List<Product> = emptyList()

            var usedRecommendedRpc = false
            if (hasSupabaseKey()) {
                val currentUserId = runCatching { sessionStore.currentSession().userId.ifBlank { null } }.getOrNull()
                val (adsResult, latestPair) = coroutineScope {
                    val adsDef = async(Dispatchers.IO) {
                        val rpcRes = runCatching {
                            supabaseApi.getHomePromotedProducts(
                                mapOf("p_limit" to 20, "p_window_minutes" to 30)
                            )
                        }.getOrNull()
                        if (rpcRes?.isSuccessful == true) {
                            rpcRes.body().orEmpty().mapNotNull { row ->
                                row.product?.copy(advertisementId = row.id, adType = row.adType)
                            }.filter { it.isActiveListing && !it.isOutOfStock }
                        } else {
                            val now = nowIsoUtc()
                            val fallbackRes = runCatching {
                                supabaseApi.getAdvertisements(
                                    mapOf(
                                        "select" to "id,ad_type,product:products!inner(*,seller:profiles(id,display_name,avatar_url,is_verified))",
                                        "status" to "eq.Active",
                                        "product.status" to "eq.Active",
                                        "start_date" to "lte.$now",
                                        "end_date" to "gte.$now",
                                        "limit" to "20"
                                    )
                                )
                            }.getOrNull()
                            if (fallbackRes?.isSuccessful == true) {
                                fallbackRes.body().orEmpty().mapNotNull { row ->
                                    row.product?.copy(advertisementId = row.id, adType = row.adType)
                                }.filter { it.isActiveListing && !it.isOutOfStock }
                            } else {
                                emptyList()
                            }
                        }
                    }

                    val latestDef = async(Dispatchers.IO) {
                        val recBody = buildMap<String, Any?> {
                            if (!currentUserId.isNullOrBlank()) put("p_user_id", currentUserId)
                            put("p_limit", 20)
                            put("p_window_minutes", 30)
                        }
                        val recRes = runCatching {
                            supabaseApi.getHomeRecommendedProducts(recBody)
                        }.getOrNull()
                        if (recRes?.isSuccessful == true) {
                            val recItems = recRes.body().orEmpty().filter { it.isActiveListing && !it.isOutOfStock }
                            if (recItems.isNotEmpty()) {
                                return@async true to recItems
                            }
                        }
                        val latestRes = runCatching {
                            supabaseApi.getProducts(
                                mapOf(
                                    "select" to "*,seller:profiles(id,display_name,avatar_url,is_verified)",
                                    "status" to "eq.Active",
                                    "stock_quantity" to "gt.0",
                                    "order" to "created_at.desc",
                                    "limit" to "20"
                                )
                            )
                        }.getOrNull()
                        val fallbackItems = if (latestRes?.isSuccessful == true) {
                            latestRes.body().orEmpty().filter { it.isActiveListing && !it.isOutOfStock }
                        } else {
                            emptyList()
                        }
                        false to fallbackItems
                    }

                    adsDef.await() to latestDef.await()
                }
                activeAds = adsResult
                usedRecommendedRpc = latestPair.first
                latestProducts = latestPair.second
            }

            // Fallback/primary live feed via public kart.cx Edge endpoint (works even without Supabase anon key)
            if (latestProducts.isEmpty()) {
                val webRes = kartWebApi.getMarketplaceProducts(page = 1, limit = 24, sort = "newest")
                if (webRes.isSuccessful) {
                    val fetched = webRes.body()?.products.orEmpty().filter { it.isActiveListing && !it.isOutOfStock }
                    latestProducts = fetched
                    if (activeAds.isEmpty()) {
                        activeAds = fetched.filter { it.advertisementId != null || it.isFeatured == true || it.isBoosted == true }
                    }
                } else {
                    return ApiResult.Error(KartErrorFormatter.fromHttpStatus(webRes.code(), fallback = "Unable to load campus feed"), webRes.code())
                }
            }

            val bannerCandidates = activeAds.filter { it.adType == "Featured" || it.adType == "Campus Ad" }
                .ifEmpty { activeAds }
            val bannerProducts = KartFormatters.getFairRotatedPromotions(bannerCandidates, 30, 0).take(5)
            val heroProducts = if (bannerProducts.isNotEmpty()) {
                bannerProducts
            } else {
                KartFormatters.seededShuffle(latestProducts, KartFormatters.getFairTimeSeed(30, 0)).take(5)
            }
            val heroIds = heroProducts.map { it.id }.toSet()

            val promotedFeatured = KartFormatters.getDivergentFeaturedPromotions(activeAds, bannerProducts, 30, 1)
                .take(15)
            val featuredProducts = if (promotedFeatured.isNotEmpty()) {
                promotedFeatured
            } else {
                val nonHero = latestProducts.filterNot { it.id in heroIds }.ifEmpty { latestProducts }
                KartFormatters.seededShuffle(nonHero, KartFormatters.getFairTimeSeed(30, 1)).take(10)
            }

            val baseRecommended = if (usedRecommendedRpc) {
                latestProducts
            } else {
                KartFormatters.seededShuffle(latestProducts, KartFormatters.getFairTimeSeed(30, 2))
            }
            val topShownIds = heroIds + featuredProducts.take(4).map { it.id }.toSet()
            val recommendedProducts = if (baseRecommended.size > topShownIds.size) {
                baseRecommended.filterNot { it.id in topShownIds } +
                    baseRecommended.filter { it.id in topShownIds }
            } else {
                baseRecommended
            }

            ApiResult.Success(
                HomeFeedData(
                    heroProducts = heroProducts,
                    featuredProducts = featuredProducts,
                    latestProducts = recommendedProducts
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while loading Home feed."))
        }
    }

    suspend fun fetchMarketplaceProducts(
        filter: MarketplaceFilter,
        page: Int = 1,
        limit: Int = 12
    ): ApiResult<MarketplaceProductsResponse> {
        return try {
            val res = kartWebApi.getMarketplaceProducts(
                page = page,
                limit = limit,
                search = filter.query,
                campus = filter.campus,
                category = filter.category,
                condition = filter.condition,
                minPrice = filter.minPrice.takeIf { it.isNotBlank() },
                maxPrice = filter.maxPrice.takeIf { it.isNotBlank() },
                sort = filter.sort
            )
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Failed to fetch marketplace listings"), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Unable to connect to Kart marketplace."))
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    fun marketplacePagerFlow(filter: MarketplaceFilter): Flow<PagingData<Product>> {
        val db = runCatching { databaseProvider?.invoke() }.getOrNull()
        val cursorApi = kartCursorApi
        val spec = filter.toFilterSpec()
        val feedKey = spec.feedKey()
        if (db != null && cursorApi != null) {
            return Pager(
                config = PagingConfig(
                    pageSize = 20,
                    prefetchDistance = 6,
                    initialLoadSize = 20,
                    enablePlaceholders = false
                ),
                remoteMediator = MarketplaceRemoteMediator(
                    filter = spec,
                    database = db,
                    api = cursorApi
                ),
                pagingSourceFactory = { db.productDao().pagingSource(feedKey) }
            ).flow.map { pagingData -> pagingData.map { entity -> entity.toDomainProduct() } }
        }
        return Pager(
            config = PagingConfig(
                pageSize = 12,
                prefetchDistance = 4,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                object : PagingSource<Int, Product>() {
                    override fun getRefreshKey(state: PagingState<Int, Product>): Int? =
                        state.anchorPosition?.let { anchor ->
                            val page = state.closestPageToPosition(anchor)
                            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
                        }

                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> {
                        val page = params.key ?: 1
                        return when (val res = fetchMarketplaceProducts(filter, page = page, limit = params.loadSize.coerceIn(10, 40))) {
                            is ApiResult.Success -> LoadResult.Page(
                                data = res.data.products,
                                prevKey = if (page > 1) page - 1 else null,
                                nextKey = if (res.data.hasMore) page + 1 else null
                            )
                            is ApiResult.Error -> LoadResult.Error(IllegalStateException(res.message))
                        }
                    }
                }
            }
        ).flow
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun fetchProductDetail(productId: String, currentUserId: String?): ApiResult<ProductDetailBundle> {
        return try {
            val useSupabase = hasSupabaseKey()

            // Wave 1: fetch product, reviews, and buyer orders in parallel (all only need productId)
            val (product, reviewsDeferred, buyerOrdersDeferred) = coroutineScope {
                val productDef = async(Dispatchers.IO) {
                    var found: Product? = null
                    if (useSupabase) {
                        val res = runCatching {
                            supabaseApi.getProducts(
                                mapOf(
                                    "select" to "*,seller:profiles(id,display_name,avatar_url,is_verified)",
                                    "id" to "eq.$productId",
                                    "limit" to "1"
                                )
                            )
                        }.getOrNull()
                        if (res?.isSuccessful == true) {
                            found = res.body()?.firstOrNull()
                        }
                        if (found == null) {
                            val plainRes = runCatching {
                                supabaseApi.getProducts(
                                    mapOf(
                                        "select" to "*",
                                        "id" to "eq.$productId",
                                        "limit" to "1"
                                    )
                                )
                            }.getOrNull()
                            if (plainRes?.isSuccessful == true) {
                                found = plainRes.body()?.firstOrNull()
                            }
                        }
                    }
                    if (found == null) {
                        val webRes = runCatching {
                            kartWebApi.getMarketplaceProducts(page = 1, limit = 50)
                        }.getOrNull()
                        found = webRes?.body()?.products?.firstOrNull { it.id == productId }
                    }
                    found
                }

                val reviewsDef = async(Dispatchers.IO) {
                    if (!useSupabase) emptyList()
                    else runCatching {
                        val rawReviews = supabaseApi.getReviews(
                            mapOf(
                                "select" to "*",
                                "product_id" to "eq.$productId",
                                "order" to "created_at.desc",
                                "limit" to "20"
                            )
                        ).body().orEmpty()
                        enrichReviewsWithProfiles(rawReviews)
                    }.getOrDefault(emptyList())
                }

                val buyerOrdersDef = async(Dispatchers.IO) {
                    if (!useSupabase || currentUserId.isNullOrBlank()) emptyList()
                    else runCatching {
                        supabaseApi.getOrders(
                            mapOf(
                                "select" to "id,status",
                                "buyer_id" to "eq.$currentUserId",
                                "product_id" to "eq.$productId",
                                "order" to "created_at.desc",
                                "limit" to "10"
                            )
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }

                Triple(productDef.await(), reviewsDef, buyerOrdersDef)
            }

            if (product == null) {
                return ApiResult.Error("Listing not found or no longer active.")
            }

            // Fire-and-forget view increment so it never blocks screen rendering
            if (trackedProductViews.add(productId)) {
                backgroundScope.launch {
                    runCatching { kartWebApi.incrementViews(productId) }
                }
            }

            val sellerId = product.sellerId ?: product.seller?.id

            // Wave 2: fetch seller profile, follows, and similar products in parallel
            coroutineScope {
                val sellerDef = async(Dispatchers.IO) {
                    if (!useSupabase || sellerId.isNullOrBlank()) null
                    else runCatching {
                        supabaseApi.getProfiles(
                            mapOf("select" to "*", "id" to "eq.$sellerId", "limit" to "1")
                        ).body()?.firstOrNull()
                    }.getOrNull()
                }

                val followsDef = async(Dispatchers.IO) {
                    if (!useSupabase || sellerId.isNullOrBlank()) null
                    else runCatching {
                        val rpcRes = supabaseApi.getFollowStats(mapOf("p_target_user_id" to sellerId))
                        if (rpcRes.isSuccessful && rpcRes.body() != null) {
                            rpcRes.body()!!
                        } else {
                            val rows = supabaseApi.getFollows(
                                mapOf(
                                    "select" to "id,follower_id,following_id",
                                    "following_id" to "eq.$sellerId",
                                    "limit" to "50"
                                )
                            ).body().orEmpty()
                            FollowStatsRpc(
                                followersCount = rows.size,
                                followingCount = 0,
                                isFollowing = currentUserId != null && rows.any { it.followerId == currentUserId }
                            )
                        }
                    }.getOrNull()
                }

                val similarDef = async(Dispatchers.IO) {
                    val category = product.category.orEmpty()
                    if (useSupabase && category.isNotBlank()) {
                        val sbSimilar = runCatching {
                            supabaseApi.getProducts(
                                mapOf(
                                    "select" to "*,seller:profiles(id,display_name,avatar_url,is_verified)",
                                    "status" to "eq.Active",
                                    "stock_quantity" to "gt.0",
                                    "category" to "eq.$category",
                                    "id" to "neq.$productId",
                                    "order" to "created_at.desc",
                                    "limit" to "6"
                                )
                            ).body().orEmpty()
                        }.getOrDefault(emptyList())
                        if (sbSimilar.isNotEmpty()) return@async sbSimilar
                    }
                    runCatching {
                        kartWebApi.getMarketplaceProducts(
                            page = 1,
                            limit = 8,
                            category = category
                        ).body()?.products.orEmpty()
                            .filter { it.id != productId && it.isActiveListing && !it.isOutOfStock }
                            .take(6)
                    }.getOrDefault(emptyList())
                }

                val reviews = reviewsDeferred.await()
                val buyerOrders = if (!currentUserId.isNullOrBlank() && currentUserId != sellerId) {
                    buyerOrdersDeferred.await()
                } else {
                    emptyList()
                }
                val verifiedBuyerOrderId = buyerOrders.firstOrNull {
                    it.status.equals("Delivered", ignoreCase = true) ||
                        it.status.equals("Completed", ignoreCase = true)
                }?.id
                val hasUserReviewed = !currentUserId.isNullOrBlank() &&
                    reviews.any { it.buyerId == currentUserId || it.reviewerId == currentUserId }

                val followStats = followsDef.await()
                val followerCount = followStats?.followersCount ?: 0
                val isFollowing = currentUserId != null && (followStats?.isFollowing == true)

                ApiResult.Success(
                    ProductDetailBundle(
                        product = product,
                        seller = sellerDef.await(),
                        reviews = reviews,
                        similarProducts = similarDef.await(),
                        isFollowingSeller = isFollowing,
                        followerCount = followerCount,
                        canReview = verifiedBuyerOrderId != null,
                        hasUserReviewed = hasUserReviewed,
                        verifiedBuyerOrderId = verifiedBuyerOrderId
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load listing details."))
        }
    }

    suspend fun fetchProductById(productId: String): ApiResult<Product> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getProducts(mapOf("select" to "*", "id" to "eq.$productId", "limit" to "1"))
            val product = res.body()?.firstOrNull()
            if (res.isSuccessful && product != null) ApiResult.Success(product)
            else ApiResult.Error("Listing not found.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load listing."))
        }
    }

    suspend fun trackAdEvent(adId: String?, eventType: String) {
        if (adId.isNullOrBlank()) return
        val cache = if (eventType == "click") trackedAdClicks else trackedAdViews
        if (!cache.add(adId)) return
        runCatching {
            kartWebApi.trackAdEvent(mapOf("advertisementId" to adId, "eventType" to eventType))
        }
    }

    suspend fun trackShare(productId: String) {
        if (!trackedProductShares.add(productId)) return
        runCatching { kartWebApi.incrementShares(productId) }
    }

    // --- Authentication ---
    suspend fun signIn(email: String, password: String): ApiResult<UserSession> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.signInWithPassword(
                body = mapOf("email" to email.trim().lowercase(), "password" to password)
            )
            if (res.isSuccessful && res.body()?.accessToken != null) {
                val body = res.body()!!
                val session = UserSession(
                    accessToken = body.accessToken.orEmpty(),
                    refreshToken = body.refreshToken.orEmpty(),
                    userId = body.user?.id.orEmpty(),
                    email = body.user?.email ?: email.trim().lowercase()
                )
                val saved = sessionStore.saveSession(session)
                if (!saved) {
                    return ApiResult.Error("Unable to securely store your session on this device. Please try again.")
                }
                ApiResult.Success(session)
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                val msg = when {
                    errBody.contains("Email not confirmed", true) -> "Please confirm your email address before signing in."
                    errBody.contains("Invalid login credentials", true) -> "Invalid email or password."
                    else -> KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Sign in failed. Please verify your credentials.")
                }
                ApiResult.Error(msg, res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while signing in."))
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String? = null): ApiResult<UserSession> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val payload = buildMap {
                put("provider", "google")
                put("id_token", idToken)
                if (!rawNonce.isNullOrBlank()) {
                    put("nonce", rawNonce)
                }
            }
            val res = supabaseApi.signInWithIdToken(body = payload)
            if (res.isSuccessful && res.body()?.accessToken != null) {
                val body = res.body()!!
                val session = UserSession(
                    accessToken = body.accessToken.orEmpty(),
                    refreshToken = body.refreshToken.orEmpty(),
                    userId = body.user?.id.orEmpty(),
                    email = body.user?.email.orEmpty()
                )
                val saved = sessionStore.saveSession(session)
                if (!saved) {
                    return ApiResult.Error("Unable to securely store your session on this device. Please try again.")
                }
                ApiResult.Success(session)
            } else {
                ApiResult.Error(
                    KartErrorFormatter.fromHttpStatus(
                        res.code(),
                        extractHttpErrorMessage(res),
                        "Google sign-in failed. Please try again."
                    ),
                    res.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while signing in with Google."))
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        campus: String,
        phone: String
    ): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.signUp(
                mapOf(
                    "email" to email.trim().lowercase(),
                    "password" to password,
                    "data" to mapOf(
                        "display_name" to displayName.trim(),
                        "campus" to campus.trim(),
                        "phone" to phone.trim()
                    )
                )
            )
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.accessToken != null && body.user != null) {
                    val saved = sessionStore.saveSession(
                        UserSession(
                            accessToken = body.accessToken,
                            refreshToken = body.refreshToken.orEmpty(),
                            userId = body.user.id,
                            email = body.user.email ?: email.trim().lowercase()
                        )
                    )
                    if (!saved) {
                        return ApiResult.Error("Account created, but we couldn't securely save your session. Please sign in again.")
                    }
                    ApiResult.Success("Account created and signed in!")
                } else {
                    ApiResult.Success("Account created! Check your email to verify your Kart account.")
                }
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Sign up failed. Email may already be registered."), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error during sign up."))
        }
    }

    suspend fun exchangeMobileAuthCode(code: String, state: String?): ApiResult<UserSession> {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return ApiResult.Error("Missing authorization code.")
        val pending = sessionStore.consumePendingPkce()
            ?: return ApiResult.Error("Sign-in session expired. Please try signing in with Google again.")
        val (verifier, expectedState) = pending
        if (expectedState.isNotBlank() && state?.trim() != expectedState) {
            return ApiResult.Error("Sign-in verification failed (state mismatch). Please try again.")
        }
        return try {
            val res = kartWebApi.exchangeMobileAuthCode(
                mapOf("code" to cleanCode, "code_verifier" to verifier)
            )
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val accessToken = body["access_token"]?.toString().orEmpty()
                val refreshToken = body["refresh_token"]?.toString().orEmpty()
                val userMap = body["user"] as? Map<*, *>
                val userId = userMap?.get("id")?.toString().orEmpty()
                val email = userMap?.get("email")?.toString().orEmpty()
                if (accessToken.isBlank() || userId.isBlank()) {
                    return ApiResult.Error("Incomplete sign-in response from server.")
                }
                val session = UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = userId,
                    email = email
                )
                val saved = sessionStore.saveSession(session)
                if (!saved) {
                    return ApiResult.Error("Unable to securely store your session on this device. Please try again.")
                }
                ApiResult.Success(session)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Google sign-in failed."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while completing Google sign-in."))
        }
    }

    suspend fun refreshSessionWithToken(refreshToken: String): ApiResult<UserSession> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.refreshToken(body = mapOf("refresh_token" to refreshToken))
            if (res.isSuccessful && res.body()?.accessToken != null) {
                val body = res.body()!!
                val session = UserSession(
                    accessToken = body.accessToken.orEmpty(),
                    refreshToken = body.refreshToken ?: refreshToken,
                    userId = body.user?.id.orEmpty(),
                    email = body.user?.email.orEmpty()
                )
                val saved = sessionStore.saveSession(session)
                if (!saved) {
                    return ApiResult.Error("Unable to securely store your session on this device. Please try again.")
                }
                ApiResult.Success(session)
            } else {
                ApiResult.Error("Session expired. Please sign in again.")
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to refresh session."))
        }
    }

    suspend fun recoverPassword(email: String): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val normalizedEmail = email.trim().lowercase()
        return try {
            val res = supabaseApi.recoverPassword(mapOf("email" to normalizedEmail))
            if (res.isSuccessful) {
                ApiResult.Success("A verification code has been sent to $normalizedEmail.")
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                val msg = when {
                    errBody.contains("rate limit", true) || res.code() == 429 ->
                        "Too many reset attempts. Please wait a moment and try again."
                    else -> KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not send reset code.")
                }
                ApiResult.Error(msg)
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to send reset code."))
        }
    }

    suspend fun verifyPasswordResetOtp(email: String, code: String): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val normalizedEmail = email.trim().lowercase()
        val normalizedCode = code.trim()
        return try {
            val res = supabaseApi.verifyOtp(
                mapOf(
                    "email" to normalizedEmail,
                    "token" to normalizedCode,
                    "type" to "recovery"
                )
            )
            if (res.isSuccessful) {
                val accessToken = res.body()?.accessToken.orEmpty()
                if (accessToken.isNotBlank()) {
                    ApiResult.Success(accessToken)
                } else {
                    ApiResult.Error("Verification succeeded, but no recovery session was returned.")
                }
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                val msg = when {
                    errBody.contains("expired", true) -> "This code has expired. Please request a new code."
                    errBody.contains("invalid", true) || res.code() in setOf(400, 401, 403) ->
                        "Invalid or expired verification code. Please check and try again."
                    else -> KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not verify code.")
                }
                ApiResult.Error(msg)
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to verify code."))
        }
    }

    suspend fun completePasswordReset(recoveryAccessToken: String, newPassword: String): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.updateAuthUser(
                authorization = "Bearer $recoveryAccessToken",
                body = mapOf("password" to newPassword)
            )
            if (res.isSuccessful) {
                ApiResult.Success("Your password has been updated. You can now sign in.")
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                val msg = when {
                    errBody.contains("different from the old password", true) ->
                        "New password must be different from your previous password."
                    errBody.contains("weak", true) || errBody.contains("at least", true) ->
                        "Please choose a stronger password (at least 6 characters)."
                    res.code() in setOf(401, 403) ->
                        "Your reset session expired. Please request a new 6-digit code."
                    else -> KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not update password.")
                }
                ApiResult.Error(msg)
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to update password."))
        }
    }

    private suspend fun resolveCurrentFcmToken(): String? {
        lastRegisteredFcmToken?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            kotlinx.coroutines.withTimeoutOrNull(2500L) {
                kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->
                            if (cont.isActive) cont.resumeWith(Result.success(token))
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resumeWith(Result.success(null))
                        }
                }
            }
        }.getOrNull()
    }

    suspend fun unregisterFcmToken() {
        if (!hasSupabaseKey()) return
        val tokenToRemove = resolveCurrentFcmToken()
        if (!tokenToRemove.isNullOrBlank()) {
            runCatching {
                val rpc = supabaseApi.deleteDeviceTokenRpc(mapOf("p_fcm_token" to tokenToRemove))
                if (!rpc.isSuccessful) {
                    supabaseApi.deleteDeviceToken("eq.$tokenToRemove")
                }
            }
            lastRegisteredFcmToken = null
        }
    }

    suspend fun signOut() {
        unregisterFcmToken()
        runCatching {
            if (hasSupabaseKey()) supabaseApi.logout()
        }
        cachedAdminBundle = null
        cachedAdminFetchedAtMs = 0L
        sessionStore.clearSession()
    }

    // --- Profile, Status, Wishlist, Notifications ---
    suspend fun fetchLatestVerificationRequest(userId: String): VerificationRequestRow? {
        if (!hasSupabaseKey() || userId.isBlank()) return null
        return runCatching {
            supabaseApi.getVerificationRequests(
                mapOf(
                    "select" to "*",
                    "user_id" to "eq.$userId",
                    "order" to "created_at.desc",
                    "limit" to "1"
                )
            ).body()?.firstOrNull()
        }.getOrNull()
    }

    suspend fun fetchMyProfile(userId: String): Profile? {
        if (!hasSupabaseKey() || userId.isBlank()) return null
        val profile = runCatching {
            supabaseApi.getProfiles(mapOf("select" to "*", "id" to "eq.$userId", "limit" to "1"))
                .body()?.firstOrNull()
        }.getOrNull() ?: return null

        if (profile.isVerified != true) {
            val latestReq = fetchLatestVerificationRequest(userId)
            val reqStatus = latestReq?.status?.trim()
            if (!reqStatus.isNullOrBlank()) {
                val normalized = when (reqStatus.lowercase(Locale.US)) {
                    "pending", "under_review", "in_review", "submitted" -> "Pending"
                    "approved", "verified" -> "Approved"
                    "rejected", "declined" -> "Rejected"
                    else -> reqStatus
                }
                return profile.copy(
                    isVerified = if (normalized == "Approved") true else profile.isVerified,
                    verificationStatus = normalized,
                    verificationRejectionReason = latestReq.resolvedNotes ?: profile.verificationRejectionReason
                )
            }
        }
        return profile
    }

    @Volatile
    private var lastKnownPlatformFlags: PlatformFlags? = null
    @Volatile
    private var platformFlagsFetchedAtMs: Long = 0L

    @Volatile
    private var cachedCampuses: List<String>? = null
    @Volatile
    private var campusesFetchedAtMs: Long = 0L

    suspend fun fetchPlatformFlags(forceRefresh: Boolean = false): PlatformFlags {
        if (!hasSupabaseKey()) return lastKnownPlatformFlags ?: PlatformFlags()
        val now = System.currentTimeMillis()
        val cached = lastKnownPlatformFlags
        if (!forceRefresh && cached != null && (now - platformFlagsFetchedAtMs) < 60_000L) {
            return cached
        }
        return runCatching {
            val res = supabaseApi.getPlatformSettings(
                mapOf(
                    "select" to "key,value,category,label,description",
                    "limit" to "100"
                )
            )
            if (!res.isSuccessful) {
                return@runCatching lastKnownPlatformFlags ?: PlatformFlags()
            }
            val rows = res.body().orEmpty()
            val byKey = rows.associateBy { it.key.lowercase(Locale.US) }
            fun parseDoubleSetting(raw: Any?, default: Double): Double = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.trim().trim('"').trim().toDoubleOrNull() ?: default
                else -> default
            }
            fun parseStringSetting(raw: Any?, default: String): String {
                val cleaned = raw?.toString()?.trim()?.trim('"')?.trim().orEmpty()
                return if (cleaned.isNotBlank() && !cleaned.equals("null", true)) cleaned else default
            }
            val parsed = PlatformFlags(
                isMaintenanceMode = KartFormatters.parseBooleanSetting(
                    byKey[KEY_MAINTENANCE_MODE]?.value,
                    default = lastKnownPlatformFlags?.isMaintenanceMode ?: false
                ),
                buyNowEnabled = KartFormatters.parseBooleanSetting(
                    byKey[KEY_BUY_NOW_ENABLED]?.value,
                    default = lastKnownPlatformFlags?.buyNowEnabled ?: true
                ),
                promoDailyPrice = parseDoubleSetting(byKey[KEY_PROMO_DAILY_PRICE]?.value, 5.0),
                promoWeeklyPrice = parseDoubleSetting(byKey[KEY_PROMO_WEEKLY_PRICE]?.value, 25.0),
                promoFeaturedPrice = parseDoubleSetting(byKey[KEY_PROMO_FEATURED_PRICE]?.value, 50.0),
                marketplaceServiceFee = parseDoubleSetting(byKey[KEY_MARKETPLACE_SERVICE_FEE]?.value, 2.0),
                transactionFeeFixed = parseDoubleSetting(byKey[KEY_TRANSACTION_FEE_FIXED]?.value, 1.0),
                transactionFeePercent = parseDoubleSetting(byKey[KEY_TRANSACTION_FEE_PERCENT]?.value, 0.0),
                withdrawalFeeFlat = parseDoubleSetting(byKey[KEY_WITHDRAWAL_FEE_FLAT]?.value, 1.0),
                minDepositAmount = parseDoubleSetting(byKey[KEY_MIN_DEPOSIT_AMOUNT]?.value, 10.0),
                minWithdrawalAmount = parseDoubleSetting(byKey[KEY_MIN_WITHDRAWAL_AMOUNT]?.value, 10.0),
                maxListingPrice = parseDoubleSetting(byKey[KEY_MAX_LISTING_PRICE]?.value, 50000.0),
                supportEmail = parseStringSetting(byKey[KEY_PLATFORM_SUPPORT_EMAIL]?.value, "support@kart.com"),
                whatsappSupportNumber = parseStringSetting(byKey[KEY_WHATSAPP_SUPPORT_NUMBER]?.value, "")
            )
            lastKnownPlatformFlags = parsed
            platformFlagsFetchedAtMs = System.currentTimeMillis()
            parsed
        }.getOrElse { lastKnownPlatformFlags ?: PlatformFlags() }
    }

    suspend fun isMaintenanceMode(forceRefresh: Boolean = false): Boolean =
        fetchPlatformFlags(forceRefresh).isMaintenanceMode

    suspend fun isBuyNowEnabled(forceRefresh: Boolean = false): Boolean =
        fetchPlatformFlags(forceRefresh).buyNowEnabled

    suspend fun fetchProfileBundle(targetUserId: String, currentUserId: String?): ApiResult<ProfileBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            coroutineScope {
                val profileDef = async(Dispatchers.IO) {
                    supabaseApi.getProfiles(
                        mapOf("select" to "*", "id" to "eq.$targetUserId", "limit" to "1")
                    ).body()?.firstOrNull()
                }
                val listingsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getProducts(
                            mapOf(
                                "select" to "*",
                                "seller_id" to "eq.$targetUserId",
                                "order" to "created_at.desc",
                                "limit" to "50"
                            )
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val reviewsDef = async(Dispatchers.IO) {
                    runCatching {
                        val rawReviews = supabaseApi.getReviews(
                            mapOf(
                                "select" to "*",
                                "seller_id" to "eq.$targetUserId",
                                "order" to "created_at.desc",
                                "limit" to "50"
                            )
                        ).body().orEmpty()
                        enrichReviewsWithProfiles(rawReviews)
                    }.getOrDefault(emptyList())
                }
                val walletDef = async(Dispatchers.IO) {
                    if (targetUserId == currentUserId) {
                        runCatching {
                            supabaseApi.getWallets(mapOf("select" to "balance", "user_id" to "eq.$targetUserId", "limit" to "1"))
                                .body()?.firstOrNull()?.balance ?: 0.0
                        }.getOrDefault(0.0)
                    } else 0.0
                }
                val followStatsDef = async(Dispatchers.IO) {
                    runCatching {
                        val rpcRes = supabaseApi.getFollowStats(mapOf("p_target_user_id" to targetUserId))
                        if (rpcRes.isSuccessful && rpcRes.body() != null) {
                            rpcRes.body()!!
                        } else {
                            FollowStatsRpc()
                        }
                    }.getOrDefault(FollowStatsRpc())
                }

                val profile = profileDef.await() ?: return@coroutineScope ApiResult.Error("User profile not found.")
                val followStats = followStatsDef.await()

                ApiResult.Success(
                    ProfileBundle(
                        profile = profile,
                        listings = listingsDef.await(),
                        reviews = reviewsDef.await(),
                        walletBalance = walletDef.await(),
                        followersCount = followStats.followersCount,
                        followingCount = followStats.followingCount,
                        isFollowing = currentUserId != null && followStats.isFollowing
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load profile."))
        }
    }

    suspend fun fetchWishlist(userId: String): ApiResult<List<WishlistRow>> {
        val cursorApi = kartCursorApi
        if (cursorApi != null) {
            val cursorRes = runCatching { cursorApi.getWishlist(include = "products", limit = 50) }.getOrNull()
            if (cursorRes != null) {
                val rows = cursorRes.items.map { item ->
                    if (item.userId.isNullOrBlank()) item.copy(userId = userId) else item
                }
                return ApiResult.Success(rows)
            }
        }
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getWishlist(
                mapOf(
                    "select" to "*,product:products(*,seller:profiles(id,display_name,avatar_url,is_verified))",
                    "user_id" to "eq.$userId",
                    "order" to "created_at.desc",
                    "limit" to "100"
                )
            )
            if (res.isSuccessful) ApiResult.Success(res.body().orEmpty())
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Failed to fetch wishlist"))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error loading wishlist."))
        }
    }

    suspend fun fetchWishlistPage(cursor: String? = null, limit: Int = 20): ApiResult<WishlistCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(api.getWishlist(include = "products", cursor = cursor, limit = limit))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load wishlist page."))
        }
    }

    suspend fun toggleWishlist(productId: String, currentlySaved: Boolean): ApiResult<Boolean> {
        return try {
            val res = if (currentlySaved) {
                kartWebApi.removeFromWishlist(mapOf("productId" to productId))
            } else {
                kartWebApi.addToWishlist(mapOf("productId" to productId))
            }
            if (res.isSuccessful) ApiResult.Success(!currentlySaved)
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not update wishlist."))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Wishlist update failed."))
        }
    }

    private suspend fun enrichReviewsWithProfiles(reviews: List<Review>): List<Review> {
        if (reviews.isEmpty() || !hasSupabaseKey()) return reviews
        val reviewerIds = reviews.mapNotNull { it.buyerId ?: it.reviewerId }.filter { it.isNotBlank() }.distinct()
        if (reviewerIds.isEmpty()) return reviews
        val profilesById = runCatching {
            supabaseApi.getProfiles(
                mapOf(
                    "select" to "id,display_name,username,avatar_url,is_verified",
                    "id" to "in.(${reviewerIds.joinToString(",")})"
                )
            ).body().orEmpty().associateBy { it.id }
        }.getOrDefault(emptyMap())
        return reviews.map { rev ->
            val uid = rev.buyerId ?: rev.reviewerId
            val p = uid?.let { profilesById[it] }
            if (p != null) {
                rev.copy(
                    reviewer = SellerSnippet(
                        id = p.id,
                        displayName = p.displayName,
                        username = p.username,
                        avatarUrl = p.avatarUrl,
                        isVerified = p.isVerified
                    )
                )
            } else rev
        }
    }

    suspend fun toggleFollow(targetUserId: String): ApiResult<Boolean> {
        return try {
            val res = kartWebApi.toggleFollow(
                mapOf(
                    "followingId" to targetUserId,
                    "following_id" to targetUserId,
                    "targetUserId" to targetUserId
                )
            )
            if (res.isSuccessful) {
                val following = res.body()?.get("following") as? Boolean ?: true
                ApiResult.Success(following)
            } else ApiResult.Error("Could not update follow status.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Follow toggle failed."))
        }
    }

    suspend fun fetchFollowProfiles(userId: String, type: String): List<Profile> {
        if (!hasSupabaseKey()) return emptyList()
        return runCatching {
            val rows = if (type == "followers") {
                supabaseApi.getFollows(
                    mapOf("select" to "follower_id", "following_id" to "eq.$userId", "limit" to "50")
                ).body().orEmpty().mapNotNull { it.followerId }
            } else {
                supabaseApi.getFollows(
                    mapOf("select" to "following_id", "follower_id" to "eq.$userId", "limit" to "50")
                ).body().orEmpty().mapNotNull { it.followingId }
            }.distinct()
            if (rows.isEmpty()) emptyList()
            else rows.chunked(50).flatMap { chunk ->
                supabaseApi.getProfiles(
                    mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})")
                ).body().orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchNotifications(userId: String): ApiResult<List<NotificationItem>> {
        val cursorApi = kartCursorApi
        if (cursorApi != null) {
            val cursorRes = runCatching { cursorApi.getNotifications(limit = 50) }.getOrNull()
            if (cursorRes != null && cursorRes.error.isNullOrBlank()) {
                val normalized = cursorRes.notifications.map { item ->
                    if (item.link.isNullOrBlank() && !item.relatedOrderId.isNullOrBlank()) {
                        item.copy(link = "/orders/${item.relatedOrderId}")
                    } else item
                }
                return ApiResult.Success(normalized)
            }
        }
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getNotifications(
                mapOf("select" to "*", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "50")
            )
            if (res.isSuccessful) ApiResult.Success(res.body().orEmpty())
            else ApiResult.Error("Failed to load notifications.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error loading notifications."))
        }
    }

    suspend fun fetchNotificationsPage(
        cursor: String? = null,
        limit: Int = 25,
        unreadOnly: Boolean = false
    ): ApiResult<NotificationsCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(api.getNotifications(cursor = cursor, limit = limit, unreadOnly = unreadOnly))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load notifications page."))
        }
    }

    suspend fun fetchUnreadSummary(userId: String): Pair<Int, Int> {
        if (!hasSupabaseKey() || userId.isBlank()) return 0 to 0
        val rpcRes = runCatching { supabaseApi.getUnreadSummary() }.getOrNull()
        val body = rpcRes?.body()
        if (rpcRes?.isSuccessful == true && body != null) {
            return body.unreadNotifications to body.unreadMessages
        }
        return coroutineScope {
            val notifsDef = async(Dispatchers.IO) { fetchUnreadNotificationsCount(userId) }
            val msgsDef = async(Dispatchers.IO) { fetchUnreadMessagesCount() }
            notifsDef.await() to msgsDef.await()
        }
    }

    suspend fun fetchUnreadNotificationsCount(userId: String): Int {
        if (!hasSupabaseKey() || userId.isBlank()) return 0
        return runCatching {
            supabaseApi.getNotifications(
                mapOf(
                    "select" to "id",
                    "user_id" to "eq.$userId",
                    "is_read" to "eq.false",
                    "limit" to "100"
                )
            ).body().orEmpty().size
        }.getOrDefault(0)
    }

    suspend fun markNotificationRead(notificationId: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.markNotificationRead("eq.$notificationId", mapOf("is_read" to true))
            if (res.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Could not mark notification as read.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error updating notification."))
        }
    }

    suspend fun markAllNotificationsRead(userId: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.markAllNotificationsRead(
                userIdFilter = "eq.$userId",
                isReadFilter = "eq.false",
                body = mapOf("is_read" to true)
            )
            if (res.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not mark notifications as read."))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error updating notifications."))
        }
    }

    suspend fun deleteNotification(notificationId: String): ApiResult<Unit> {
        return try {
            val res = kartWebApi.deleteNotification(mapOf("notificationId" to notificationId))
            if (res.isSuccessful) ApiResult.Success(Unit) else ApiResult.Error("Could not delete notification.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error deleting notification."))
        }
    }

    // --- Orders & Checkout ---
    suspend fun fetchOrders(userId: String, asSeller: Boolean = false): ApiResult<List<Order>> {
        val role = if (asSeller) "seller" else "buyer"
        val cursorApi = kartCursorApi
        if (cursorApi != null) {
            val cursorRes = runCatching { cursorApi.getOrders(role = role, limit = 50) }.getOrNull()
            if (cursorRes != null && cursorRes.error.isNullOrBlank()) {
                val orders = cursorRes.orders
                cacheOrdersLocally(role, orders)
                return ApiResult.Success(orders)
            }
        }
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val filterCol = if (asSeller) "seller_id" else "buyer_id"
            val res = supabaseApi.getOrders(
                mapOf(
                    "select" to "*,product:products(id,title,images,image_url),seller:profiles!orders_seller_id_profiles_fkey(id,display_name,email,is_verified,avatar_url),buyer:profiles!orders_buyer_id_profiles_fkey(id,display_name,email,is_verified,avatar_url)",
                    filterCol to "eq.$userId",
                    "order" to "created_at.desc",
                    "limit" to "100"
                )
            )
            if (res.isSuccessful) {
                val orders = res.body().orEmpty()
                cacheOrdersLocally(role, orders)
                ApiResult.Success(orders)
            } else {
                // Fallback without explicit foreign key hint
                val fallback = supabaseApi.getOrders(
                    mapOf(
                        "select" to "*,product:products(id,title,images,image_url)",
                        filterCol to "eq.$userId",
                        "order" to "created_at.desc",
                        "limit" to "100"
                    )
                )
                if (fallback.isSuccessful) {
                    val orders = fallback.body().orEmpty()
                    cacheOrdersLocally(role, orders)
                    ApiResult.Success(orders)
                } else ApiResult.Error(KartErrorFormatter.fromHttpStatus(fallback.code(), fallback = "Failed to load orders."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error loading orders."))
        }
    }

    private suspend fun cacheOrdersLocally(role: String, orders: List<Order>) {
        val db = runCatching { databaseProvider?.invoke() }.getOrNull() ?: return
        runCatching {
            val entities = orders.map { order ->
                OrderEntity(
                    id = order.id,
                    role = role,
                    productId = order.productId ?: order.product?.id,
                    productTitle = order.product?.title,
                    productImageUrl = order.product?.primaryImage?.let {
                        KartFormatters.buildOptimizedImageUrl(it, KartFormatters.ImageVariant.CARD).ifBlank { it }
                    },
                    totalAmount = order.totalAmount ?: 0.0,
                    status = order.status.orEmpty(),
                    escrowStatus = order.escrowStatus,
                    createdAt = order.createdAt.orEmpty()
                )
            }
            db.orderDao().insertAll(entities)
        }
    }

    suspend fun fetchOrdersPage(
        cursor: String? = null,
        limit: Int = 20,
        role: String = "buyer",
        status: String? = null
    ): ApiResult<OrdersCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(api.getOrders(cursor = cursor, limit = limit, role = role, status = status))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load orders page."))
        }
    }

    suspend fun fetchReviewsPage(
        productId: String? = null,
        sellerId: String? = null,
        cursor: String? = null,
        limit: Int = 20
    ): ApiResult<ReviewsCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            val res = api.getReviews(productId = productId, sellerId = sellerId, cursor = cursor, limit = limit)
            val normalized = res.copy(
                reviews = res.reviews.map { rev ->
                    if (rev.reviewer == null && rev.buyer != null) rev.copy(reviewer = rev.buyer) else rev
                }
            )
            ApiResult.Success(normalized)
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load reviews."))
        }
    }

    suspend fun fetchConversationsPage(cursor: String? = null, limit: Int = 25): ApiResult<ConversationsCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(api.getConversations(cursor = cursor, limit = limit))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load conversations page."))
        }
    }

    suspend fun fetchConversationMessagesPage(
        conversationId: String,
        cursor: String? = null,
        limit: Int = 50
    ): ApiResult<MessagesCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(api.getConversationMessages(conversationId = conversationId, cursor = cursor, limit = limit))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load messages page."))
        }
    }

    suspend fun fetchPresenceBatch(userIds: List<String>): PresenceBatchResponse? {
        val api = kartCursorApi ?: return null
        val csv = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(40).joinToString(",")
        if (csv.isEmpty()) return PresenceBatchResponse()
        return runCatching { api.getPresenceBatch(csv) }.getOrNull()
    }

    suspend fun touchPresence() {
        val api = kartCursorApi ?: return
        runCatching { api.touchPresence() }
    }

    suspend fun fetchAdminConsolePage(
        resource: String,
        cursor: String? = null,
        limit: Int = 20,
        search: String? = null,
        status: String? = null,
        includeKpis: Boolean = false
    ): ApiResult<AdminConsolePageResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            ApiResult.Success(
                api.getAdminConsolePage(
                    resource = resource,
                    cursor = cursor,
                    limit = limit,
                    q = search,
                    status = status,
                    includeKpis = includeKpis
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load admin console page."))
        }
    }

    suspend fun fetchOrderDetail(orderId: String): ApiResult<OrderDetailBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val currentSession = sessionStore.currentSession()
        if (!currentSession.isLoggedIn) return ApiResult.Error("Please sign in to view order details.")
        return try {
            val res = supabaseApi.getOrders(
                mapOf(
                    "select" to "*,product:products(id,title,images,image_url),seller:profiles!orders_seller_id_profiles_fkey(id,display_name,email,is_verified,avatar_url),buyer:profiles!orders_buyer_id_profiles_fkey(id,display_name,email,is_verified,avatar_url)",
                    "id" to "eq.$orderId",
                    "limit" to "1"
                )
            )
            val order = res.body()?.firstOrNull()
                ?: supabaseApi.getOrders(mapOf("select" to "*,product:products(id,title,images,image_url)", "id" to "eq.$orderId", "limit" to "1")).body()?.firstOrNull()
                ?: return ApiResult.Error("Order not found.")

            val isParticipant = order.buyerId == currentSession.userId || order.sellerId == currentSession.userId
            if (!isParticipant) {
                val isAdmin = fetchMyProfile(currentSession.userId)?.isAdmin == true
                if (!isAdmin) return ApiResult.Error("You are not authorized to view this order.")
            }

            val refundRequests = runCatching {
                supabaseApi.getRefundRequests(
                    mapOf("select" to "id,status", "order_id" to "eq.$orderId", "limit" to "5")
                ).body().orEmpty()
            }.getOrDefault(emptyList())
            val hasPendingRefund = refundRequests.any { it.status.equals("Pending", ignoreCase = true) }
            val hasApprovedRefund = refundRequests.any { it.status.equals("Approved", ignoreCase = true) }

            val hasReviewed = runCatching {
                supabaseApi.getReviews(
                    mapOf("select" to "id", "order_id" to "eq.$orderId", "limit" to "1")
                ).body().orEmpty().isNotEmpty()
            }.getOrDefault(false)

            ApiResult.Success(
                OrderDetailBundle(
                    order = order,
                    hasPendingRefund = hasPendingRefund,
                    hasApprovedRefund = hasApprovedRefund,
                    hasReviewed = hasReviewed
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load order."))
        }
    }

    suspend fun fetchOrderById(orderId: String): ApiResult<Order> {
        return when (val res = fetchOrderDetail(orderId)) {
            is ApiResult.Success -> ApiResult.Success(res.data.order)
            is ApiResult.Error -> res
        }
    }

    suspend fun createOrderPaystack(productId: String, quantity: Int): ApiResult<Pair<String, String>> {
        return try {
            val res = kartWebApi.createOrder(
                mapOf("productId" to productId, "quantity" to quantity, "isApp" to true)
            )
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val orderMap = body["order"] as? Map<*, *>
                val paymentMap = body["payment"] as? Map<*, *>
                val orderId = orderMap?.get("id")?.toString().orEmpty()
                val authUrl = paymentMap?.get("authorization_url")?.toString().orEmpty()
                if (authUrl.isNotBlank()) ApiResult.Success(orderId to authUrl)
                else ApiResult.Error(body["error"]?.toString() ?: "Payment URL missing from response.")
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Checkout failed."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Unable to initialize Paystack checkout."))
        }
    }

    suspend fun payOrderWithWallet(productId: String, quantity: Int): ApiResult<String> {
        return try {
            val res = kartWebApi.payOrderWithWallet(mapOf("productId" to productId, "quantity" to quantity))
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val orderMap = body["order"] as? Map<*, *>
                val orderId = orderMap?.get("id")?.toString() ?: body["orderId"]?.toString().orEmpty()
                ApiResult.Success(orderId)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Wallet payment failed. Check your available balance."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Wallet checkout error."))
        }
    }

    suspend fun markOrderShipped(orderId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.markOrderShipped(mapOf("orderId" to orderId))
    }

    suspend fun sendDeliveryOtp(orderId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.sendDeliveryOtp(mapOf("orderId" to orderId))
    }

    suspend fun confirmDelivery(orderId: String, code: String, mode: String): ApiResult<Unit> = runApiAction {
        val payload = when (mode) {
            "pin" -> mapOf("orderId" to orderId, "pin" to code, "method" to "pin")
            "qr" -> mapOf("orderId" to orderId, "qrToken" to code, "method" to "qr")
            else -> mapOf("orderId" to orderId, "otp" to code, "method" to "otp")
        }
        kartWebApi.confirmDelivery(payload)
    }

    suspend fun generateDeliveryQr(orderId: String): ApiResult<DeliveryQrData> {
        return try {
            val res = kartWebApi.generateDeliveryQr(mapOf("orderId" to orderId))
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val token = body["qrToken"]?.toString().orEmpty()
                val expiresAt = body["expiresAt"]?.toString().orEmpty()
                if (token.isNotBlank()) {
                    ApiResult.Success(DeliveryQrData(qrToken = token, expiresAt = expiresAt))
                } else {
                    ApiResult.Error("QR token missing from server response.")
                }
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Could not generate handover QR."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while generating handover QR."))
        }
    }

    suspend fun cancelOrder(orderId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.cancelOrder(mapOf("orderId" to orderId))
    }

    suspend fun requestRefund(orderId: String, reason: String): ApiResult<Unit> = runApiAction {
        kartWebApi.requestRefund(mapOf("orderId" to orderId, "reason" to reason))
    }

    suspend fun blockUser(targetUserId: String, reason: String = ""): ApiResult<Unit> = runApiAction {
        val body = buildMap {
            put("targetUserId", targetUserId)
            if (reason.isNotBlank()) put("reason", reason.trim().take(500))
        }
        kartWebApi.blockUser(body)
    }

    suspend fun unblockUser(targetUserId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.unblockUser(mapOf("targetUserId" to targetUserId))
    }

    suspend fun fetchBlockedUsers(): ApiResult<Pair<Set<String>, List<Profile>>> {
        return try {
            val res = kartWebApi.getBlockedUsers()
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val ids = (body["blockedUserIds"] as? List<*>)
                    .orEmpty()
                    .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                    .toSet()
                val rawUsers = (body["users"] as? List<*>).orEmpty()
                val profiles = rawUsers.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = map["id"]?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    Profile(
                        id = id,
                        displayName = map["display_name"]?.toString() ?: map["full_name"]?.toString(),
                        username = map["username"]?.toString(),
                        avatarUrl = map["avatar_url"]?.toString(),
                        campus = map["campus"]?.toString(),
                        bio = map["bio"]?.toString(),
                        isVerified = map["is_verified"] as? Boolean ?: false,
                        averageRating = (map["average_rating"] as? Number)?.toDouble() ?: 0.0,
                        reviewsCount = (map["total_reviews"] as? Number)?.toInt() ?: 0
                    )
                }
                val mergedIds = ids + profiles.map { it.id }
                ApiResult.Success(mergedIds to profiles)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Could not load blocked users."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Network error while loading blocked users."))
        }
    }

    suspend fun verifyPaystackReference(orderId: String, reference: String): ApiResult<Unit> {
        val cleanOrderId = orderId.trim()
        val cleanRef = reference.trim()
        if (cleanOrderId.isBlank() || cleanRef.isBlank()) {
            return ApiResult.Error("Order ID and reference are required.")
        }
        return runApiAction {
            kartWebApi.verifyPaystack(mapOf("orderId" to cleanOrderId, "reference" to cleanRef))
        }
    }

    suspend fun verifySubscriptionReference(subscriptionId: String, reference: String): ApiResult<Unit> {
        val cleanSubId = subscriptionId.trim()
        val cleanRef = reference.trim()
        if (cleanSubId.isBlank() || cleanRef.isBlank()) {
            return ApiResult.Error("Subscription ID and reference are required.")
        }
        return runApiAction {
            kartWebApi.verifySubscription(mapOf("subscriptionId" to cleanSubId, "reference" to cleanRef))
        }
    }

    suspend fun verifyPromotionReference(adId: String, reference: String): ApiResult<Unit> {
        val cleanAdId = adId.trim()
        val cleanRef = reference.trim()
        if (cleanAdId.isBlank() || cleanRef.isBlank()) {
            return ApiResult.Error("Ad ID and reference are required.")
        }
        return runApiAction {
            kartWebApi.verifyPromotion(mapOf("adId" to cleanAdId, "reference" to cleanRef))
        }
    }

    suspend fun submitReview(
        productId: String?,
        sellerId: String?,
        orderId: String?,
        rating: Int,
        comment: String
    ): ApiResult<Unit> = runApiAction {
        val payload = buildMap<String, Any> {
            put("rating", rating)
            put("comment", comment)
            if (!productId.isNullOrBlank()) put("productId", productId)
            if (!sellerId.isNullOrBlank()) put("sellerId", sellerId)
            if (!orderId.isNullOrBlank()) put("orderId", orderId)
        }
        kartWebApi.createReview(payload)
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String, details: String): ApiResult<Unit> =
        runApiAction {
            val body = buildMap<String, Any> {
                put("target_type", targetType)
                put("targetType", targetType)
                put("target_id", targetId)
                put("targetId", targetId)
                if (targetType == "product") {
                    put("productId", targetId)
                    put("product_id", targetId)
                } else {
                    put("reportedUserId", targetId)
                    put("reported_user_id", targetId)
                }
                put("reason", reason)
                put("description", details)
                put("details", details)
            }
            kartWebApi.createReport(body)
        }

    // --- Messaging ---
    suspend fun fetchConversations(userId: String): ApiResult<List<EnrichedConversation>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val inboxRpc = runCatching {
                supabaseApi.getUserInbox(mapOf("p_limit" to 40))
            }.getOrNull()
            if (inboxRpc?.isSuccessful == true && inboxRpc.body() != null) {
                val mapped = inboxRpc.body().orEmpty().map { item ->
                    EnrichedConversation(
                        id = item.id,
                        otherUser = item.otherUser ?: Profile(id = "", displayName = "Campus User"),
                        product = null,
                        lastMessage = item.lastMessage,
                        unreadCount = item.unreadCount,
                        updatedAt = item.updatedAt
                    )
                }
                return ApiResult.Success(mapped)
            }

            val convsRes = supabaseApi.getConversations(
                mapOf(
                    "select" to "*",
                    "participants" to "cs.{$userId}",
                    "order" to "updated_at.desc",
                    "limit" to "40"
                )
            )
            if (!convsRes.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(convsRes.code(), fallback = "Failed to load conversations."))
            }
            val convs = convsRes.body().orEmpty()
            if (convs.isEmpty()) return ApiResult.Success(emptyList())

            val convIds = convs.map { it.id }
            val otherIds = convs.mapNotNull { it.participants.firstOrNull { p -> p != userId } }.distinct()
            val productIds = convs.mapNotNull { it.productId }.distinct()

            val profilesMap = if (otherIds.isNotEmpty()) {
                runCatching {
                    otherIds.chunked(50).flatMap { chunk ->
                        supabaseApi.getProfiles(mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})"))
                            .body().orEmpty()
                    }.associateBy { it.id }
                }.getOrDefault(emptyMap())
            } else emptyMap()

            val productsMap = if (productIds.isNotEmpty()) {
                runCatching {
                    productIds.chunked(50).flatMap { chunk ->
                        supabaseApi.getProducts(mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})"))
                            .body().orEmpty()
                    }.associateBy { it.id }
                }.getOrDefault(emptyMap())
            } else emptyMap()

            val messages = runCatching {
                supabaseApi.getMessages(
                    mapOf(
                        "select" to "*",
                        "conversation_id" to "in.(${convIds.joinToString(",")})",
                        "order" to "created_at.desc",
                        "limit" to "200"
                    )
                ).body().orEmpty()
            }.getOrDefault(emptyList())

            val unreadMessages = runCatching {
                supabaseApi.getMessages(
                    mapOf(
                        "select" to "id,conversation_id,sender_id,is_read",
                        "conversation_id" to "in.(${convIds.joinToString(",")})",
                        "sender_id" to "neq.$userId",
                        "is_read" to "eq.false",
                        "limit" to "200"
                    )
                ).body().orEmpty()
            }.getOrNull()

            val lastByConv = mutableMapOf<String, MessageRow>()
            val unreadByConv = mutableMapOf<String, Int>()
            for (msg in messages) {
                if (!lastByConv.containsKey(msg.conversationId)) lastByConv[msg.conversationId] = msg
                if (unreadMessages == null && msg.senderId != userId && msg.isRead != true) {
                    unreadByConv[msg.conversationId] = (unreadByConv[msg.conversationId] ?: 0) + 1
                }
            }
            if (unreadMessages != null) {
                for (msg in unreadMessages) {
                    unreadByConv[msg.conversationId] = (unreadByConv[msg.conversationId] ?: 0) + 1
                }
            }

            // Backfill lastMessage for any conversation outside the 200-message window
            val missingLastConvIds = convIds.filter { it !in lastByConv }
            if (missingLastConvIds.isNotEmpty()) {
                coroutineScope {
                    missingLastConvIds.take(5).map { cid ->
                        async {
                            cid to runCatching {
                                supabaseApi.getMessages(
                                    mapOf(
                                        "select" to "*",
                                        "conversation_id" to "eq.$cid",
                                        "order" to "created_at.desc",
                                        "limit" to "1"
                                    )
                                ).body()?.firstOrNull()
                            }.getOrNull()
                        }
                    }.awaitAll().forEach { (cid, msg) ->
                        if (msg != null) lastByConv[cid] = msg
                    }
                }
            }

            val enriched = convs.map { c ->
                val otherId = c.participants.firstOrNull { it != userId }.orEmpty()
                EnrichedConversation(
                    id = c.id,
                    otherUser = profilesMap[otherId] ?: Profile(id = otherId, displayName = "Campus User"),
                    product = c.productId?.let { productsMap[it] },
                    lastMessage = lastByConv[c.id],
                    unreadCount = unreadByConv[c.id] ?: 0,
                    updatedAt = lastByConv[c.id]?.createdAt ?: c.updatedAt
                )
            }
            ApiResult.Success(enriched)
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load conversations."))
        }
    }

    suspend fun fetchMessagesForConversation(
        conversationId: String,
        afterCreatedAt: String? = null
    ): ApiResult<List<MessageRow>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val isInitialLoad = afterCreatedAt.isNullOrBlank()
            val query = buildMap {
                put("select", "*")
                put("conversation_id", "eq.$conversationId")
                if (!isInitialLoad) {
                    put("created_at", "gt.$afterCreatedAt")
                    put("order", "created_at.asc")
                } else {
                    // Fetch the newest 200 messages on initial load so long threads don't open at message #1
                    put("order", "created_at.desc")
                }
                put("limit", "200")
            }
            val res = supabaseApi.getMessages(query)
            if (res.isSuccessful) {
                val rawRows = res.body().orEmpty()
                val rows = if (isInitialLoad) rawRows.reversed() else rawRows
                if (isInitialLoad || rows.isNotEmpty()) {
                    runCatching { kartWebApi.markMessagesRead(mapOf("conversationId" to conversationId)) }
                }
                ApiResult.Success(rows)
            } else ApiResult.Error("Could not load messages.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error loading messages."))
        }
    }

    suspend fun getOrCreateConversation(currentUserId: String, sellerId: String, productId: String?): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        if (currentUserId == sellerId) return ApiResult.Error("You cannot start a conversation with yourself.")
        return try {
            suspend fun findExistingConversations(): List<com.example.domain.model.ConversationRow> {
                val direct = runCatching {
                    supabaseApi.getConversations(
                        mapOf(
                            "select" to "*",
                            "participants" to "cs.{$currentUserId,$sellerId}",
                            "order" to "updated_at.desc"
                        )
                    ).body().orEmpty()
                }.getOrDefault(emptyList())
                if (direct.isNotEmpty()) return direct

                return runCatching {
                    supabaseApi.getConversations(
                        mapOf(
                            "select" to "*",
                            "participants" to "cs.{$currentUserId}",
                            "order" to "updated_at.desc",
                            "limit" to "100"
                        )
                    ).body().orEmpty().filter { sellerId in it.participants }
                }.getOrDefault(emptyList())
            }

            val existingList = findExistingConversations()

            val exactMatch = if (!productId.isNullOrBlank()) {
                existingList.firstOrNull { it.productId == productId }
            } else {
                existingList.firstOrNull()
            }
            if (exactMatch != null) return ApiResult.Success(exactMatch.id)

            var lastError: String? = null
            val candidateBodies = buildList<Map<String, Any?>> {
                if (!productId.isNullOrBlank()) {
                    add(mapOf("participants" to listOf(currentUserId, sellerId), "product_id" to productId))
                }
                add(mapOf("participants" to listOf(currentUserId, sellerId)))
            }

            for (body in candidateBodies) {
                val created = supabaseApi.createConversation(body = body)
                if (created.isSuccessful) {
                    val createdId = created.body()?.firstOrNull()?.id
                    if (!createdId.isNullOrBlank()) return ApiResult.Success(createdId)
                    // Some RLS setups return 201 with an empty representation; re-fetch the new thread
                    val refetched = findExistingConversations()
                    val matchId = refetched.firstOrNull { !productId.isNullOrBlank() && it.productId == productId }?.id
                        ?: refetched.firstOrNull()?.id
                    if (!matchId.isNullOrBlank()) return ApiResult.Success(matchId)
                } else {
                    lastError = KartErrorFormatter.fromHttpStatus(created.code(), extractHttpErrorMessage(created), "Could not start conversation.")
                    // If the database enforces one conversation per buyer/seller pair, reuse the existing thread
                    if (existingList.isNotEmpty()) {
                        return ApiResult.Success(existingList.first().id)
                    }
                }
            }

            // Final fallback: re-check existing conversations in case of a race or unique conflict
            val fallbackId = findExistingConversations().firstOrNull()?.id
            if (!fallbackId.isNullOrBlank()) {
                ApiResult.Success(fallbackId)
            } else {
                ApiResult.Error(lastError ?: "Could not start conversation.")
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error starting chat."))
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        content: String,
        allowMediaMarker: Boolean = false
    ): ApiResult<MessageRow> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ApiResult.Error("Message cannot be empty.")
        if (trimmed.length > MAX_MESSAGE_LENGTH) {
            return ApiResult.Error("Message is too long (max $MAX_MESSAGE_LENGTH characters).")
        }
        val safeContent = if (!allowMediaMarker &&
            (trimmed.startsWith("[IMAGE:", ignoreCase = true) ||
                trimmed.startsWith("[VOICE:", ignoreCase = true) ||
                trimmed.startsWith("[PRODUCT:", ignoreCase = true) ||
                trimmed.startsWith("[REPLY:", ignoreCase = true))
        ) {
            // Prevent spoofing in-band media markers from plain text input
            "\\$trimmed"
        } else {
            trimmed
        }
        return try {
            val res = supabaseApi.sendMessage(
                body = mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to safeContent
                )
            )
            val sent = res.body()?.firstOrNull()
            if (res.isSuccessful && sent != null) {
                runCatching {
                    supabaseApi.updateConversation(
                        idFilter = "eq.$conversationId",
                        body = mapOf("updated_at" to nowIsoUtc())
                    )
                }
                ApiResult.Success(sent)
            } else ApiResult.Error("Failed to send message.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Message failed to send."))
        }
    }

    suspend fun fetchUnreadMessagesCount(): Int {
        return runCatching {
            val res = kartWebApi.getUnreadMessagesCount()
            val body = res.body()
            val num = (body?.get("unreadCount") ?: body?.get("count")) as? Number
            num?.toInt() ?: 0
        }.getOrDefault(0)
    }

    // --- Wallet & Subscriptions ---
    suspend fun fetchWallet(userId: String): ApiResult<Wallet> {
        val cursorApi = kartCursorApi
        if (cursorApi != null) {
            val cursorRes = runCatching { cursorApi.getWalletTransactions(limit = 50) }.getOrNull()
            if (cursorRes != null && cursorRes.error.isNullOrBlank() && cursorRes.wallet != null) {
                val txns = cursorRes.transactions.map { tx -> tx.copy(type = tx.resolvedType) }
                return ApiResult.Success(cursorRes.wallet.copy(userId = cursorRes.wallet.userId ?: userId, transactions = txns))
            }
        }
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getWallets(
                mapOf(
                    "select" to "*",
                    "user_id" to "eq.$userId",
                    "limit" to "1"
                )
            )
            if (res.isSuccessful) {
                val wallet = res.body()?.firstOrNull() ?: return ApiResult.Success(Wallet(userId = userId))
                val walletId = wallet.id
                val txns = if (!walletId.isNullOrBlank()) {
                    runCatching {
                        supabaseApi.getWalletTransactions(
                            mapOf(
                                "select" to "*,order:orders(id,product:products(title))",
                                "wallet_id" to "eq.$walletId",
                                "order" to "created_at.desc",
                                "limit" to "100"
                            )
                        ).body().orEmpty().map { tx -> tx.copy(type = tx.resolvedType) }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                ApiResult.Success(wallet.copy(transactions = txns))
            } else ApiResult.Error("Failed to load wallet.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error loading wallet."))
        }
    }

    suspend fun fetchWalletTransactionsPage(
        cursor: String? = null,
        limit: Int = 25,
        type: String? = null
    ): ApiResult<WalletTransactionsCursorResponse> {
        val api = kartCursorApi ?: return ApiResult.Error("Cursor API is unavailable.")
        return try {
            val res = api.getWalletTransactions(cursor = cursor, limit = limit, type = type)
            ApiResult.Success(res.copy(transactions = res.transactions.map { it.copy(type = it.resolvedType) }))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load wallet transactions page."))
        }
    }

    suspend fun initiateWalletDeposit(amount: Double): ApiResult<String> {
        return try {
            val res = kartWebApi.depositWallet(mapOf("amount" to amount, "isApp" to true))
            val url = res.body()?.get("authorization_url")?.toString()
                ?: (res.body()?.get("data") as? Map<*, *>)?.get("authorization_url")?.toString()
            if (res.isSuccessful && !url.isNullOrBlank()) ApiResult.Success(url)
            else ApiResult.Error(res.body()?.get("error")?.toString() ?: "Could not initialize deposit.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Deposit initiation failed."))
        }
    }

    suspend fun verifyWalletDeposit(reference: String): ApiResult<Unit> {
        val cleanRef = reference.trim()
        if (cleanRef.isBlank()) {
            return ApiResult.Error("Payment reference is required.")
        }
        return runApiAction {
            kartWebApi.verifyWalletPayment(mapOf("reference" to cleanRef))
        }
    }

    suspend fun requestWithdrawal(amount: Double, method: String, details: String): ApiResult<Unit> = runApiAction {
        kartWebApi.withdrawWallet(
            mapOf("amount" to amount, "payoutMethod" to method, "accountDetails" to details)
        )
    }

    suspend fun fetchSubscriptions(userId: String): ApiResult<Pair<List<SubscriptionPlan>, UserSubscription?>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val plansRes = supabaseApi.getSubscriptionPlans(mapOf("select" to "*", "order" to "duration_months.asc"))
            if (!plansRes.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(plansRes.code(), fallback = "Failed to load subscription plans."))
            }
            val plans = plansRes.body().orEmpty()
            val subsRes = supabaseApi.getSubscriptions(
                mapOf("select" to "*,plan:subscription_plans(*)", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "5")
            )
            if (!subsRes.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(subsRes.code(), fallback = "Failed to load subscriptions."))
            }
            val subs = subsRes.body().orEmpty()
            val activeSub = subs.firstOrNull { KartFormatters.isActiveSubscription(it) }
            ApiResult.Success(plans to activeSub)
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load subscription plans."))
        }
    }

    suspend fun subscribeToPlan(planId: String): ApiResult<String> {
        return try {
            val res = kartWebApi.createSubscription(mapOf("planId" to planId, "isApp" to true))
            if (res.isSuccessful) {
                val body = res.body()
                val errMsg = body?.get("error")?.toString()?.takeIf { it.isNotBlank() }
                if (errMsg != null) return ApiResult.Error(errMsg)
                val nested = body?.get("data") as? Map<*, *>
                val url = body?.get("authorization_url")?.toString()
                    ?: nested?.get("authorization_url")?.toString()
                    ?: ""
                // Free plans activate immediately without returning a Paystack authorization_url
                ApiResult.Success(url)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Subscription checkout failed."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Subscription error."))
        }
    }

    suspend fun cancelSubscription(subscriptionId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.cancelSubscription(mapOf("subscriptionId" to subscriptionId))
    }

    // --- Seller Studio ---
    suspend fun fetchSellerHub(userId: String): ApiResult<SellerHubBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val listingsRes = supabaseApi.getProducts(
                mapOf("select" to "*", "seller_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "100")
            )
            if (!listingsRes.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(listingsRes.code(), fallback = "Failed to load seller listings."))
            }
            val listings = listingsRes.body().orEmpty()

            val ordersRes = supabaseApi.getOrders(
                mapOf(
                    "select" to "*,product:products(id,title,images,image_url)",
                    "seller_id" to "eq.$userId",
                    "order" to "created_at.desc",
                    "limit" to "100"
                )
            )
            if (!ordersRes.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(ordersRes.code(), fallback = "Failed to load seller orders."))
            }
            val orders = ordersRes.body().orEmpty()

            val promos = runCatching {
                supabaseApi.getAdvertisements(
                    mapOf("select" to "*,product:products(*)", "seller_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "100")
                ).body().orEmpty()
            }.getOrDefault(emptyList())

            val plans = runCatching {
                supabaseApi.getSubscriptionPlans(mapOf("select" to "price")).body().orEmpty()
            }.getOrDefault(emptyList())

            val sub = runCatching {
                supabaseApi.getSubscriptions(
                    mapOf("select" to "*,plan:subscription_plans(*)", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "5")
                ).body().orEmpty().firstOrNull { KartFormatters.isActiveSubscription(it) }
            }.getOrNull()

            val completedOrders = orders.filter {
                it.status.equals("Completed", ignoreCase = true) ||
                    it.status.equals("Delivered", ignoreCase = true)
            }
            val totalEarnings = completedOrders.sumOf { it.sellerPayoutAmount ?: it.totalAmount ?: 0.0 }

            // 7-day rolling chart in UTC matching backend timestamps
            val utcTz = java.util.TimeZone.getTimeZone("UTC")
            val dayFmt = SimpleDateFormat("EEE", Locale.US).apply { timeZone = utcTz }
            val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utcTz }
            val nowMs = System.currentTimeMillis()
            val labels = mutableListOf<String>()
            val values = mutableListOf<Double>()
            for (offset in 6 downTo 0) {
                val dayDate = Date(nowMs - offset * 86_400_000L)
                val key = dateKeyFmt.format(dayDate)
                labels.add(dayFmt.format(dayDate))
                val daySum = completedOrders.filter {
                    val ts = KartFormatters.parseIsoTimestamp(it.updatedAt ?: it.createdAt)
                    ts > 0 && dateKeyFmt.format(Date(ts)) == key
                }.sumOf { it.sellerPayoutAmount ?: it.totalAmount ?: 0.0 }
                values.add(daySum)
            }

            ApiResult.Success(
                SellerHubBundle(
                    listings = listings,
                    orders = orders,
                    promotions = promos,
                    activeSubscription = sub,
                    isSubscriptionFree = plans.isNotEmpty() && plans.all { it.price <= 0.0 },
                    totalEarnings = totalEarnings,
                    itemsSold = completedOrders.size,
                    activeListings = listings.count { it.isActiveListing },
                    chartValues = values,
                    chartLabels = labels
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load Seller Hub."))
        }
    }

    private fun readBoundedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun isValidImageMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isWebp = bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        return isJpeg || isPng || isWebp
    }

    private suspend fun tryPrivateUploadAndSign(
        bucket: String,
        storagePath: String,
        bytes: ByteArray,
        mime: String
    ): String? {
        if (!hasSupabaseKey()) return null
        val uploadRes = runCatching {
            supabaseApi.uploadToStorage(
                bucket = bucket,
                path = storagePath,
                body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
        }.getOrNull() ?: return null
        if (!uploadRes.isSuccessful) return null
        val signRes = runCatching {
            supabaseApi.createSignedStorageUrl(bucket = bucket, path = storagePath)
        }.getOrNull()
        val signedPath = signRes?.body()?.get("signedURL")?.toString()
            ?: signRes?.body()?.get("signedUrl")?.toString()
        if (signRes?.isSuccessful == true && !signedPath.isNullOrBlank()) {
            val base = BuildConfig.SUPABASE_URL.trimEnd('/')
            return if (signedPath.startsWith("http")) signedPath
            else if (signedPath.startsWith("/storage/")) "$base$signedPath"
            else "$base/storage/v1$signedPath"
        }
        return null
    }

    private suspend fun tryDirectR2Upload(
        bytes: ByteArray,
        bucket: String,
        mime: String,
        conversationId: String?
    ): String? {
        val uploader = directR2Uploader ?: return null
        val presignBucket = if (bucket == "chat-media") "chat-attachments" else bucket
        if (presignBucket == "chat-attachments" && conversationId.isNullOrBlank()) {
            return null
        }
        return runCatching {
            uploader.uploadBytes(
                bytes = bytes,
                bucket = presignBucket,
                mimeType = mime,
                conversationId = conversationId
            )
        }.getOrNull()?.publicUrl?.takeIf { url ->
            url.isNotBlank() && KartFormatters.isTrustedMediaUrl(url)
        }
    }

    suspend fun uploadImageUri(
        contentResolver: ContentResolver,
        uri: Uri,
        bucket: String = "products",
        isPrivate: Boolean = false,
        conversationId: String? = null
    ): ApiResult<String> {
        return try {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { readBoundedBytes(it, MAX_UPLOAD_BYTES) }
            } ?: return ApiResult.Error("Image is unreadable or exceeds the 5 MB size limit.")
            if (bytes.isEmpty() || !isValidImageMagic(bytes)) {
                return ApiResult.Error("Please select a valid JPEG, PNG, or WebP image.")
            }
            val rawMime = contentResolver.getType(uri)?.takeIf {
                it in setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
            } ?: "image/jpeg"
            val compressed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (bucket == "verifications") compressImageBytes(bytes, maxDimensionPx = 1800, jpegQuality = 88)
                else compressImageBytes(bytes, maxDimensionPx = 1440, jpegQuality = 82)
            }
            val uploadBytes = compressed ?: bytes
            val mime = if (compressed != null) "image/jpeg" else rawMime
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }

            // Primary path: direct-to-R2 presigned PUT (bypasses Vercel serverless payload bottleneck)
            tryDirectR2Upload(uploadBytes, bucket, mime, conversationId)?.let { publicUrl ->
                return ApiResult.Success(publicUrl)
            }

            val uid = sessionStore.currentSession().userId
            val filename = "${UUID.randomUUID()}.$ext"
            val scopedPath = if (uid.isNotBlank()) "$uid/$filename" else filename

            // For private KYC / chat uploads, try private Supabase bucket + signed URL next
            if (isPrivate) {
                tryPrivateUploadAndSign(bucket, scopedPath, uploadBytes, mime)?.let { signedUrl ->
                    return ApiResult.Success(signedUrl)
                }
            }

            // Try Kart server-controlled /api/upload endpoint
            val part = MultipartBody.Part.createFormData(
                "file",
                filename,
                uploadBytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            val webUpload = runCatching { kartWebApi.uploadImage(part) }.getOrNull()
            val webUrl = webUpload?.body()?.get("url")?.toString()
            if (webUpload?.isSuccessful == true && !webUrl.isNullOrBlank()) {
                return ApiResult.Success(webUrl)
            }

            // Never fall back to public storage for private uploads (KYC documents, private chat media)
            if (isPrivate) {
                val msg = if (bucket == "verifications") {
                    "Secure verification upload is currently unavailable. Please try again."
                } else {
                    "Secure private upload failed. Please try again."
                }
                return ApiResult.Error(msg)
            }

            // Fallback to Supabase Storage direct upload for public listing/avatar assets
            if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
            val storagePath = if (bucket == "profiles") {
                if (uid.isNotBlank()) "$uid/avatar_${System.currentTimeMillis()}.$ext" else filename
            } else scopedPath
            val storageRes = supabaseApi.uploadToStorage(
                bucket = bucket,
                path = storagePath,
                body = uploadBytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            if (storageRes.isSuccessful) {
                val publicUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$bucket/$storagePath"
                ApiResult.Success(publicUrl)
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(storageRes.code(), fallback = "Image upload failed."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Upload failed."))
        }
    }

    suspend fun uploadAudioFile(
        file: java.io.File,
        bucket: String = "chat-media",
        isPrivate: Boolean = true,
        conversationId: String? = null
    ): ApiResult<String> {
        return try {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!file.exists() || file.length() <= 0L) return@withContext null
                if (file.length() > MAX_UPLOAD_BYTES) return@withContext ByteArray(0)
                file.readBytes()
            }
            if (bytes == null) return ApiResult.Error("Recording was empty.")
            if (bytes.isEmpty()) return ApiResult.Error("Voice recording exceeds the 5 MB limit.")
            val mime = "audio/mp4"

            tryDirectR2Upload(bytes, bucket, mime, conversationId)?.let { publicUrl ->
                return ApiResult.Success(publicUrl)
            }

            val uid = sessionStore.currentSession().userId
            val filename = if (uid.isNotBlank()) "voice/$uid/${UUID.randomUUID()}.m4a" else "voice/${UUID.randomUUID()}.m4a"

            if (isPrivate) {
                tryPrivateUploadAndSign(bucket, filename, bytes, mime)?.let { signedUrl ->
                    return ApiResult.Success(signedUrl)
                }
            }

            val part = MultipartBody.Part.createFormData(
                "file",
                "voice_${UUID.randomUUID()}.m4a",
                bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            val webUpload = runCatching { kartWebApi.uploadImage(part) }.getOrNull()
            val webUrl = webUpload?.body()?.get("url")?.toString()
            if (webUpload?.isSuccessful == true && !webUrl.isNullOrBlank()) {
                return ApiResult.Success(webUrl)
            }

            if (isPrivate) {
                return ApiResult.Error("Could not securely upload voice message. Please try again.")
            }

            if (hasSupabaseKey()) {
                val fallbackBucket = if (bucket == "chat-media") "products" else bucket
                val res = supabaseApi.uploadToStorage(
                    bucket = fallbackBucket,
                    path = filename,
                    body = bytes.toRequestBody(mime.toMediaTypeOrNull())
                )
                if (res.isSuccessful) {
                    val publicUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$fallbackBucket/$filename"
                    return ApiResult.Success(publicUrl)
                }
            }
            ApiResult.Error("Could not upload voice message.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Voice upload failed."))
        }
    }

    suspend fun createListing(
        sellerId: String,
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: String,
        campus: String,
        stock: Int,
        imageUrls: List<String>
    ): ApiResult<Product> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val profile = fetchMyProfile(sellerId)
        if (profile?.isVerified != true) {
            return ApiResult.Error(
                if (profile?.verificationStatus.equals("Pending", ignoreCase = true)) {
                    "Your identity verification is under review. You can post items once your account is verified."
                } else {
                    "Please verify your identity before selling or posting items on Kart."
                }
            )
        }
        val trimmedTitle = title.trim()
        if (trimmedTitle.length < 3) return ApiResult.Error("Listing title must be at least 3 characters.")
        if (price <= 0.0 || price > 1_000_000.0) return ApiResult.Error("Please enter a valid price between ₵0.01 and ₵1,000,000.")
        if (imageUrls.isEmpty()) return ApiResult.Error("Please add at least one photo of your item.")
        if (stock < 1) return ApiResult.Error("Stock quantity must be at least 1.")

        // Enforce seller listing quota when paid subscriptions are active
        val plans = runCatching {
            supabaseApi.getSubscriptionPlans(mapOf("select" to "price")).body().orEmpty()
        }.getOrDefault(emptyList())
        val isPlatformFree = plans.isEmpty() || plans.all { it.price <= 0.0 }
        if (!isPlatformFree) {
            val activeSub = runCatching {
                supabaseApi.getSubscriptions(
                    mapOf("select" to "*", "user_id" to "eq.$sellerId", "order" to "created_at.desc", "limit" to "5")
                ).body().orEmpty().firstOrNull { KartFormatters.isActiveSubscription(it) }
            }.getOrNull()
            if (activeSub == null) {
                val used = profile.freeListingsUsed ?: 0
                if (used >= FREE_LISTING_QUOTA) {
                    return ApiResult.Error("Free listing limit reached ($FREE_LISTING_QUOTA). Activate a Seller Subscription to publish more items.")
                }
            }
        }

        return try {
            val payload = mapOf(
                "seller_id" to sellerId,
                "title" to trimmedTitle,
                "description" to description.trim(),
                "price" to price,
                "category" to category,
                "condition" to condition,
                "campus" to campus,
                "stock_quantity" to stock.coerceIn(1, 999),
                "image_url" to imageUrls.firstOrNull(),
                "images" to imageUrls,
                "status" to "Active",
                "currency" to "GHS"
            )
            val res = supabaseApi.createProduct(body = payload)
            val created = res.body()?.firstOrNull()
            if (res.isSuccessful && created != null) ApiResult.Success(created)
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Could not publish listing."))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to create listing."))
        }
    }

    private suspend fun hasActiveOrdersForProduct(productId: String): Boolean {
        val orders = runCatching {
            supabaseApi.getOrders(
                mapOf("select" to "id,status", "product_id" to "eq.$productId", "limit" to "20")
            ).body().orEmpty()
        }.getOrDefault(emptyList())
        return orders.any { it.status.orEmpty().lowercase(Locale.US) in ACTIVE_ORDER_STATUSES }
    }

    private suspend fun hasActivePromotionForProduct(productId: String): Boolean {
        val now = nowIsoUtc()
        val promos = runCatching {
            supabaseApi.getAdvertisements(
                mapOf(
                    "select" to "id,status,end_date",
                    "product_id" to "eq.$productId",
                    "status" to "eq.Active",
                    "end_date" to "gte.$now",
                    "limit" to "5"
                )
            ).body().orEmpty()
        }.getOrDefault(emptyList())
        return promos.isNotEmpty()
    }

    suspend fun updateListing(
        sellerId: String,
        productId: String,
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: String,
        campus: String,
        stock: Int? = null,
        imageUrls: List<String>,
        originalPrice: Double? = null
    ): ApiResult<Product> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val trimmedTitle = title.trim()
        if (trimmedTitle.length < 3) return ApiResult.Error("Listing title must be at least 3 characters.")
        if (price <= 0.0 || price > 1_000_000.0) return ApiResult.Error("Please enter a valid price.")
        if (imageUrls.isEmpty()) return ApiResult.Error("Please keep at least one photo.")
        if (originalPrice != null && originalPrice != price && hasActiveOrdersForProduct(productId)) {
            return ApiResult.Error("Price cannot be changed while this listing has active escrow orders.")
        }
        return try {
            val payload = buildMap<String, Any?> {
                put("title", trimmedTitle)
                put("description", description.trim())
                put("price", price)
                put("category", category)
                put("condition", condition)
                put("campus", campus)
                if (stock != null) put("stock_quantity", stock.coerceIn(0, 999))
                put("image_url", imageUrls.firstOrNull())
                put("images", imageUrls)
            }
            val res = supabaseApi.updateProduct(
                idFilter = "eq.$productId",
                sellerIdFilter = "eq.$sellerId",
                body = payload
            )
            val updated = res.body()?.firstOrNull()
            if (res.isSuccessful && updated != null) ApiResult.Success(updated)
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Failed to update listing."))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error updating listing."))
        }
    }

    suspend fun deleteListing(sellerId: String, productId: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        if (hasActiveOrdersForProduct(productId)) {
            return ApiResult.Error("Cannot delete a listing with active escrow orders.")
        }
        if (hasActivePromotionForProduct(productId)) {
            return ApiResult.Error("Cannot delete a listing with an active paid promotion.")
        }
        return try {
            val res = supabaseApi.deleteProduct(
                idFilter = "eq.$productId",
                sellerIdFilter = "eq.$sellerId"
            )
            if (res.isSuccessful) ApiResult.Success(Unit) else ApiResult.Error("Could not delete listing.")
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Delete error."))
        }
    }

    suspend fun createPromotion(productId: String, tierId: String, amount: Double): ApiResult<String> {
        val currentSession = sessionStore.currentSession()
        if (!currentSession.isLoggedIn) return ApiResult.Error("Please sign in to promote a listing.")
        if (hasSupabaseKey()) {
            val listingRes = fetchProductById(productId)
            val listing = (listingRes as? ApiResult.Success)?.data
                ?: return ApiResult.Error("Listing not found.")
            if (listing.sellerId != currentSession.userId) {
                return ApiResult.Error("You can only promote your own listings.")
            }
            if (!listing.isActiveListing || listing.isOutOfStock) {
                return ApiResult.Error("Only active, in-stock listings can be promoted.")
            }
            if (amount <= 0.0 && hasActivePromotionForProduct(productId)) {
                return ApiResult.Error("This listing already has an active free promotion.")
            }
        }
        return try {
            val normalizedTier = when (tierId.trim().lowercase(Locale.US)) {
                "daily", "weekly", "featured" -> tierId.trim().lowercase(Locale.US)
                else -> "daily"
            }
            val res = kartWebApi.createPromotion(
                mapOf(
                    "productId" to productId,
                    "tierId" to normalizedTier,
                    "amount" to amount.coerceAtLeast(0.0),
                    "isApp" to true
                )
            )
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.get("redirect_to_success") == true) {
                    return ApiResult.Success("")
                }
                val nested = body?.get("data") as? Map<*, *>
                val url = body?.get("authorization_url")?.toString()
                    ?: nested?.get("authorization_url")?.toString()
                    ?: ""
                if (url.isNotBlank()) ApiResult.Success(url)
                else ApiResult.Error("Promotion payment URL was not returned.")
            } else {
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Could not create promotion."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Promotion error."))
        }
    }

    // --- Profile, Settings, Security & KYC Verification ---
    suspend fun updateProfile(userId: String, fields: Map<String, Any?>): ApiResult<Profile> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val sanitized = fields.filterKeys { it in ALLOWED_PROFILE_FIELDS }
        if (sanitized.isEmpty()) return ApiResult.Error("No valid profile fields to update.")

        // Route through server-validated KartWebApi first to enforce backend allowlists & hooks
        val webRes = runCatching { kartWebApi.updateProfileSettings(sanitized) }.getOrNull()
        if (webRes?.isSuccessful == true) {
            val refreshed = fetchMyProfile(userId)
            if (refreshed != null) return ApiResult.Success(refreshed)
        } else if (webRes != null && webRes.code() in 400..499 && webRes.code() != 404 && webRes.code() != 405) {
            val errBody = runCatching { webRes.errorBody()?.string() }.getOrNull()
            val parsed = errBody?.let {
                runCatching { org.json.JSONObject(it).optString("error").ifBlank { null } }.getOrNull()
            }
            return ApiResult.Error(KartErrorFormatter.fromHttpStatus(webRes.code(), parsed, "Profile update failed."))
        }

        // Fallback to PostgREST strictly with allowlisted non-privileged fields
        return try {
            val res = supabaseApi.updateProfile(idFilter = "eq.$userId", body = sanitized)
            val updated = res.body()?.firstOrNull()
            if (res.isSuccessful && updated != null) ApiResult.Success(updated)
            else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Profile update failed."))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Error updating profile."))
        }
    }

    suspend fun syncNotificationPreferences(pushEnabled: Boolean, emailEnabled: Boolean) {
        if (!sessionStore.currentSession().isLoggedIn) return
        runCatching {
            kartWebApi.updateProfileSettings(
                mapOf(
                    "push_notifications_enabled" to pushEnabled,
                    "email_notifications_enabled" to emailEnabled
                )
            )
        }
    }

    suspend fun updatePayoutSettings(momoNetwork: String, momoNumber: String, bankName: String, bankAccount: String): ApiResult<Unit> =
        runApiAction {
            kartWebApi.updatePayoutSettings(
                mapOf(
                    "momo_network" to momoNetwork,
                    "momo_number" to momoNumber.trim(),
                    "bank_name" to bankName.trim(),
                    "bank_account_number" to bankAccount.trim()
                )
            )
        }

    suspend fun updatePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = runApiAction {
        kartWebApi.updatePassword(mapOf("currentPassword" to currentPassword, "newPassword" to newPassword))
    }

    suspend fun fetchDeliveryPinStatus(): ApiResult<Boolean> {
        return try {
            val res = kartWebApi.getDeliveryPinStatus()
            if (res.isSuccessful) {
                val body = res.body().orEmpty()
                val hasPin = (body["hasPin"] as? Boolean)
                    ?: (body["has_pin"] as? Boolean)
                    ?: (body["configured"] as? Boolean)
                    ?: false
                ApiResult.Success(hasPin)
            } else {
                ApiResult.Error("Unable to check Delivery PIN status.")
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Unable to check Delivery PIN status."))
        }
    }

    suspend fun saveDeliveryPin(
        pin: String,
        currentPin: String? = null,
        currentPassword: String? = null
    ): ApiResult<Unit> = runApiAction {
        val body = buildMap<String, String> {
            put("pin", pin)
            put("newPin", pin)
            if (!currentPin.isNullOrBlank()) put("currentPin", currentPin)
            if (!currentPassword.isNullOrBlank()) put("currentPassword", currentPassword)
        }
        kartWebApi.saveDeliveryPin(body)
    }

    suspend fun sendEmailVerificationOtp(email: String): ApiResult<Unit> = runApiAction {
        kartWebApi.sendEmailVerificationOtp(mapOf("email" to email))
    }

    suspend fun verifyEmailOtp(email: String, otp: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifyEmailOtp(mapOf("email" to email, "otp" to otp))
    }

    suspend fun sendPhoneOtp(phone: String): ApiResult<Unit> = runApiAction {
        kartWebApi.sendPhoneOtp(mapOf("phone" to phone))
    }

    suspend fun verifyPhoneOtp(phone: String, otp: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifyPhoneOtp(mapOf("phone" to phone, "otp" to otp))
    }

    suspend fun submitKycVerification(
        idImageUrl: String,
        selfieImageUrl: String,
        studentIdNumber: String,
        fullName: String = "",
        campus: String = ""
    ): ApiResult<Unit> {
        val cleanCampus = campus.trim()
        if (cleanCampus.isBlank()) {
            return ApiResult.Error("Please enter your campus before submitting verification.")
        }
        val webRes = runApiAction {
            kartWebApi.submitVerificationRequest(
                mapOf(
                    "id_image_url" to idImageUrl,
                    "idImageUrl" to idImageUrl,
                    "idPhotoUrl" to idImageUrl,
                    "id_photo_url" to idImageUrl,
                    "idCardUrl" to idImageUrl,
                    "id_card_url" to idImageUrl,
                    "idFrontUrl" to idImageUrl,
                    "id_front_url" to idImageUrl,
                    "documentUrl" to idImageUrl,
                    "document_url" to idImageUrl,
                    "studentIdUrl" to idImageUrl,
                    "student_id_url" to idImageUrl,
                    "student_id_image" to idImageUrl,
                    "studentIdImage" to idImageUrl,
                    "idUrl" to idImageUrl,
                    "selfie_image_url" to selfieImageUrl,
                    "selfieImageUrl" to selfieImageUrl,
                    "selfiePhotoUrl" to selfieImageUrl,
                    "selfie_photo_url" to selfieImageUrl,
                    "selfieUrl" to selfieImageUrl,
                    "selfie_url" to selfieImageUrl,
                    "faceImageUrl" to selfieImageUrl,
                    "face_image_url" to selfieImageUrl,
                    "face_image" to selfieImageUrl,
                    "faceImage" to selfieImageUrl,
                    "faceUrl" to selfieImageUrl,
                    "student_id_number" to studentIdNumber,
                    "studentIdNumber" to studentIdNumber,
                    "studentId" to studentIdNumber,
                    "student_id" to studentIdNumber,
                    "idNumber" to studentIdNumber,
                    "id_number" to studentIdNumber,
                    "fullName" to fullName.ifBlank { "Kart User" },
                    "full_name" to fullName.ifBlank { "Kart User" },
                    "campus" to cleanCampus
                )
            )
        }
        return webRes
    }

    suspend fun requestAccountDeletion(reason: String): ApiResult<Unit> = runApiAction {
        kartWebApi.requestAccountDeletion(mapOf("reason" to reason))
    }

    suspend fun fetchCampuses(forceRefresh: Boolean = false): List<String> {
        if (!hasSupabaseKey()) return KartGhanaCampuses
        val now = System.currentTimeMillis()
        val cached = cachedCampuses
        if (!forceRefresh && !cached.isNullOrEmpty() && (now - campusesFetchedAtMs) < 600_000L) {
            return cached
        }
        return runCatching {
            val rows = supabaseApi.getCampuses(mapOf("select" to "name,abbreviation", "order" to "name.asc"))
                .body().orEmpty()
            if (rows.isNotEmpty()) {
                KartFormatters.registerCampusAbbreviations(rows)
            }
            val resolved = rows.map { it.name }.ifEmpty { KartGhanaCampuses }
            cachedCampuses = resolved
            campusesFetchedAtMs = System.currentTimeMillis()
            resolved
        }.getOrDefault(cached ?: KartGhanaCampuses)
    }

    // --- Push Notifications (FCM Device Tokens) ---
    suspend fun registerFcmToken(userId: String, token: String) {
        if (!hasSupabaseKey() || userId.isBlank() || token.isBlank()) return
        lastRegisteredFcmToken = token
        runCatching {
            val rpcRes = supabaseApi.upsertDeviceTokenRpc(
                body = mapOf(
                    "p_fcm_token" to token,
                    "p_platform" to "android"
                )
            )
            if (!rpcRes.isSuccessful) {
                supabaseApi.upsertDeviceToken(
                    body = mapOf(
                        "user_id" to userId,
                        "fcm_token" to token,
                        "platform" to "android"
                    )
                )
            }
        }
    }

    @Volatile
    private var cachedAdminBundle: AdminDashboardBundle? = null
    @Volatile
    private var cachedAdminFetchedAtMs: Long = 0L

    fun getCachedAdminBundle(): AdminDashboardBundle? = cachedAdminBundle

    fun isAdminCacheFresh(maxAgeMs: Long = 120_000L): Boolean =
        cachedAdminBundle != null && (System.currentTimeMillis() - cachedAdminFetchedAtMs) < maxAgeMs

    fun invalidateAdminCache() {
        cachedAdminBundle = null
        cachedAdminFetchedAtMs = 0L
    }

    // --- Admin Console ---
    suspend fun fetchAdminBundle(): ApiResult<AdminDashboardBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            coroutineScope {
                val statsDef = async(Dispatchers.IO) {
                    runCatching {
                        val res = supabaseApi.getAdminOverviewStats()
                        if (res.isSuccessful) res.body() else null
                    }.getOrNull()
                }
                val usersDef = async(Dispatchers.IO) {
                    val res = supabaseApi.getProfiles(
                        mapOf("select" to "*", "order" to "created_at.desc,id.desc", "limit" to "200")
                    )
                    if (!res.isSuccessful) throw IllegalStateException(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Admin profiles query failed."))
                    res.body().orEmpty()
                }
                val productsDef = async(Dispatchers.IO) {
                    val res = supabaseApi.getProducts(
                        mapOf("select" to "*", "order" to "created_at.desc,id.desc", "limit" to "200")
                    )
                    if (!res.isSuccessful) throw IllegalStateException(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Admin products query failed."))
                    res.body().orEmpty()
                }
                val ordersDef = async(Dispatchers.IO) {
                    val res = supabaseApi.getOrders(
                        mapOf("select" to "*,product:products(id,title,image_url)", "order" to "created_at.desc", "limit" to "200")
                    )
                    if (!res.isSuccessful) throw IllegalStateException(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Admin orders query failed."))
                    res.body().orEmpty()
                }
                val verificationsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getVerificationRequests(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val withdrawalsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getWithdrawalRequests(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val refundsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getRefundRequests(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val reportsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getReports(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val deletionsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getAccountDeletionRequests(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val reviewsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getReviews(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "200")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val adsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getAdvertisements(
                            mapOf("select" to "*", "order" to "created_at.desc", "limit" to "200")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val campusesDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getCampuses(
                            mapOf("select" to "*", "order" to "name.asc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val plansDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getSubscriptionPlans(
                            mapOf("select" to "*", "order" to "price.asc,duration_months.asc", "limit" to "50")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val subscriptionsDef = async(Dispatchers.IO) {
                    runCatching {
                        supabaseApi.getSubscriptions(
                            mapOf("select" to "*,plan:subscription_plans(*)", "order" to "created_at.desc", "limit" to "100")
                        ).body().orEmpty()
                    }.getOrDefault(emptyList())
                }
                val flagsDef = async(Dispatchers.IO) { fetchPlatformFlags(forceRefresh = true) }

                val serverStats = statsDef.await()
                val users = usersDef.await()
                val products = productsDef.await()
                val orders = ordersDef.await()
                val verifications = verificationsDef.await()
                val withdrawals = withdrawalsDef.await()
                val refunds = refundsDef.await()
                val reports = reportsDef.await()
                val deletionRequests = deletionsDef.await()
                val reviews = reviewsDef.await()
                val ads = adsDef.await()
                val dbCampuses = campusesDef.await()
                val subscriptionPlans = plansDef.await()
                val subscriptions = subscriptionsDef.await()
                val flags = flagsDef.await()

                val campuses = dbCampuses.ifEmpty {
                    (KartGhanaCampuses + users.mapNotNull { it.campus } + products.mapNotNull { it.campus })
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase(Locale.US) }
                        .sorted()
                        .map { CampusRow(name = it, abbreviation = KartFormatters.abbreviateCampus(it)) }
                }

                val usersMap = users.associateBy { it.id }.toMutableMap()
                val missingUserIds = buildList {
                    verifications.mapNotNullTo(this) { it.userId }
                    withdrawals.mapNotNullTo(this) { it.userId }
                    refunds.mapNotNullTo(this) { it.buyerId }
                    orders.mapNotNullTo(this) { it.buyerId }
                    orders.mapNotNullTo(this) { it.sellerId }
                    products.mapNotNullTo(this) { it.sellerId }
                    ads.mapNotNullTo(this) { it.sellerId }
                    subscriptions.mapNotNullTo(this) { it.userId }
                    reports.mapNotNullTo(this) { it.reporterId }
                    reports.mapNotNullTo(this) { it.reportedUserId }
                    deletionRequests.mapNotNullTo(this) { it.userId }
                    reports.filter { it.resolvedTargetType.equals("user", true) || it.resolvedTargetType.equals("profile", true) }
                        .mapNotNullTo(this) { it.resolvedTargetId }
                    reviews.mapNotNullTo(this) { it.reviewerId }
                    reviews.mapNotNullTo(this) { it.buyerId }
                    reviews.mapNotNullTo(this) { it.sellerId }
                }
                    .filter { it.isNotBlank() && it !in usersMap }
                    .distinct()
                if (missingUserIds.isNotEmpty()) {
                    runCatching {
                        missingUserIds.chunked(50).forEach { chunk ->
                            supabaseApi.getProfiles(
                                mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})")
                            ).body().orEmpty().forEach { usersMap[it.id] = it }
                        }
                    }
                }

                val productsMap = products.associateBy { it.id }.toMutableMap()
                val missingProductIds = buildList {
                    orders.mapNotNullTo(this) { it.productId }
                    ads.mapNotNullTo(this) { it.productId }
                    reviews.mapNotNullTo(this) { it.productId }
                    reports.filter { it.resolvedTargetType.equals("product", true) || it.resolvedTargetType.equals("listing", true) }
                        .mapNotNullTo(this) { it.resolvedTargetId }
                }
                    .filter { it.isNotBlank() && it !in productsMap }
                    .distinct()
                if (missingProductIds.isNotEmpty()) {
                    runCatching {
                        missingProductIds.chunked(50).forEach { chunk ->
                            supabaseApi.getProducts(
                                mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})")
                            ).body().orEmpty().forEach { productsMap[it.id] = it }
                        }
                    }
                }

                val totalUsersServer = serverStats?.totalUsers
                val totalListingsServer = serverStats?.totalListings
                val bundle = AdminDashboardBundle(
                    users = users,
                    products = products,
                    orders = orders,
                    verifications = verifications,
                    withdrawals = withdrawals,
                    refunds = refunds,
                    reports = reports,
                    deletionRequests = deletionRequests,
                    reviews = reviews,
                    advertisements = ads,
                    campuses = campuses,
                    subscriptionPlans = subscriptionPlans,
                    subscriptions = subscriptions,
                    usersById = usersMap,
                    ordersById = orders.associateBy { it.id },
                    productsById = productsMap,
                    buyNowEnabled = flags.buyNowEnabled,
                    isMaintenanceMode = flags.isMaintenanceMode,
                    promoDailyPrice = flags.promoDailyPrice,
                    promoWeeklyPrice = flags.promoWeeklyPrice,
                    promoFeaturedPrice = flags.promoFeaturedPrice,
                    platformFlags = flags,
                    serverStats = serverStats,
                    usersHasMore = if (totalUsersServer != null) users.size < totalUsersServer else users.size >= 200,
                    productsHasMore = if (totalListingsServer != null) products.size < totalListingsServer else products.size >= 200
                )
                cachedAdminBundle = bundle
                cachedAdminFetchedAtMs = System.currentTimeMillis()
                ApiResult.Success(bundle)
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load admin dashboard."))
        }
    }

    suspend fun adminBanUser(userId: String, banned: Boolean): ApiResult<Unit> {
        val currentUserId = sessionStore.currentSession().userId
        if (banned && userId == currentUserId) {
            return ApiResult.Error("You cannot suspend your own administrator account.")
        }
        return runApiAction {
            kartWebApi.adminBanUser(mapOf("userId" to userId, "banned" to banned))
        }
    }

    suspend fun adminUpdateUserRole(userId: String, isAdmin: Boolean): ApiResult<Unit> {
        val currentUserId = sessionStore.currentSession().userId
        if (!isAdmin && userId == currentUserId) {
            return ApiResult.Error("You cannot revoke your own administrative privileges.")
        }
        return runApiAction {
            kartWebApi.adminUpdateUserRole(mapOf("userId" to userId, "isAdmin" to isAdmin))
        }
    }

    suspend fun adminUpdateProductStatus(productId: String, status: String): ApiResult<Unit> = runApiAction {
        val normalizedStatus = if (status.equals("Suspended", ignoreCase = true)) "Banned" else status
        kartWebApi.adminUpdateProductStatus(mapOf("productId" to productId, "status" to normalizedStatus))
    }

    suspend fun adminDeleteProduct(productId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminDeleteProduct(mapOf("productId" to productId))
    }

    suspend fun adminResolveVerification(
        requestId: String,
        approve: Boolean,
        adminNotes: String? = null
    ): ApiResult<Unit> = runApiAction {
        val notes = adminNotes?.trim().orEmpty()
        val body = buildMap {
            put("verificationId", requestId)
            put("verification_id", requestId)
            put("requestId", requestId)
            put("id", requestId)
            if (notes.isNotBlank()) {
                put("adminNotes", notes)
                put("admin_notes", notes)
                put("reason", notes)
                put("rejectionReason", notes)
                put("rejection_reason", notes)
            }
        }
        if (approve) kartWebApi.adminApproveVerification(body)
        else kartWebApi.adminRejectVerification(body)
    }

    suspend fun adminResolveWithdrawal(
        requestId: String,
        approve: Boolean,
        reason: String? = null
    ): ApiResult<Unit> = runApiAction {
        if (approve) {
            kartWebApi.adminApproveWithdrawal(mapOf("withdrawalRequestId" to requestId))
        } else {
            val body = buildMap {
                put("withdrawalRequestId", requestId)
                reason?.trim()?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
            }
            kartWebApi.adminRejectWithdrawal(body)
        }
    }

    suspend fun adminManualApproveWithdrawal(
        requestId: String,
        manualReference: String? = null,
        manualTransactionId: String? = null,
        manualNotes: String? = null
    ): ApiResult<Unit> = runApiAction {
        val body = buildMap<String, Any> {
            put("withdrawalRequestId", requestId)
            manualReference?.trim()?.takeIf { it.isNotBlank() }?.let { put("manualReference", it) }
            manualTransactionId?.trim()?.takeIf { it.isNotBlank() }?.let { put("manualTransactionId", it) }
            manualNotes?.trim()?.takeIf { it.isNotBlank() }?.let { put("manualNotes", it) }
        }
        kartWebApi.adminManualApproveWithdrawal(body)
    }

    suspend fun adminRetryWithdrawalPaystack(requestId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminRetryWithdrawalPaystack(mapOf("withdrawalRequestId" to requestId))
    }

    suspend fun adminResolveDeletionRequest(
        requestId: String,
        userId: String?,
        approve: Boolean,
        reason: String? = null
    ): ApiResult<Unit> {
        val currentUserId = sessionStore.currentSession().userId
        if (approve && !userId.isNullOrBlank() && userId == currentUserId) {
            return ApiResult.Error("You cannot approve a deletion request for your own admin account.")
        }
        return runApiAction {
            if (approve) {
                kartWebApi.adminApproveAccountDeletion(mapOf("requestId" to requestId))
            } else {
                val body = buildMap {
                    put("requestId", requestId)
                    reason?.trim()?.takeIf { it.isNotBlank() }?.let { put("rejectionReason", it) }
                }
                kartWebApi.adminRejectAccountDeletion(body)
            }
        }
    }

    suspend fun adminDeleteReview(reviewId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminDeleteReview(mapOf("reviewId" to reviewId))
    }

    suspend fun adminUpdateReportStatus(reportId: String, status: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminUpdateReport(mapOf("reportId" to reportId, "status" to status))
    }

    suspend fun adminUpdateAdStatus(adId: String, status: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val cachedAd = cachedAdminBundle?.advertisements?.firstOrNull { it.id == adId }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        var targetEndIso = cachedAd?.endDate
        val body = mutableMapOf<String, Any?>(
            "status" to status,
            "updated_at" to nowIsoUtc()
        )
        if (status.equals("Active", ignoreCase = true)) {
            val endMs = KartFormatters.parseIsoTimestamp(cachedAd?.endDate)
            val nowMs = System.currentTimeMillis()
            if (endMs <= nowMs) {
                val days = cachedAd?.resolvedDurationDays?.takeIf { it > 0 } ?: 7
                val newEndIso = sdf.format(Date(nowMs + days * 86_400_000L))
                body["start_date"] = sdf.format(Date(nowMs))
                body["end_date"] = newEndIso
                targetEndIso = newEndIso
            }
        }
        val res = runCatching {
            supabaseApi.updateAdvertisement(
                idFilter = "eq.$adId",
                body = body
            )
        }.getOrNull() ?: return ApiResult.Error("Network error while updating ad.")
        if (!res.isSuccessful) {
            return ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Failed to update ad."))
        }

        val updatedAd = res.body()?.firstOrNull() ?: cachedAd
        val productId = updatedAd?.productId?.takeIf { it.isNotBlank() }
        if (productId != null) {
            val adType = updatedAd.adType ?: "Boost"
            val isFeatured = adType.equals("Featured", ignoreCase = true)
            if (status.equals("Active", ignoreCase = true)) {
                val productUpdates = if (isFeatured) {
                    mapOf<String, Any?>("is_featured" to true, "updated_at" to nowIsoUtc())
                } else {
                    buildMap<String, Any?> {
                        put("is_boosted", true)
                        put("updated_at", nowIsoUtc())
                        val endVal = updatedAd.endDate ?: targetEndIso
                        if (!endVal.isNullOrBlank()) put("boost_expires_at", endVal)
                    }
                }
                runCatching {
                    supabaseApi.updateProduct(idFilter = "eq.$productId", body = productUpdates)
                }
            } else {
                val otherActive = runCatching {
                    supabaseApi.getAdvertisements(
                        mapOf(
                            "select" to "id",
                            "product_id" to "eq.$productId",
                            "ad_type" to "eq.$adType",
                            "status" to "eq.Active",
                            "limit" to "1"
                        )
                    ).body().orEmpty()
                }.getOrDefault(emptyList())
                if (otherActive.isEmpty()) {
                    val clearUpdates = if (isFeatured) {
                        mapOf<String, Any?>("is_featured" to false, "updated_at" to nowIsoUtc())
                    } else {
                        mapOf<String, Any?>("is_boosted" to false, "updated_at" to nowIsoUtc())
                    }
                    runCatching {
                        supabaseApi.updateProduct(idFilter = "eq.$productId", body = clearUpdates)
                    }
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    suspend fun adminDeleteAd(adId: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val cachedAd = cachedAdminBundle?.advertisements?.firstOrNull { it.id == adId }
        val res = runCatching {
            supabaseApi.deleteAdvertisement(idFilter = "eq.$adId")
        }.getOrNull() ?: return ApiResult.Error("Network error while deleting ad.")
        if (!res.isSuccessful) {
            return ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Failed to delete ad."))
        }
        val productId = cachedAd?.productId?.takeIf { it.isNotBlank() }
        if (productId != null) {
            val adType = cachedAd.adType ?: "Boost"
            val otherActive = runCatching {
                supabaseApi.getAdvertisements(
                    mapOf(
                        "select" to "id",
                        "product_id" to "eq.$productId",
                        "ad_type" to "eq.$adType",
                        "status" to "eq.Active",
                        "limit" to "1"
                    )
                ).body().orEmpty()
            }.getOrDefault(emptyList())
            if (otherActive.isEmpty()) {
                val isFeatured = adType.equals("Featured", ignoreCase = true)
                val clearUpdates = if (isFeatured) {
                    mapOf<String, Any?>("is_featured" to false, "updated_at" to nowIsoUtc())
                } else {
                    mapOf<String, Any?>("is_boosted" to false, "updated_at" to nowIsoUtc())
                }
                runCatching {
                    supabaseApi.updateProduct(idFilter = "eq.$productId", body = clearUpdates)
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    suspend fun adminAddCampus(name: String, abbreviation: String?): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return ApiResult.Error("Campus name is required.")
        val res = runCatching {
            supabaseApi.createCampus(
                mapOf(
                    "name" to trimmedName,
                    "abbreviation" to abbreviation?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }.getOrNull() ?: return ApiResult.Error("Network error while adding campus.")
        return if (res.isSuccessful) {
            cachedCampuses = null
            campusesFetchedAtMs = 0L
            ApiResult.Success(Unit)
        } else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Failed to add campus."))
    }

    suspend fun adminUpdateCampus(id: String, name: String, abbreviation: String?): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return ApiResult.Error("Campus name is required.")
        val res = runCatching {
            supabaseApi.updateCampus(
                idFilter = "eq.$id",
                body = mapOf(
                    "name" to trimmedName,
                    "abbreviation" to abbreviation?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }.getOrNull() ?: return ApiResult.Error("Network error while updating campus.")
        return if (res.isSuccessful) {
            cachedCampuses = null
            campusesFetchedAtMs = 0L
            ApiResult.Success(Unit)
        } else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Failed to update campus."))
    }

    suspend fun adminDeleteCampus(id: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val res = runCatching {
            supabaseApi.deleteCampus(idFilter = "eq.$id")
        }.getOrNull() ?: return ApiResult.Error("Network error while deleting campus.")
        return if (res.isSuccessful) {
            cachedCampuses = null
            campusesFetchedAtMs = 0L
            ApiResult.Success(Unit)
        } else ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), extractHttpErrorMessage(res), "Failed to delete campus."))
    }

    suspend fun adminResolveRefund(orderId: String, requestId: String, approve: Boolean): ApiResult<Unit> = runApiAction {
        if (approve) kartWebApi.adminRefundOrder(mapOf("orderId" to orderId, "order_id" to orderId))
        else kartWebApi.adminRejectRefund(
            mapOf(
                "requestId" to requestId,
                "refundRequestId" to requestId,
                "refund_request_id" to requestId,
                "id" to requestId
            )
        )
    }

    suspend fun adminCreateSubscriptionPlan(
        name: String,
        price: Double,
        durationMonths: Int,
        features: List<String>
    ): ApiResult<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return ApiResult.Error("Plan name is required.")
        if (price < 0.0) return ApiResult.Error("Price cannot be negative.")
        if (durationMonths < 1) return ApiResult.Error("Duration must be at least 1 month.")
        return runApiAction {
            kartWebApi.adminCreateSubscriptionPlan(
                mapOf(
                    "name" to cleanName,
                    "price" to price,
                    "duration_months" to durationMonths,
                    "features" to features.map { it.trim() }.filter { it.isNotBlank() }
                )
            )
        }
    }

    suspend fun adminUpdateSubscriptionPlan(
        id: String,
        name: String,
        price: Double,
        durationMonths: Int,
        features: List<String>
    ): ApiResult<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return ApiResult.Error("Plan name is required.")
        if (price < 0.0) return ApiResult.Error("Price cannot be negative.")
        if (durationMonths < 1) return ApiResult.Error("Duration must be at least 1 month.")
        return runApiAction {
            kartWebApi.adminUpdateSubscriptionPlan(
                mapOf(
                    "id" to id,
                    "name" to cleanName,
                    "price" to price,
                    "duration_months" to durationMonths,
                    "features" to features.map { it.trim() }.filter { it.isNotBlank() }
                )
            )
        }
    }

    suspend fun adminDeleteSubscriptionPlan(id: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminDeleteSubscriptionPlan(id)
    }

    suspend fun adminUpdateSubscriptionStatus(subscriptionId: String, status: String): ApiResult<Unit> {
        val webRes = runCatching {
            kartWebApi.adminUpdateSubscriptionStatus(
                mapOf("subscriptionId" to subscriptionId, "status" to status)
            )
        }.getOrNull()
        if (webRes?.isSuccessful == true && webRes.body()?.get("success") != false) {
            return ApiResult.Success(Unit)
        }
        if (webRes != null && webRes.code() in 400..499) {
            return ApiResult.Error(KartErrorFormatter.fromHttpStatus(webRes.code(), extractHttpErrorMessage(webRes), "Failed to update subscription."))
        }
        // Fallback to direct Supabase PostgREST under admin RLS if backend returns 500 or is offline
        if (!hasSupabaseKey()) {
            return ApiResult.Error(webRes?.let { extractHttpErrorMessage(it) } ?: requireKeyMessage())
        }
        val dbRes = runCatching {
            supabaseApi.updateSubscription(
                idFilter = "eq.$subscriptionId",
                body = mapOf("status" to status, "updated_at" to nowIsoUtc())
            )
        }.getOrNull() ?: return ApiResult.Error("Network error while updating subscription.")
        return if (dbRes.isSuccessful && !dbRes.body().isNullOrEmpty()) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error(
                extractHttpErrorMessage(dbRes)
                    ?: webRes?.let { extractHttpErrorMessage(it) }
                    ?: KartErrorFormatter.fromHttpStatus(dbRes.code(), fallback = "Failed to update subscription.")
            )
        }
    }

    suspend fun adminSavePlatformSettings(updates: List<Pair<String, Any>>): ApiResult<Unit> {
        if (updates.isEmpty()) return ApiResult.Error("No settings provided.")
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        val currentUserId = sessionStore.currentSession().userId
        val profile = fetchMyProfile(currentUserId)
        if (profile?.isAdmin != true) {
            return ApiResult.Error("Admin access required to update platform settings.")
        }

        val payload = mapOf(
            "updates" to updates.map { (k, v) -> mapOf("key" to k, "value" to v) }
        )
        val webRes = runCatching { kartWebApi.adminPutSettings(payload) }.getOrNull()
        if (webRes?.isSuccessful == true && webRes.body()?.get("success") != false) {
            platformFlagsFetchedAtMs = 0L
            cachedAdminFetchedAtMs = 0L
            return ApiResult.Success(Unit)
        }
        if (webRes != null && webRes.code() in 400..499 && webRes.code() != 404 && webRes.code() != 405) {
            return ApiResult.Error(KartErrorFormatter.fromHttpStatus(webRes.code(), extractHttpErrorMessage(webRes), "Settings update rejected."))
        }

        // Fallback to direct Supabase PostgREST only when backend is unreachable or returns 5xx
        val nowIso = nowIsoUtc()
        for ((key, rawVal) in updates) {
            val dbValue: Any = when (rawVal) {
                is Boolean -> rawVal
                is Number -> rawVal
                else -> rawVal.toString()
            }
            val patchRes = runCatching {
                supabaseApi.patchPlatformSetting(
                    keyFilter = "eq.$key",
                    body = mapOf(
                        "value" to dbValue,
                        "updated_at" to nowIso,
                        "updated_by" to currentUserId.ifBlank { null }
                    )
                )
            }.getOrNull()
            if (patchRes?.isSuccessful == true && !patchRes.body().isNullOrEmpty()) continue

            val category = when {
                key.startsWith("promo_") -> "promotion"
                key in setOf(
                    KEY_MARKETPLACE_SERVICE_FEE,
                    KEY_TRANSACTION_FEE_FIXED,
                    KEY_TRANSACTION_FEE_PERCENT,
                    KEY_WITHDRAWAL_FEE_FLAT,
                    KEY_MIN_DEPOSIT_AMOUNT,
                    KEY_MIN_WITHDRAWAL_AMOUNT
                ) -> "financial"
                else -> "general"
            }
            val upsertRes = runCatching {
                supabaseApi.upsertPlatformSetting(
                    body = mapOf(
                        "key" to key,
                        "value" to dbValue,
                        "category" to category,
                        "label" to key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        "description" to "Platform setting for $key.",
                        "updated_at" to nowIso,
                        "updated_by" to currentUserId.ifBlank { null }
                    )
                )
            }.getOrNull()
            if (upsertRes?.isSuccessful != true) {
                val err = upsertRes?.let { extractHttpErrorMessage(it) }
                    ?: patchRes?.let { extractHttpErrorMessage(it) }
                    ?: webRes?.let { extractHttpErrorMessage(it) }
                    ?: "Failed to update $key."
                return ApiResult.Error(err)
            }
        }
        platformFlagsFetchedAtMs = 0L
        cachedAdminFetchedAtMs = 0L
        return ApiResult.Success(Unit)
    }

    suspend fun adminSetPlatformSetting(key: String, value: Boolean): ApiResult<Unit> =
        adminSavePlatformSettings(listOf(key to value))

    suspend fun adminUpdatePromotionRates(
        dailyPrice: Double,
        weeklyPrice: Double,
        featuredPrice: Double
    ): ApiResult<Unit> {
        if (dailyPrice < 0.0 || weeklyPrice < 0.0 || featuredPrice < 0.0) {
            return ApiResult.Error("Promotion prices cannot be negative.")
        }
        return adminSavePlatformSettings(
            listOf(
                KEY_PROMO_DAILY_PRICE to dailyPrice,
                KEY_PROMO_WEEKLY_PRICE to weeklyPrice,
                KEY_PROMO_FEATURED_PRICE to featuredPrice
            )
        )
    }

    suspend fun fetchAdminUsersPage(
        offset: Int,
        limit: Int = 50,
        query: String = "",
        filter: String = "All"
    ): ApiResult<Pair<List<Profile>, Boolean>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val cleanQ = query.trim().replace(",", " ").replace("(", " ").replace(")", " ").replace("*", "")
            val pageLimit = limit.coerceIn(10, 100)
            val params = linkedMapOf(
                "select" to "*",
                "order" to "created_at.desc,id.desc",
                "offset" to offset.coerceAtLeast(0).toString(),
                "limit" to (pageLimit + 1).toString()
            )
            val andClauses = mutableListOf<String>()
            when (filter) {
                "Active" -> andClauses.add("or(banned.is.null,banned.eq.false)")
                "Banned" -> params["banned"] = "eq.true"
                "Verified" -> params["is_verified"] = "eq.true"
                "Admins" -> params["is_admin"] = "eq.true"
            }
            if (cleanQ.isNotBlank()) {
                if (UUID_REGEX.matches(cleanQ)) {
                    params["id"] = "eq.$cleanQ"
                } else {
                    andClauses.add(
                        "or(display_name.ilike.*$cleanQ*,username.ilike.*$cleanQ*,email.ilike.*$cleanQ*,campus.ilike.*$cleanQ*,phone.ilike.*$cleanQ*)"
                    )
                }
            }
            if (andClauses.isNotEmpty()) {
                params["and"] = "(${andClauses.joinToString(",")})"
            }
            val res = supabaseApi.getProfiles(params)
            if (!res.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Admin profiles query failed."))
            }
            val raw = res.body().orEmpty()
            ApiResult.Success(raw.take(pageLimit) to (raw.size > pageLimit))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load users page."))
        }
    }

    suspend fun fetchAdminProductsPage(
        offset: Int,
        limit: Int = 50,
        query: String = "",
        filter: String = "All"
    ): ApiResult<Triple<List<Product>, Boolean, Map<String, Profile>>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val cleanQ = query.trim().replace(",", " ").replace("(", " ").replace(")", " ").replace("*", "")
            val pageLimit = limit.coerceIn(10, 100)
            val params = linkedMapOf(
                "select" to "*",
                "order" to "created_at.desc,id.desc",
                "offset" to offset.coerceAtLeast(0).toString(),
                "limit" to (pageLimit + 1).toString()
            )
            val andClauses = mutableListOf<String>()
            when (filter) {
                "Active" -> andClauses.add("or(status.is.null,status.ilike.Active)")
                "Suspended" -> andClauses.add("or(status.ilike.Banned,status.ilike.Suspended)")
                "Sold" -> andClauses.add("or(status.ilike.Sold,stock_quantity.lte.0)")
            }
            if (cleanQ.isNotBlank()) {
                if (UUID_REGEX.matches(cleanQ)) {
                    params["id"] = "eq.$cleanQ"
                } else {
                    andClauses.add(
                        "or(title.ilike.*$cleanQ*,category.ilike.*$cleanQ*,campus.ilike.*$cleanQ*,status.ilike.*$cleanQ*)"
                    )
                }
            }
            if (andClauses.isNotEmpty()) {
                params["and"] = "(${andClauses.joinToString(",")})"
            }
            val res = supabaseApi.getProducts(params)
            if (!res.isSuccessful) {
                return ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), fallback = "Admin products query failed."))
            }
            val raw = res.body().orEmpty()
            val items = raw.take(pageLimit)
            val existingUsers = cachedAdminBundle?.usersById.orEmpty()
            val missingSellerIds = items.mapNotNull { it.sellerId }
                .filter { it.isNotBlank() && it !in existingUsers }
                .distinct()
            val resolvedUsers = mutableMapOf<String, Profile>()
            if (missingSellerIds.isNotEmpty()) {
                runCatching {
                    missingSellerIds.chunked(50).forEach { chunk ->
                        supabaseApi.getProfiles(
                            mapOf("select" to "*", "id" to "in.(${chunk.joinToString(",")})")
                        ).body().orEmpty().forEach { resolvedUsers[it.id] = it }
                    }
                }
            }
            ApiResult.Success(Triple(items, raw.size > pageLimit, resolvedUsers))
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Failed to load listings page."))
        }
    }

    private fun extractHttpErrorMessage(res: retrofit2.Response<*>): String? {
        val errBody = runCatching { res.errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = parseHttpErrorBody(errBody, res.code()) ?: return null
        return KartErrorFormatter.fromHttpStatus(res.code(), parsed)
    }

    private suspend fun runApiAction(call: suspend () -> retrofit2.Response<Map<String, Any>>): ApiResult<Unit> {
        return try {
            val res = call()
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.get("success") == false) {
                    val msg = body["error"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: body["message"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Action failed."
                    ApiResult.Error(msg)
                } else {
                    ApiResult.Success(Unit)
                }
            } else {
                val errMsg = extractHttpErrorMessage(res)
                ApiResult.Error(KartErrorFormatter.fromHttpStatus(res.code(), errMsg ?: res.body()?.get("error")?.toString(), "Action failed."))
            }
        } catch (e: Exception) {
            ApiResult.Error(KartErrorFormatter.fromThrowable(e, "Request failed."))
        }
    }
}
