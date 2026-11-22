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
    val followerCount: Int
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
    val campuses: List<CampusRow>
)

class KartRepository(
    private val supabaseApi: SupabaseApi,
    private val kartWebApi: KartWebApi,
    val sessionStore: SessionStore,
    private val hasSupabaseKey: () -> Boolean
) {
    private val trackedAdViews = mutableSetOf<String>()
    private val trackedAdClicks = mutableSetOf<String>()

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
                    }
                }
                val latestRes = runCatching {
                    supabaseApi.getProducts(
                        mapOf(
                            "select" to "*,seller:profiles(id,display_name,avatar_url,is_verified)",
                            "status" to "eq.Active",
                            "order" to "created_at.desc",
                            "limit" to "20"
                        )
                    )
                }.getOrNull()
                if (latestRes?.isSuccessful == true) {
                    latestProducts = latestRes.body().orEmpty()
                }
            }

            // Fallback/primary live feed via public kart.cx Edge endpoint (works even without Supabase anon key)
            if (latestProducts.isEmpty()) {
                val webRes = kartWebApi.getMarketplaceProducts(page = 1, limit = 24, sort = "newest")
                if (webRes.isSuccessful) {
                    val fetched = webRes.body()?.products.orEmpty()
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
                        "select" to "*,seller:profiles(id,display_name,username,avatar_url,is_verified,average_rating,reviews_count,streak_count,campus)",
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

            // Increment views asynchronously (best effort)
            runCatching { kartWebApi.incrementViews(productId) }

            val sellerId = product.sellerId ?: product.seller?.id
            var sellerProfile: Profile? = null
            var reviews: List<Review> = emptyList()
            var isFollowing = false
            var followerCount = 0

            if (hasSupabaseKey() && !sellerId.isNullOrBlank()) {
                sellerProfile = runCatching {
                    supabaseApi.getProfiles(mapOf("select" to "*", "id" to "eq.$sellerId", "limit" to "1"))
                        .body()?.firstOrNull()
                }.getOrNull()

                reviews = runCatching {
                    supabaseApi.getReviews(
                        mapOf(
                            "select" to "*,reviewer:profiles!reviews_reviewer_id_fkey(id,display_name,avatar_url,is_verified)",
                            "or" to "(product_id.eq.$productId,seller_id.eq.$sellerId)",
                            "order" to "created_at.desc",
                            "limit" to "20"
                        )
                    ).body().orEmpty()
                }.getOrDefault(emptyList())

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
                ).body()?.products.orEmpty().filterNot { it.id == productId }.take(6)
            }.getOrDefault(emptyList())

            ApiResult.Success(
                ProductDetailBundle(
                    product = product,
                    seller = sellerProfile,
                    reviews = reviews,
                    similarProducts = similar,
                    isFollowingSeller = isFollowing,
                    followerCount = followerCount
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load listing details.")
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

    suspend fun signOut() {
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
                supabaseApi.getReviews(
                    mapOf(
                        "select" to "*,reviewer:profiles!reviews_reviewer_id_fkey(id,display_name,avatar_url,is_verified)",
                        "seller_id" to "eq.$targetUserId",
                        "order" to "created_at.desc"
                    )
                ).body().orEmpty()
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

    suspend fun toggleFollow(targetUserId: String): ApiResult<Boolean> {
        return try {
            val res = kartWebApi.toggleFollow(mapOf("targetUserId" to targetUserId))
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

    suspend fun fetchOrderById(orderId: String): ApiResult<Order> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
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
            if (order != null) ApiResult.Success(order) else ApiResult.Error("Order not found.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load order.")
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
            kartWebApi.createReport(
                mapOf("targetType" to targetType, "targetId" to targetId, "reason" to reason, "details" to details)
            )
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
                        "limit" to "200"
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

    suspend fun fetchMessagesForConversation(conversationId: String): ApiResult<List<MessageRow>> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.getMessages(
                mapOf(
                    "select" to "*",
                    "conversation_id" to "eq.$conversationId",
                    "order" to "created_at.asc",
                    "limit" to "200"
                )
            )
            if (res.isSuccessful) {
                runCatching { kartWebApi.markMessagesRead(mapOf("conversationId" to conversationId)) }
                ApiResult.Success(res.body().orEmpty())
            } else ApiResult.Error("Could not load messages.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error loading messages.")
        }
    }

    suspend fun getOrCreateConversation(currentUserId: String, sellerId: String, productId: String?): ApiResult<String> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val existing = supabaseApi.getConversations(
                mapOf("select" to "*", "participants" to "cs.{$currentUserId,$sellerId}")
            ).body().orEmpty().firstOrNull { it.productId == productId || productId == null }

            if (existing != null) return ApiResult.Success(existing.id)

            val created = supabaseApi.createConversation(
                body = buildMap {
                    put("participants", listOf(currentUserId, sellerId))
                    if (!productId.isNullOrBlank()) put("product_id", productId)
                }
            )
            val id = created.body()?.firstOrNull()?.id
            if (id != null) ApiResult.Success(id) else ApiResult.Error("Could not start conversation.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error starting chat.")
        }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, content: String): ApiResult<MessageRow> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.sendMessage(
                body = mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to content.trim()
                )
            )
            val sent = res.body()?.firstOrNull()
            if (res.isSuccessful && sent != null) ApiResult.Success(sent)
            else ApiResult.Error("Failed to send message.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Message failed to send.")
        }
    }

    suspend fun fetchUnreadMessagesCount(): Int {
        return runCatching {
            val res = kartWebApi.getUnreadMessagesCount()
            val num = res.body()?.get("count") as? Number
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
            val sub = supabaseApi.getSubscriptions(
                mapOf("select" to "*,plan:subscription_plans(*)", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "1")
            ).body()?.firstOrNull()
            ApiResult.Success(plans to sub)
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
                    mapOf("select" to "*,plan:subscription_plans(*)", "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "1")
                ).body()?.firstOrNull()
            }.getOrNull()

            val completedOrders = orders.filter { it.status in listOf("Paid", "Completed", "Delivered") }
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
                    activeListings = listings.count { it.status == "Active" || it.status == null },
                    chartValues = values,
                    chartLabels = labels
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load Seller Hub.")
        }
    }

    suspend fun uploadImageUri(contentResolver: ContentResolver, uri: Uri, bucket: String = "products"): ApiResult<String> {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ApiResult.Error("Unable to read selected image.")
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val filename = "${UUID.randomUUID()}.$ext"

            // First try Kart /api/upload
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

            // Fallback to Supabase Storage direct upload
            if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
            val storageRes = supabaseApi.uploadToStorage(
                bucket = bucket,
                path = filename,
                body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            )
            if (storageRes.isSuccessful) {
                val publicUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$bucket/$filename"
                ApiResult.Success(publicUrl)
            } else {
                ApiResult.Error("Image upload failed (${storageRes.code()}).")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Upload failed.")
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
        return try {
            val payload = mapOf(
                "seller_id" to sellerId,
                "title" to title.trim(),
                "description" to description.trim(),
                "price" to price,
                "category" to category,
                "condition" to condition,
                "campus" to campus,
                "stock_quantity" to stock.coerceAtLeast(1),
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

    suspend fun updateListing(
        productId: String,
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: String,
        campus: String,
        status: String,
        imageUrls: List<String>
    ): ApiResult<Product> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val payload = mapOf(
                "title" to title.trim(),
                "description" to description.trim(),
                "price" to price,
                "category" to category,
                "condition" to condition,
                "campus" to campus,
                "status" to status,
                "image_url" to imageUrls.firstOrNull(),
                "images" to imageUrls
            )
            val res = supabaseApi.updateProduct(idFilter = "eq.$productId", body = payload)
            val updated = res.body()?.firstOrNull()
            if (res.isSuccessful && updated != null) ApiResult.Success(updated)
            else ApiResult.Error("Failed to update listing (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error updating listing.")
        }
    }

    suspend fun deleteListing(productId: String): ApiResult<Unit> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val res = supabaseApi.deleteProduct("eq.$productId")
            if (res.isSuccessful) ApiResult.Success(Unit) else ApiResult.Error("Could not delete listing.")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Delete error.")
        }
    }

    suspend fun createPromotion(productId: String, adType: String, durationDays: Int): ApiResult<String> {
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
        return try {
            val res = supabaseApi.updateProfile(idFilter = "eq.$userId", body = fields)
            val updated = res.body()?.firstOrNull()
            if (res.isSuccessful && updated != null) ApiResult.Success(updated)
            else ApiResult.Error("Profile update failed (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Error updating profile.")
        }
    }

    suspend fun updatePayoutSettings(momoNetwork: String, momoNumber: String, bankName: String, bankAccount: String): ApiResult<Unit> =
        runApiAction {
            kartWebApi.updatePayoutSettings(
                mapOf(
                    "momo_network" to momoNetwork,
                    "momo_number" to momoNumber,
                    "bank_name" to bankName,
                    "bank_account_number" to bankAccount
                )
            )
        }

    suspend fun updatePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = runApiAction {
        kartWebApi.updatePassword(mapOf("currentPassword" to currentPassword, "newPassword" to newPassword))
    }

    suspend fun saveDeliveryPin(pin: String): ApiResult<Unit> = runApiAction {
        kartWebApi.saveDeliveryPin(mapOf("pin" to pin))
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

    suspend fun submitKycVerification(idImageUrl: String, selfieImageUrl: String, studentIdNumber: String): ApiResult<Unit> =
        runApiAction {
            kartWebApi.submitVerificationRequest(
                mapOf(
                    "id_image_url" to idImageUrl,
                    "selfie_image_url" to selfieImageUrl,
                    "student_id_number" to studentIdNumber
                )
            )
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

    // --- Admin Console ---
    suspend fun fetchAdminBundle(): ApiResult<AdminDashboardBundle> {
        if (!hasSupabaseKey()) return ApiResult.Error(requireKeyMessage())
        return try {
            val users = runCatching { supabaseApi.getProfiles(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val products = runCatching { supabaseApi.getProducts(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val orders = runCatching { supabaseApi.getOrders(mapOf("select" to "*,product:products(id,title,image_url)", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val verifications = runCatching { supabaseApi.getVerificationRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val withdrawals = runCatching { supabaseApi.getWithdrawalRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val refunds = runCatching { supabaseApi.getRefundRequests(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val reports = runCatching { supabaseApi.getReports(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val reviews = runCatching { supabaseApi.getReviews(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val ads = runCatching { supabaseApi.getAdvertisements(mapOf("select" to "*", "order" to "created_at.desc", "limit" to "50")).body().orEmpty() }.getOrDefault(emptyList())
            val campuses = runCatching { supabaseApi.getCampuses(mapOf("select" to "*", "order" to "name.asc")).body().orEmpty() }.getOrDefault(emptyList())
            ApiResult.Success(
                AdminDashboardBundle(users, products, orders, verifications, withdrawals, refunds, reports, reviews, ads, campuses)
            )
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load admin dashboard.")
        }
    }

    suspend fun adminBanUser(userId: String, banned: Boolean): ApiResult<Unit> = runApiAction {
        kartWebApi.adminBanUser(mapOf("userId" to userId, "banned" to banned))
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
            else ApiResult.Error(res.body()?.get("error")?.toString() ?: "Action failed (${res.code()}).")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Request failed.")
        }
    }
}
