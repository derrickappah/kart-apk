package com.example.data.remote

import com.example.domain.model.AccountDeletionRequestRow
import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.AdminOverviewStatsRpc
import com.example.domain.model.AuthResponse
import com.example.domain.model.CampusRow
import com.example.domain.model.ConversationRow
import com.example.domain.model.FollowRow
import com.example.domain.model.FollowStatsRpc
import com.example.domain.model.InboxItemRpc
import com.example.domain.model.MessageRow
import com.example.domain.model.NotificationItem
import com.example.domain.model.Order
import com.example.domain.model.PlatformSetting
import com.example.domain.model.Product
import com.example.domain.model.Profile
import com.example.domain.model.RefundRequestRow
import com.example.domain.model.ReportRow
import com.example.domain.model.Review
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.UnreadSummaryRpc
import com.example.domain.model.UserSubscription
import com.example.domain.model.VerificationRequestRow
import com.example.domain.model.Wallet
import com.example.domain.model.WalletTransaction
import com.example.domain.model.WishlistRow
import com.example.domain.model.WithdrawalRequestRow
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SupabaseApi {
    // --- Auth ---
    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<AuthResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(@Body body: Map<String, String>): Response<AuthResponse>

    @PUT("auth/v1/user")
    suspend fun updateAuthUser(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    @POST("auth/v1/resend")
    suspend fun resendConfirmation(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/v1/logout")
    suspend fun logout(@Query("scope") scope: String = "global"): Response<Unit>

    // --- Products & Ads ---
    @GET("rest/v1/products")
    suspend fun getProducts(
        @QueryMap params: Map<String, String>
    ): Response<List<Product>>

    @POST("rest/v1/products")
    suspend fun createProduct(
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Product>>

    @PATCH("rest/v1/products")
    suspend fun updateProduct(
        @Query("id") idFilter: String,
        @Query("seller_id") sellerIdFilter: String? = null,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Product>>

    @DELETE("rest/v1/products")
    suspend fun deleteProduct(
        @Query("id") idFilter: String,
        @Query("seller_id") sellerIdFilter: String? = null
    ): Response<Unit>

    @GET("rest/v1/advertisements")
    suspend fun getAdvertisements(
        @QueryMap params: Map<String, String>
    ): Response<List<ActiveAdvertisementRow>>

    @POST("rest/v1/rpc/get_home_promoted_products")
    suspend fun getHomePromotedProducts(
        @Body body: Map<String, @JvmSuppressWildcards Any> = mapOf("p_limit" to 20, "p_window_minutes" to 30)
    ): Response<List<ActiveAdvertisementRow>>

    @POST("rest/v1/rpc/get_home_recommended_products")
    suspend fun getHomeRecommendedProducts(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Product>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/advertisements")
    suspend fun updateAdvertisement(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<ActiveAdvertisementRow>>

    @DELETE("rest/v1/advertisements")
    suspend fun deleteAdvertisement(@Query("id") idFilter: String): Response<Unit>

    // --- Profiles & Follows ---
    @GET("rest/v1/profiles")
    suspend fun getProfiles(@QueryMap params: Map<String, String>): Response<List<Profile>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Profile>>

    @GET("rest/v1/follows")
    suspend fun getFollows(@QueryMap params: Map<String, String>): Response<List<FollowRow>>

    // --- Orders ---
    @GET("rest/v1/orders")
    suspend fun getOrders(@QueryMap params: Map<String, String>): Response<List<Order>>

    // --- Wallet ---
    @GET("rest/v1/wallets")
    suspend fun getWallets(@QueryMap params: Map<String, String>): Response<List<Wallet>>

    @GET("rest/v1/wallet_transactions")
    suspend fun getWalletTransactions(@QueryMap params: Map<String, String>): Response<List<WalletTransaction>>

    // --- Scalable RPCs ---
    @POST("rest/v1/rpc/get_unread_summary")
    suspend fun getUnreadSummary(): Response<UnreadSummaryRpc>

    @POST("rest/v1/rpc/get_follow_stats")
    suspend fun getFollowStats(
        @Body body: Map<String, String>
    ): Response<FollowStatsRpc>

    @POST("rest/v1/rpc/get_admin_overview_stats")
    suspend fun getAdminOverviewStats(): Response<AdminOverviewStatsRpc>

    @POST("rest/v1/rpc/get_user_inbox")
    suspend fun getUserInbox(
        @Body body: Map<String, Int> = mapOf("p_limit" to 40)
    ): Response<List<InboxItemRpc>>

    // --- Wishlist ---
    @GET("rest/v1/wishlist")
    suspend fun getWishlist(@QueryMap params: Map<String, String>): Response<List<WishlistRow>>

    // --- Notifications ---
    @GET("rest/v1/notifications")
    suspend fun getNotifications(@QueryMap params: Map<String, String>): Response<List<NotificationItem>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationRead(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @PATCH("rest/v1/notifications")
    suspend fun markAllNotificationsRead(
        @Query("user_id") userIdFilter: String,
        @Query("is_read") isReadFilter: String = "eq.false",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    // --- Conversations & Messages ---
    @GET("rest/v1/conversations")
    suspend fun getConversations(@QueryMap params: Map<String, String>): Response<List<ConversationRow>>

    @POST("rest/v1/conversations")
    suspend fun createConversation(
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<ConversationRow>>

    @PATCH("rest/v1/conversations")
    suspend fun updateConversation(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @GET("rest/v1/messages")
    suspend fun getMessages(@QueryMap params: Map<String, String>): Response<List<MessageRow>>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<MessageRow>>

    // --- Reviews ---
    @GET("rest/v1/reviews")
    suspend fun getReviews(@QueryMap params: Map<String, String>): Response<List<Review>>

    @DELETE("rest/v1/reviews")
    suspend fun deleteReview(@Query("id") idFilter: String): Response<Unit>

    // --- Subscriptions ---
    @GET("rest/v1/subscription_plans")
    suspend fun getSubscriptionPlans(@QueryMap params: Map<String, String>): Response<List<SubscriptionPlan>>

    @GET("rest/v1/subscriptions")
    suspend fun getSubscriptions(@QueryMap params: Map<String, String>): Response<List<UserSubscription>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/subscriptions")
    suspend fun updateSubscription(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<UserSubscription>>

    // --- Platform Settings & Campuses ---
    @GET("rest/v1/platform_settings")
    suspend fun getPlatformSettings(@QueryMap params: Map<String, String>): Response<List<PlatformSetting>>

    @POST("rest/v1/platform_settings")
    suspend fun upsertPlatformSetting(
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Query("on_conflict") onConflict: String = "key",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @PATCH("rest/v1/platform_settings")
    suspend fun patchPlatformSetting(
        @Header("Prefer") prefer: String = "return=representation",
        @Query("key") keyFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<PlatformSetting>>

    @GET("rest/v1/campuses")
    suspend fun getCampuses(@QueryMap params: Map<String, String>): Response<List<CampusRow>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/campuses")
    suspend fun createCampus(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<List<CampusRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/campuses")
    suspend fun updateCampus(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<CampusRow>>

    @DELETE("rest/v1/campuses")
    suspend fun deleteCampus(@Query("id") idFilter: String): Response<Unit>

    // --- Admin & Compliance Tables ---
    @GET("rest/v1/verification_requests")
    suspend fun getVerificationRequests(@QueryMap params: Map<String, String>): Response<List<VerificationRequestRow>>

    @GET("rest/v1/account_deletion_requests")
    suspend fun getAccountDeletionRequests(@QueryMap params: Map<String, String>): Response<List<AccountDeletionRequestRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/account_deletion_requests")
    suspend fun updateAccountDeletionRequest(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<AccountDeletionRequestRow>>

    @GET("rest/v1/reports")
    suspend fun getReports(@QueryMap params: Map<String, String>): Response<List<ReportRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/reports")
    suspend fun updateReport(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<ReportRow>>

    @GET("rest/v1/withdrawal_requests")
    suspend fun getWithdrawalRequests(@QueryMap params: Map<String, String>): Response<List<WithdrawalRequestRow>>

    @GET("rest/v1/refund_requests")
    suspend fun getRefundRequests(@QueryMap params: Map<String, String>): Response<List<RefundRequestRow>>

    // --- Device Push Tokens (FCM) ---
    @POST("rest/v1/rpc/upsert_device_token")
    suspend fun upsertDeviceTokenRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @POST("rest/v1/device_tokens")
    suspend fun upsertDeviceToken(
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Query("on_conflict") onConflict: String = "fcm_token",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @POST("rest/v1/rpc/delete_device_token")
    suspend fun deleteDeviceTokenRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @DELETE("rest/v1/device_tokens")
    suspend fun deleteDeviceToken(
        @Query("fcm_token") fcmTokenFilter: String
    ): Response<Unit>

    // --- Storage Upload & Signed URLs ---
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadToStorage(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Header("x-upsert") upsert: String = "true",
        @Body body: RequestBody
    ): Response<Map<String, Any>>

    @POST("storage/v1/object/sign/{bucket}/{path}")
    suspend fun createSignedStorageUrl(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Body body: Map<String, @JvmSuppressWildcards Any> = mapOf("expiresIn" to 604800)
    ): Response<Map<String, Any>>
}
