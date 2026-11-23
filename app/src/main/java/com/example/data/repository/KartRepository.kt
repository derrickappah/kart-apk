package com.example.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.BuildConfig
import com.example.data.local.SessionStore
import com.example.data.remote.KartWebApi
import com.example.data.remote.SupabaseApi
import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.CampusRow
import com.example.domain.model.EnrichedConversation
import com.example.domain.model.KartFormatters
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.MarketplaceFilter
import com.example.domain.model.MarketplaceProductsResponse
import com.example.domain.model.MessageRow
import com.example.domain.model.NotificationItem
import com.example.domain.model.Order
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
    val hasReviewed: Boolean = false
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
    val reviews: List<Review>,
    val advertisements: List<ActiveAdvertisementRow>,
    val campuses: List<CampusRow>,
    val usersById: Map<String, Profile> = emptyMap()
)

class KartRepository(
    private val supabaseApi: SupabaseApi,
    private val kartWebApi: KartWebApi,
    val sessionStore: SessionStore,
    private val hasSupabaseKey: () -> Boolean
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

            if (hasSupabaseKey()) {
                val now = nowIsoUtc()
                val adsRes = runCatching {
                    supabaseApi.getAdvertisements(
                        mapOf(
                            "select" to "id,ad_type,product:products!inner(*,seller:profiles(id,display_name,avatar_url,is_verified))",
                            "status" to "eq.Active",
                            "product.status" to "eq.Active",
                            "start_date" to "lte.$now",
                            "end_date" to "gte.$now",
                            "limit" to "40"
                        )
                    )
                }.getOrNull()
                if (adsRes?.isSuccessful == true) {
                    activeAds = adsRes.body().orEmpty().mapNotNull { row ->
                        row.product?.copy(advertisementId = row.id, adType = row.adType)
                    }.filter { it.isActiveListing && !it.isOutOfStock }
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
                if (latestRes?.isSuccessful == true) {
                    latestProducts = latestRes.body().orEmpty().filter { it.isActiveListing && !it.isOutOfStock }
                }
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
                    return ApiResult.Error("Unable to load campus feed (${webRes.code()})", webRes.code())
                }
            }

            val bannerCandidates = activeAds.filter { it.adType == "Featured" || it.adType == "Campus Ad" }
                .ifEmpty { activeAds }
            val bannerProducts = KartFormatters.getFairRotatedPromotions(bannerCandidates, 30, 0).take(5)
            val featuredProducts = KartFormatters.getDivergentFeaturedPromotions(activeAds, bannerProducts, 30, 1)
                .ifEmpty { latestProducts.take(10) }
            val shuffledLatest = KartFormatters.seededShuffle(latestProducts, KartFormatters.getFairTimeSeed(30, 2))
            val heroProducts = when {
                bannerProducts.isNotEmpty() -> bannerProducts
                featuredProducts.isNotEmpty() -> featuredProducts.take(5)
                else -> shuffledLatest.take(5)
            }

            ApiResult.Success(
                HomeFeedData(
                    heroProducts = heroProducts,
                    featuredProducts = featuredProducts,
                    latestProducts = shuffledLatest
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Network error while loading Home feed.")
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
                ApiResult.Error("Failed to fetch marketplace listings (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Unable to connect to Kart marketplace.")
        }
    }

    suspend fun fetchProductDetail(productId: String, currentUserId: String?): ApiResult<ProductDetailBundle> {
        return try {
            var product: Product? = null
            if (hasSupabaseKey()) {
                val res = supabaseApi.getProducts(
                    mapOf(
                        "select" to "*,seller:profiles(id,display_name,username,avatar_url,is_verified,average_rating,streak_count,campus)",
                        "id" to "eq.$productId",
                        "limit" to "1"
                    )
                )
                if (res.isSuccessful) product = res.body()?.firstOrNull()
            }

            if (product == null) {
                // Fallback lookup from public marketplace API
                val webRes = kartWebApi.getMarketplaceProducts(page = 1, limit = 50)
                product = webRes.body()?.products?.firstOrNull { it.id == productId }
            }

            if (product == null) {
                return ApiResult.Error("Listing not found or no longer active.")
            }

            // Increment views asynchronously once per session per listing
            if (trackedProductViews.add(productId)) {
                runCatching { kartWebApi.incrementViews(productId) }
            }

            val sellerId = product.sellerId ?: product.seller?.id
            var sellerProfile: Profile? = null
            var reviews: List<Review> = emptyList()
            var isFollowing = false
            var followerCount = 0
            var verifiedBuyerOrderId: String? = null
            var hasUserReviewed = false

            if (hasSupabaseKey() && !sellerId.isNullOrBlank()) {
                sellerProfile = runCatching {
                    supabaseApi.getProfiles(mapOf("select" to "*", "id" to "eq.$sellerId", "limit" to "1"))
                        .body()?.firstOrNull()
                }.getOrNull()

                reviews = runCatching {
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

                if (!currentUserId.isNullOrBlank() && currentUserId != sellerId) {
                    val buyerOrders = runCatching {
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
                    verifiedBuyerOrderId = buyerOrders.firstOrNull {
                        it.status.equals("Delivered", ignoreCase = true) ||
                            it.status.equals("Completed", ignoreCase = true)
                    }?.id

                    hasUserReviewed = runCatching {
                        supabaseApi.getReviews(
                            mapOf(
                                "select" to "id",
                                "product_id" to "eq.$productId",
                                "or" to "(buyer_id.eq.$currentUserId,reviewer_id.eq.$currentUserId)",
                                "limit" to "1"
                            )
                        ).body().orEmpty().isNotEmpty()
                    }.getOrDefault(false)
                }

                val follows = runCatching {
                    supabaseApi.getFollows(mapOf("select" to "id,follower_id,following_id", "following_id" to "eq.$sellerId"))
                        .body().orEmpty()
                }.getOrDefault(emptyList())
                followerCount = follows.size
                isFollowing = currentUserId != null && follows.any { it.followerId == currentUserId }
            }

            val similar = runCatching {
                kartWebApi.getMarketplaceProducts(
                    page = 1,
                    limit = 8,
                    category = product.category.orEmpty()
                ).body()?.products.orEmpty()
                    .filter { it.id != productId && it.isActiveListing && !it.isOutOfStock }
                    .take(6)
            }.getOrDefault(emptyList())

            ApiResult.Success(
                ProductDetailBundle(
                    product = product,
                    seller = sellerProfile,
                    reviews = reviews,
                    similarProducts = similar,
                    isFollowingSeller = isFollowing,
                    followerCount = followerCount,
                    canReview = verifiedBuyerOrderId != null,
                    hasUserReviewed = hasUserReviewed,
                    verifiedBuyerOrderId = verifiedBuyerOrderId
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load listing details.")
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
            ApiResult.Error(e.localizedMessage ?: "Failed to load listing.")
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
                sessionStore.saveSession(session)
                ApiResult.Success(session)
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                val msg = when {
                    errBody.contains("Email not confirmed", true) -> "Please confirm your email address before signing in."
                    errBody.contains("Invalid login credentials", true) -> "Invalid email or password."
                    else -> "Sign in failed (${res.code()}). Please verify your credentials."
                }
                ApiResult.Error(msg, res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Network error while signing in.")
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
                sessionStore.saveSession(session)
                ApiResult.Success(session)
            } else {
                val errBody = res.errorBody()?.string().orEmpty()
                ApiResult.Error(
                    errBody.ifBlank { "Google sign-in failed (${res.code()}). Please try again." },
                    res.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Network error while signing in with Google.")
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
                    sessionStore.saveSession(
                        UserSession(
                            accessToken = body.accessToken,
                            refreshToken = body.refreshToken.orEmpty(),
                            userId = body.user.id,
                            email = body.user.email ?: email.trim().lowercase()
                        )
                    )
                    ApiResult.Success("Account created and signed in!")
                } else {
                    ApiResult.Success("Account created! Check your email to verify your Kart account.")
                }
            } else {
                ApiResult.Error("Sign up failed (${res.code()}). Email may already be registered.", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Network error during sign up.")
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
                sessionStore.saveSession(session)
                ApiResult.Success(session)
            } else {
                ApiResult.Error("Session expired. Please sign in again.")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to refresh session.")
        }
    }

    suspend fun recoverPassword(email: String): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.recoverPassword(mapOf("email" to email.trim().lowercase()))
            if (res.isSuccessful) ApiResult.Success("Password reset link sent to $email")
            else ApiResult.Error("Could not send reset email (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to send reset link.")
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
        sessionStore.clearSession()
    }

    // --- Profile, Status, Wishlist, Notifications ---
    suspend fun fetchMyProfile(userId: String): Profile? {
        if (!hasSupabaseKey() || userId.isBlank()) return null
        return runCatching {
            supabaseApi.getProfiles(mapOf("select" to "*", "id" to "eq.$userId", "limit" to "1"))
                .body()?.firstOrNull()
        }.getOrNull()
    }

    suspend fun isMaintenanceMode(): Boolean {
        if (!hasSupabaseKey()) return false
        return runCatching {
            val res = supabaseApi.getPlatformSettings(
                mapOf("select" to "key,value", "key" to "eq.maintenance_mode", "limit" to "1")
            ).body()?.firstOrNull()
            res?.value?.toString()?.equals("true", true) == true
        }.getOrDefault(false)
    }

    suspend fun fetchProfileBundle(targetUserId: String, currentUserId: String?): ApiResult<ProfileBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val profile = supabaseApi.getProfiles(
                mapOf("select" to "*", "id" to "eq.$targetUserId", "limit" to "1")
            ).body()?.firstOrNull() ?: return ApiResult.Error("User profile not found.")

            val listings = runCatching {
                supabaseApi.getProducts(
                    mapOf(
                        "select" to "*",
                        "seller_id" to "eq.$targetUserId",
                        "order" to "created_at.desc"
                    )
                ).body().orEmpty()
            }.getOrDefault(emptyList())

            val reviews = runCatching {
                val rawReviews = supabaseApi.getReviews(
                    mapOf(
                        "select" to "*",
                        "seller_id" to "eq.$targetUserId",
                        "order" to "created_at.desc"
                    )
                ).body().orEmpty()
                enrichReviewsWithProfiles(rawReviews)
            }.getOrDefault(emptyList())

            val walletBalance = if (targetUserId == currentUserId) {
                runCatching {
                    supabaseApi.getWallets(mapOf("select" to "balance", "user_id" to "eq.$targetUserId", "limit" to "1"))
                        .body()?.firstOrNull()?.balance ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            val followers = runCatching {
                supabaseApi.getFollows(mapOf("select" to "follower_id", "following_id" to "eq.$targetUserId"))
                    .body().orEmpty()
            }.getOrDefault(emptyList())

            val following = runCatching {
                supabaseApi.getFollows(mapOf("select" to "following_id", "follower_id" to "eq.$targetUserId"))
                    .body().orEmpty()
            }.getOrDefault(emptyList())

            ApiResult.Success(
                ProfileBundle(
                    profile = profile,
                    listings = listings,
                    reviews = reviews,
                    walletBalance = walletBalance,
                    followersCount = followers.size,
                    followingCount = following.size,
                    isFollowing = currentUserId != null && followers.any { it.followerId == currentUserId }
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load profile.")
        }
    }

    suspend fun fetchWishlist(userId: String): ApiResult<List<WishlistRow>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getWishlist(
                mapOf(
                    "select" to "*,product:products(*,seller:profiles(id,display_name,avatar_url,is_verified))",
                    "user_id" to "eq.$userId",
                    "order" to "created_at.desc"
                )
            )
            if (res.isSuccessful) ApiResult.Success(res.body().orEmpty())
            else ApiResult.Error("Failed to fetch wishlist (${res.code()})")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading wishlist.")
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
            else ApiResult.Error("Could not update wishlist (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Wishlist update failed.")
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
            ApiResult.Error(e.localizedMessage ?: "Follow toggle failed.")
        }
    }

    suspend fun fetchFollowProfiles(userId: String, type: String): List<Profile> {
        if (!hasSupabaseKey()) return emptyList()
        return runCatching {
            val rows = if (type == "followers") {
                supabaseApi.getFollows(mapOf("select" to "follower_id", "following_id" to "eq.$userId")).body().orEmpty()
                    .mapNotNull { it.followerId }
            } else {
                supabaseApi.getFollows(mapOf("select" to "following_id", "follower_id" to "eq.$userId")).body().orEmpty()
                    .mapNotNull { it.followingId }
            }
            if (rows.isEmpty()) emptyList()
            else supabaseApi.getProfiles(mapOf("select" to "*", "id" to "in.(${rows.joinToString(",")})")).body().orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchNotifications(userId: String): ApiResult<List<NotificationItem>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getNotifications(
                mapOf("select" to "*", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "50")
            )
            if (res.isSuccessful) ApiResult.Success(res.body().orEmpty())
            else ApiResult.Error("Failed to load notifications.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading notifications.")
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

    suspend fun markNotificationRead(notificationId: String) {
        if (!hasSupabaseKey()) return
        runCatching {
            supabaseApi.markNotificationRead("eq.$notificationId", mapOf("is_read" to true))
        }
    }

    suspend fun deleteNotification(notificationId: String): ApiResult<Unit> {
        return try {
            val res = kartWebApi.deleteNotification(mapOf("notificationId" to notificationId))
            if (res.isSuccessful) ApiResult.Success(Unit) else ApiResult.Error("Could not delete notification.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error deleting notification.")
        }
    }

    // --- Orders & Checkout ---
    suspend fun fetchOrders(userId: String, asSeller: Boolean = false): ApiResult<List<Order>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val filterCol = if (asSeller) "seller_id" else "buyer_id"
            val res = supabaseApi.getOrders(
                mapOf(
                    "select" to "*,product:products(id,title,images,image_url),seller:profiles!orders_seller_id_profiles_fkey(id,display_name,email,is_verified,avatar_url),buyer:profiles!orders_buyer_id_profiles_fkey(id,display_name,email,is_verified,avatar_url)",
                    filterCol to "eq.$userId",
                    "order" to "created_at.desc"
                )
            )
            if (res.isSuccessful) ApiResult.Success(res.body().orEmpty())
            else {
                // Fallback without explicit foreign key hint
                val fallback = supabaseApi.getOrders(
                    mapOf("select" to "*,product:products(id,title,images,image_url)", filterCol to "eq.$userId", "order" to "created_at.desc")
                )
                if (fallback.isSuccessful) ApiResult.Success(fallback.body().orEmpty())
                else ApiResult.Error("Failed to load orders (${fallback.code()}).")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading orders.")
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

            val hasPendingRefund = runCatching {
                supabaseApi.getRefundRequests(
                    mapOf("select" to "id,status", "order_id" to "eq.$orderId", "limit" to "5")
                ).body().orEmpty().any {
                    it.status.equals("Pending", ignoreCase = true) ||
                        it.status.equals("Approved", ignoreCase = true)
                }
            }.getOrDefault(false)

            val hasReviewed = runCatching {
                supabaseApi.getReviews(
                    mapOf("select" to "id", "order_id" to "eq.$orderId", "limit" to "1")
                ).body().orEmpty().isNotEmpty()
            }.getOrDefault(false)

            ApiResult.Success(OrderDetailBundle(order = order, hasPendingRefund = hasPendingRefund, hasReviewed = hasReviewed))
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load order.")
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
                ApiResult.Error("Checkout failed (${res.code()}).")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Unable to initialize Paystack checkout.")
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
                ApiResult.Error("Wallet payment failed (${res.code()}). Check your available balance.")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Wallet checkout error.")
        }
    }

    suspend fun markOrderShipped(orderId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.markOrderShipped(mapOf("orderId" to orderId))
    }

    suspend fun sendDeliveryOtp(orderId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.sendDeliveryOtp(mapOf("orderId" to orderId))
    }

    suspend fun confirmDelivery(orderId: String, code: String, mode: String): ApiResult<Unit> = runApiAction {
        val payload = if (mode == "pin") {
            mapOf("orderId" to orderId, "pin" to code, "method" to "pin")
        } else {
            mapOf("orderId" to orderId, "otp" to code, "method" to "otp")
        }
        kartWebApi.confirmDelivery(payload)
    }

    suspend fun requestRefund(orderId: String, reason: String): ApiResult<Unit> = runApiAction {
        kartWebApi.requestRefund(mapOf("orderId" to orderId, "reason" to reason))
    }

    suspend fun verifyPaystackReference(reference: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifyPaystack(mapOf("reference" to reference))
    }

    suspend fun verifySubscriptionReference(reference: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifySubscription(mapOf("reference" to reference))
    }

    suspend fun verifyPromotionReference(reference: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifyPromotion(mapOf("reference" to reference))
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
            val convsRes = supabaseApi.getConversations(
                mapOf(
                    "select" to "*",
                    "participants" to "cs.{$userId}",
                    "order" to "updated_at.desc"
                )
            )
            val convs = convsRes.body().orEmpty()
            if (convs.isEmpty()) return ApiResult.Success(emptyList())

            val convIds = convs.map { it.id }
            val otherIds = convs.mapNotNull { it.participants.firstOrNull { p -> p != userId } }.distinct()
            val productIds = convs.mapNotNull { it.productId }.distinct()

            val profilesMap = if (otherIds.isNotEmpty()) {
                runCatching {
                    supabaseApi.getProfiles(mapOf("select" to "*", "id" to "in.(${otherIds.joinToString(",")})"))
                        .body().orEmpty().associateBy { it.id }
                }.getOrDefault(emptyMap())
            } else emptyMap()

            val productsMap = if (productIds.isNotEmpty()) {
                runCatching {
                    supabaseApi.getProducts(mapOf("select" to "*", "id" to "in.(${productIds.joinToString(",")})"))
                        .body().orEmpty().associateBy { it.id }
                }.getOrDefault(emptyMap())
            } else emptyMap()

            val messages = runCatching {
                supabaseApi.getMessages(
                    mapOf(
                        "select" to "*",
                        "conversation_id" to "in.(${convIds.joinToString(",")})",
                        "order" to "created_at.desc",
                        "limit" to "500"
                    )
                ).body().orEmpty()
            }.getOrDefault(emptyList())

            val lastByConv = mutableMapOf<String, MessageRow>()
            val unreadByConv = mutableMapOf<String, Int>()
            for (msg in messages) {
                if (!lastByConv.containsKey(msg.conversationId)) lastByConv[msg.conversationId] = msg
                if (msg.senderId != userId && msg.isRead != true) {
                    unreadByConv[msg.conversationId] = (unreadByConv[msg.conversationId] ?: 0) + 1
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
            ApiResult.Error(e.localizedMessage ?: "Failed to load conversations.")
        }
    }

    suspend fun fetchMessagesForConversation(
        conversationId: String,
        afterCreatedAt: String? = null
    ): ApiResult<List<MessageRow>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val query = buildMap {
                put("select", "*")
                put("conversation_id", "eq.$conversationId")
                if (!afterCreatedAt.isNullOrBlank()) {
                    put("created_at", "gt.$afterCreatedAt")
                }
                put("order", "created_at.asc")
                put("limit", "200")
            }
            val res = supabaseApi.getMessages(query)
            if (res.isSuccessful) {
                val rows = res.body().orEmpty()
                if (afterCreatedAt.isNullOrBlank() || rows.isNotEmpty()) {
                    runCatching { kartWebApi.markMessagesRead(mapOf("conversationId" to conversationId)) }
                }
                ApiResult.Success(rows)
            } else ApiResult.Error("Could not load messages.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading messages.")
        }
    }

    suspend fun getOrCreateConversation(currentUserId: String, sellerId: String, productId: String?): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        if (currentUserId == sellerId) return ApiResult.Error("You cannot start a conversation with yourself.")
        return try {
            val existingList = supabaseApi.getConversations(
                mapOf("select" to "*", "participants" to "cs.{$currentUserId,$sellerId}")
            ).body().orEmpty()

            val existing = if (!productId.isNullOrBlank()) {
                existingList.firstOrNull { it.productId == productId }
            } else {
                existingList.firstOrNull()
            }

            if (existing != null) return ApiResult.Success(existing.id)

            val createBody = buildMap<String, Any?> {
                put("participants", listOf(currentUserId, sellerId))
                if (!productId.isNullOrBlank()) put("product_id", productId)
            }
            val created = supabaseApi.createConversation(body = createBody)
            val id = created.body()?.firstOrNull()?.id
            if (id != null) ApiResult.Success(id) else ApiResult.Error("Could not start conversation.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error starting chat.")
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
            (trimmed.startsWith("[IMAGE:", ignoreCase = true) || trimmed.startsWith("[VOICE:", ignoreCase = true))
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
            ApiResult.Error(e.localizedMessage ?: "Message failed to send.")
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
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getWallets(
                mapOf(
                    "select" to "*,wallet_transactions(*,order:orders(id,product:products(title)))",
                    "user_id" to "eq.$userId",
                    "limit" to "1"
                )
            )
            if (res.isSuccessful) {
                ApiResult.Success(res.body()?.firstOrNull() ?: Wallet(userId = userId))
            } else ApiResult.Error("Failed to load wallet.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading wallet.")
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
            ApiResult.Error(e.localizedMessage ?: "Deposit initiation failed.")
        }
    }

    suspend fun verifyWalletDeposit(reference: String): ApiResult<Unit> = runApiAction {
        kartWebApi.verifyWalletPayment(mapOf("reference" to reference))
    }

    suspend fun requestWithdrawal(amount: Double, method: String, details: String): ApiResult<Unit> = runApiAction {
        kartWebApi.withdrawWallet(
            mapOf("amount" to amount, "payoutMethod" to method, "accountDetails" to details)
        )
    }

    suspend fun fetchSubscriptions(userId: String): ApiResult<Pair<List<SubscriptionPlan>, UserSubscription?>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val plans = supabaseApi.getSubscriptionPlans(mapOf("select" to "*", "order" to "duration_months.asc"))
                .body().orEmpty()
            val subs = supabaseApi.getSubscriptions(
                mapOf("select" to "*,plan:subscription_plans(*)", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "5")
            ).body().orEmpty()
            val activeSub = subs.firstOrNull { KartFormatters.isActiveSubscription(it) }
            ApiResult.Success(plans to activeSub)
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load subscription plans.")
        }
    }

    suspend fun subscribeToPlan(planId: String): ApiResult<String> {
        return try {
            val res = kartWebApi.createSubscription(mapOf("planId" to planId, "isApp" to true))
            if (res.isSuccessful) {
                val url = res.body()?.get("authorization_url")?.toString().orEmpty()
                ApiResult.Success(url)
            } else ApiResult.Error("Subscription checkout failed (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Subscription error.")
        }
    }

    suspend fun cancelSubscription(subscriptionId: String): ApiResult<Unit> = runApiAction {
        kartWebApi.cancelSubscription(mapOf("subscriptionId" to subscriptionId))
    }

    // --- Seller Studio ---
    suspend fun fetchSellerHub(userId: String): ApiResult<SellerHubBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val listings = runCatching {
                supabaseApi.getProducts(mapOf("select" to "*", "seller_id" to "eq.$userId", "order" to "created_at.desc"))
                    .body().orEmpty()
            }.getOrDefault(emptyList())

            val orders = runCatching {
                supabaseApi.getOrders(
                    mapOf("select" to "*,product:products(id,title,images,image_url)", "seller_id" to "eq.$userId", "order" to "created_at.desc")
                ).body().orEmpty()
            }.getOrDefault(emptyList())

            val promos = runCatching {
                supabaseApi.getAdvertisements(
                    mapOf("select" to "*,product:products(*)", "seller_id" to "eq.$userId", "order" to "created_at.desc")
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

            // 7-day rolling chart matching accountBundles.js
            val dayFmt = SimpleDateFormat("EEE", Locale.US)
            val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val nowMs = System.currentTimeMillis()
            val labels = mutableListOf<String>()
            val values = mutableListOf<Double>()
            for (offset in 6 downTo 0) {
                val dayDate = Date(nowMs - offset * 86_400_000L)
                val key = dateKeyFmt.format(dayDate)
                labels.add(dayFmt.format(dayDate))
                val daySum = completedOrders.filter {
                    val ts = KartFormatters.parseIsoTimestamp(it.createdAt)
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
            ApiResult.Error(e.localizedMessage ?: "Failed to load Seller Hub.")
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

    suspend fun uploadImageUri(
        contentResolver: ContentResolver,
        uri: Uri,
        bucket: String = "products",
        isPrivate: Boolean = false
    ): ApiResult<String> {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { readBoundedBytes(it, MAX_UPLOAD_BYTES) }
                ?: return ApiResult.Error("Image is unreadable or exceeds the 5 MB size limit.")
            if (bytes.isEmpty() || !isValidImageMagic(bytes)) {
                return ApiResult.Error("Please select a valid JPEG, PNG, or WebP image.")
            }
            val mime = contentResolver.getType(uri)?.takeIf {
                it in setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
            } ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val uid = sessionStore.currentSession().userId
            val filename = "${UUID.randomUUID()}.$ext"
            val scopedPath = if (uid.isNotBlank()) "$uid/$filename" else filename

            // For private KYC / chat uploads, try private Supabase bucket + signed URL first
            if (isPrivate) {
                tryPrivateUploadAndSign(bucket, scopedPath, bytes, mime)?.let { signedUrl ->
                    return ApiResult.Success(signedUrl)
                }
            }

            // Try Kart server-controlled /api/upload endpoint
            val part = MultipartBody.Part.createFormData(
                "file",
                filename,
                bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            val webUpload = runCatching { kartWebApi.uploadImage(part) }.getOrNull()
            val webUrl = webUpload?.body()?.get("url")?.toString()
            if (webUpload?.isSuccessful == true && !webUrl.isNullOrBlank()) {
                return ApiResult.Success(webUrl)
            }

            // Never fall back to public storage for sensitive private KYC documents
            if (isPrivate && bucket == "verifications") {
                return ApiResult.Error("Secure verification upload is currently unavailable. Please try again.")
            }

            // Fallback to Supabase Storage direct upload for public listing/avatar assets
            if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
            val storagePath = if (bucket == "profiles") {
                if (uid.isNotBlank()) "$uid/avatar_${System.currentTimeMillis()}.$ext" else filename
            } else scopedPath
            val storageRes = supabaseApi.uploadToStorage(
                bucket = bucket,
                path = storagePath,
                body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            if (storageRes.isSuccessful) {
                val publicUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$bucket/$storagePath"
                ApiResult.Success(publicUrl)
            } else {
                ApiResult.Error("Image upload failed (${storageRes.code()}).")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Upload failed.")
        }
    }

    suspend fun uploadAudioFile(
        file: java.io.File,
        bucket: String = "chat-media",
        isPrivate: Boolean = true
    ): ApiResult<String> {
        return try {
            if (!file.exists() || file.length() <= 0L) return ApiResult.Error("Recording was empty.")
            if (file.length() > MAX_UPLOAD_BYTES) return ApiResult.Error("Voice recording exceeds the 5 MB limit.")
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return ApiResult.Error("Recording was empty.")
            val mime = "audio/mp4"
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
            ApiResult.Error(e.localizedMessage ?: "Voice upload failed.")
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
                val profile = fetchMyProfile(sellerId)
                val used = profile?.freeListingsUsed ?: 0
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
            else ApiResult.Error("Could not publish listing (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to create listing.")
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
            else ApiResult.Error("Failed to update listing (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error updating listing.")
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
            ApiResult.Error(e.localizedMessage ?: "Delete error.")
        }
    }

    suspend fun createPromotion(productId: String, adType: String, durationDays: Int): ApiResult<String> {
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
            if (hasActivePromotionForProduct(productId)) {
                return ApiResult.Error("This listing already has an active promotion campaign.")
            }
        }
        return try {
            val res = kartWebApi.createPromotion(
                mapOf("productId" to productId, "adType" to adType, "durationDays" to durationDays, "isApp" to true)
            )
            if (res.isSuccessful) {
                val url = res.body()?.get("authorization_url")?.toString().orEmpty()
                ApiResult.Success(url)
            } else ApiResult.Error("Could not create promotion (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Promotion error.")
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
            return ApiResult.Error(parsed ?: "Profile update failed (${webRes.code()}).")
        }

        // Fallback to PostgREST strictly with allowlisted non-privileged fields
        return try {
            val res = supabaseApi.updateProfile(idFilter = "eq.$userId", body = sanitized)
            val updated = res.body()?.firstOrNull()
            if (res.isSuccessful && updated != null) ApiResult.Success(updated)
            else ApiResult.Error("Profile update failed (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error updating profile.")
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
            ApiResult.Error(e.localizedMessage ?: "Unable to check Delivery PIN status.")
        }
    }

    suspend fun saveDeliveryPin(
        pin: String,
        currentPin: String? = null,
        currentPassword: String? = null
    ): ApiResult<Unit> = runApiAction {
        val body = buildMap<String, String> {
            put("pin", pin)
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
                    "idUrl" to idImageUrl,
                    "selfie_image_url" to selfieImageUrl,
                    "selfieImageUrl" to selfieImageUrl,
                    "selfiePhotoUrl" to selfieImageUrl,
                    "selfie_photo_url" to selfieImageUrl,
                    "selfieUrl" to selfieImageUrl,
                    "selfie_url" to selfieImageUrl,
                    "faceImageUrl" to selfieImageUrl,
                    "face_image_url" to selfieImageUrl,
                    "faceUrl" to selfieImageUrl,
                    "student_id_number" to studentIdNumber,
                    "studentIdNumber" to studentIdNumber,
                    "studentId" to studentIdNumber,
                    "student_id" to studentIdNumber,
                    "idNumber" to studentIdNumber,
                    "id_number" to studentIdNumber,
                    "fullName" to fullName.ifBlank { "Kart User" },
                    "full_name" to fullName.ifBlank { "Kart User" },
                    "campus" to campus.ifBlank { "University of Ghana" }
                )
            )
        }
        return webRes
    }

    suspend fun requestAccountDeletion(reason: String): ApiResult<Unit> = runApiAction {
        kartWebApi.requestAccountDeletion(mapOf("reason" to reason))
    }

    suspend fun fetchCampuses(): List<String> {
        if (!hasSupabaseKey()) return KartGhanaCampuses
        return runCatching {
            val rows = supabaseApi.getCampuses(mapOf("select" to "name,abbreviation", "order" to "name.asc"))
                .body().orEmpty()
            if (rows.isNotEmpty()) {
                KartFormatters.registerCampusAbbreviations(rows)
            }
            rows.map { it.name }.ifEmpty { KartGhanaCampuses }
        }.getOrDefault(KartGhanaCampuses)
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

    // --- Admin Console ---
    suspend fun fetchAdminBundle(): ApiResult<AdminDashboardBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val users = runCatching { supabaseApi.getProfiles(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val products = runCatching { supabaseApi.getProducts(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val orders = runCatching { supabaseApi.getOrders(mapOf("select" to "*,product:products(id,title,image_url)", "order" to "created_at.desc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())

            // Explicitly load all Pending queues first so older pending items are never truncated behind resolved rows
            val pendingVerifications = runCatching { supabaseApi.getVerificationRequests(mapOf("select" to "*", "status" to "eq.Pending", "order" to "created_at.asc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val recentVerifications = runCatching { supabaseApi.getVerificationRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val verifications = (pendingVerifications + recentVerifications).distinctBy { it.id }

            val pendingWithdrawals = runCatching { supabaseApi.getWithdrawalRequests(mapOf("select" to "*", "status" to "eq.Pending", "order" to "created_at.asc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val recentWithdrawals = runCatching { supabaseApi.getWithdrawalRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val withdrawals = (pendingWithdrawals + recentWithdrawals).distinctBy { it.id }

            val pendingRefunds = runCatching { supabaseApi.getRefundRequests(mapOf("select" to "*", "status" to "eq.Pending", "order" to "created_at.asc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val recentRefunds = runCatching { supabaseApi.getRefundRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val refunds = (pendingRefunds + recentRefunds).distinctBy { it.id }

            val reports = runCatching { supabaseApi.getReports(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "100")).body().orEmpty() }.getOrDefault(emptyList())
            val reviews = runCatching { supabaseApi.getReviews(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val ads = runCatching { supabaseApi.getAdvertisements(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val campuses = runCatching { supabaseApi.getCampuses(mapOf("select" to "*", "order" to "name.asc")).body().orEmpty() }.getOrDefault(emptyList())

            val usersMap = users.associateBy { it.id }.toMutableMap()
            val missingUserIds = (verifications.mapNotNull { it.userId } + withdrawals.mapNotNull { it.userId } + refunds.mapNotNull { it.buyerId })
                .filter { it.isNotBlank() && it !in usersMap }
                .distinct()
            if (missingUserIds.isNotEmpty()) {
                runCatching {
                    supabaseApi.getProfiles(mapOf("select" to "*", "id" to "in.(${missingUserIds.joinToString(",")})"))
                        .body().orEmpty().forEach { usersMap[it.id] = it }
                }
            }

            ApiResult.Success(
                AdminDashboardBundle(
                    users = users,
                    products = products,
                    orders = orders,
                    verifications = verifications,
                    withdrawals = withdrawals,
                    refunds = refunds,
                    reports = reports,
                    reviews = reviews,
                    advertisements = ads,
                    campuses = campuses,
                    usersById = usersMap
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load admin dashboard.")
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

    suspend fun adminUpdateProductStatus(productId: String, status: String): ApiResult<Unit> = runApiAction {
        kartWebApi.adminUpdateProductStatus(mapOf("productId" to productId, "status" to status))
    }

    suspend fun adminResolveVerification(requestId: String, approve: Boolean): ApiResult<Unit> = runApiAction {
        if (approve) kartWebApi.adminApproveVerification(mapOf("requestId" to requestId))
        else kartWebApi.adminRejectVerification(mapOf("requestId" to requestId))
    }

    suspend fun adminResolveWithdrawal(requestId: String, approve: Boolean): ApiResult<Unit> = runApiAction {
        if (approve) kartWebApi.adminApproveWithdrawal(mapOf("requestId" to requestId))
        else kartWebApi.adminRejectWithdrawal(mapOf("requestId" to requestId))
    }

    suspend fun adminResolveRefund(orderId: String, requestId: String, approve: Boolean): ApiResult<Unit> = runApiAction {
        if (approve) kartWebApi.adminRefundOrder(mapOf("orderId" to orderId))
        else kartWebApi.adminRejectRefund(mapOf("requestId" to requestId))
    }

    private suspend fun runApiAction(call: suspend () -> retrofit2.Response<Map<String, Any>>): ApiResult<Unit> {
        return try {
            val res = call()
            if (res.isSuccessful) ApiResult.Success(Unit)
            else {
                val errBody = runCatching { res.errorBody()?.string() }.getOrNull()
                val parsed = errBody?.let {
                    runCatching { org.json.JSONObject(it).optString("error").ifBlank { null } }.getOrNull()
                }
                ApiResult.Error(parsed ?: res.body()?.get("error")?.toString() ?: "Action failed (${res.code()}).")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Request failed.")
        }
    }
}
