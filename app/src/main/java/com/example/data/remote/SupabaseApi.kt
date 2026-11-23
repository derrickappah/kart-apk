package com.example.data.remote

import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.AuthResponse
import com.example.domain.model.CampusRow
import com.example.domain.model.ConversationRow
import com.example.domain.model.FollowRow
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
import com.example.domain.model.UserSubscription
import com.example.domain.model.VerificationRequestRow
import com.example.domain.model.Wallet
import com.example.domain.model.WishlistRow
import com.example.domain.model.WithdrawalRequestRow
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
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

    // --- Subscriptions ---
    @GET("rest/v1/subscription_plans")
    suspend fun getSubscriptionPlans(@QueryMap params: Map<String, String>): Response<List<SubscriptionPlan>>

    @GET("rest/v1/subscriptions")
    suspend fun getSubscriptions(@QueryMap params: Map<String, String>): Response<List<UserSubscription>>

    // --- Platform Settings & Campuses ---
    @GET("rest/v1/platform_settings")
    suspend fun getPlatformSettings(@QueryMap params: Map<String, String>): Response<List<PlatformSetting>>

    @GET("rest/v1/campuses")
    suspend fun getCampuses(@QueryMap params: Map<String, String>): Response<List<CampusRow>>

    // --- Admin & Compliance Tables ---
    @GET("rest/v1/verification_requests")
    suspend fun getVerificationRequests(@QueryMap params: Map<String, String>): Response<List<VerificationRequestRow>>

    @GET("rest/v1/reports")
    suspend fun getReports(@QueryMap params: Map<String, String>): Response<List<ReportRow>>

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
