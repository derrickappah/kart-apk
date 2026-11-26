package com.example.data.remote

import com.example.domain.model.MarketplaceProductsResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface KartWebApi {
    @GET("api/marketplace/products")
    suspend fun getMarketplaceProducts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 12,
        @Query("search") search: String = "",
        @Query("campus") campus: String = "",
        @Query("category") category: String = "",
        @Query("condition") condition: String = "",
        @Query("minPrice") minPrice: String? = null,
        @Query("maxPrice") maxPrice: String? = null,
        @Query("sort") sort: String = "newest"
    ): Response<MarketplaceProductsResponse>

    @POST("api/ads/track")
    suspend fun trackAdEvent(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/products/{id}/increment-views")
    suspend fun incrementViews(@Path("id") productId: String): Response<Map<String, Any>>

    @POST("api/products/{id}/increment-shares")
    suspend fun incrementShares(@Path("id") productId: String): Response<Map<String, Any>>

    @POST("api/wishlist/add")
    suspend fun addToWishlist(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/wishlist/remove")
    suspend fun removeFromWishlist(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/follow/toggle")
    suspend fun toggleFollow(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/follow/list")
    suspend fun getFollowList(
        @Query("userId") userId: String,
        @Query("type") type: String
    ): Response<Map<String, Any>>

    @POST("api/orders/create")
    suspend fun createOrder(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/orders/pay-with-wallet")
    suspend fun payOrderWithWallet(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/orders/mark-shipped")
    suspend fun markOrderShipped(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/orders/send-delivery-otp")
    suspend fun sendDeliveryOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/orders/confirm-delivery")
    suspend fun confirmDelivery(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/orders/delivery-qr")
    suspend fun generateDeliveryQr(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/orders/cancel")
    suspend fun cancelOrder(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/orders/request-refund")
    suspend fun requestRefund(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/paystack/verify")
    suspend fun verifyPaystack(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/reviews/create")
    suspend fun createReview(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/reports/create")
    suspend fun createReport(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/messages/mark-read")
    suspend fun markMessagesRead(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/messages/unread-count")
    suspend fun getUnreadMessagesCount(): Response<Map<String, Any>>

    @POST("api/notifications/delete")
    suspend fun deleteNotification(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/wallet/deposit")
    suspend fun depositWallet(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/wallet/verify-payment")
    suspend fun verifyWalletPayment(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/wallet/withdraw")
    suspend fun withdrawWallet(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/promotions/create")
    suspend fun createPromotion(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/promotions/verify")
    suspend fun verifyPromotion(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/subscriptions/create")
    suspend fun createSubscription(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/subscriptions/cancel")
    suspend fun cancelSubscription(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/subscriptions/verify")
    suspend fun verifySubscription(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/settings/profile")
    suspend fun updateProfileSettings(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("api/settings/payment")
    suspend fun updatePayoutSettings(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("api/settings/password")
    suspend fun updatePassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/settings/delivery-pin/status")
    suspend fun getDeliveryPinStatus(): Response<Map<String, Any>>

    @POST("api/settings/delivery-pin")
    suspend fun saveDeliveryPin(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/send-verification-otp")
    suspend fun sendEmailVerificationOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/verify-email")
    suspend fun verifyEmailOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/send-phone-otp")
    suspend fun sendPhoneOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/verify-phone")
    suspend fun verifyPhoneOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/mobile/exchange")
    suspend fun exchangeMobileAuthCode(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/users/block")
    suspend fun blockUser(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/users/unblock")
    suspend fun unblockUser(@Body body: Map<String, String>): Response<Map<String, Any>>

    @GET("api/users/blocked")
    suspend fun getBlockedUsers(): Response<Map<String, Any>>

    @POST("api/verification/request")
    suspend fun submitVerificationRequest(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Map<String, Any>>

    @POST("api/account/request-deletion")
    suspend fun requestAccountDeletion(@Body body: Map<String, String>): Response<Map<String, Any>>

    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<Map<String, Any>>

    // --- Admin Actions ---
    @POST("api/admin/users/ban")
    suspend fun adminBanUser(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/admin/products/update-status")
    suspend fun adminUpdateProductStatus(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/verifications/approve")
    suspend fun adminApproveVerification(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/verifications/reject")
    suspend fun adminRejectVerification(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/withdrawals/approve")
    suspend fun adminApproveWithdrawal(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/withdrawals/reject")
    suspend fun adminRejectWithdrawal(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/orders/refund")
    suspend fun adminRefundOrder(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/admin/refund-requests/reject")
    suspend fun adminRejectRefund(@Body body: Map<String, String>): Response<Map<String, Any>>

    @PATCH("api/admin/settings")
    suspend fun adminPatchSettings(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @PUT("api/admin/settings")
    suspend fun adminPutSettings(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>

    @POST("api/admin/settings")
    suspend fun adminUpdateSettings(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, Any>>
}
