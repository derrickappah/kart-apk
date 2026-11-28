package com.example.data.remote

import com.example.domain.model.MessageRow
import com.example.domain.model.NotificationItem
import com.example.domain.model.Order
import com.example.domain.model.Review
import com.example.domain.model.SellerSnippet
import com.example.domain.model.Wallet
import com.example.domain.model.WalletTransaction
import com.example.domain.model.WishlistRow
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: String,
    val title: String = "",
    val price: Double = 0.0,
    val currency: String? = "GHS",
    @Json(name = "image_url") val imageUrl: String? = null,
    val images: List<String>? = null,
    val condition: String? = null,
    val campus: String? = null,
    val category: String? = null,
    @Json(name = "seller_id") val sellerId: String? = null,
    val status: String? = "Active",
    @Json(name = "stock_quantity") val stockQuantity: Int? = 1,
    @Json(name = "is_boosted") val isBoosted: Boolean? = false,
    @Json(name = "is_featured") val isFeatured: Boolean? = false,
    @Json(name = "views_count") val viewsCount: Int? = 0,
    @Json(name = "advertisement_id") val advertisementId: String? = null,
    @Json(name = "ad_type") val adType: String? = null,
    @Json(name = "created_at") val createdAt: String? = "",
    val seller: SellerSnippet? = null
)

@JsonClass(generateAdapter = true)
data class MarketplacePageResponse(
    val products: List<ProductDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val source: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class OrdersCursorResponse(
    val orders: List<Order> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class WalletTransactionsCursorResponse(
    val wallet: Wallet? = null,
    val transactions: List<WalletTransaction> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class WishlistCursorResponse(
    val ids: List<String> = emptyList(),
    val items: List<WishlistRow> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationsCursorResponse(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int? = 0,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ReviewsCursorResponse(
    val reviews: List<Review> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class CursorConversationOtherUserDto(
    val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = false,
    val online: Boolean? = false,
    val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class CursorConversationProductDto(
    val id: String,
    val title: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    val price: Double? = null
)

@JsonClass(generateAdapter = true)
data class CursorConversationDto(
    val id: String,
    val participants: List<String> = emptyList(),
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    val otherUser: CursorConversationOtherUserDto? = null,
    val product: CursorConversationProductDto? = null,
    val lastMessage: MessageRow? = null,
    val unreadCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class ConversationsCursorResponse(
    val conversations: List<CursorConversationDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class MessagesCursorResponse(
    val messages: List<MessageRow> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class SendMessageResponse(
    val success: Boolean = false,
    val message: MessageRow? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PresenceStatusDto(
    val online: Boolean = false,
    val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class PresenceBatchResponse(
    val presence: Map<String, PresenceStatusDto> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class PresenceTouchResponse(
    val ok: Boolean = false,
    val ttlSeconds: Int = 45
)

@JsonClass(generateAdapter = true)
data class AdminConsolePageResponse(
    val resource: String? = null,
    val items: List<Map<String, Any?>> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val kpis: Map<String, Any?>? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PresignRequest(
    val bucket: String = "products",
    val contentType: String,
    val fileSize: Long,
    val conversationId: String? = null
)

@JsonClass(generateAdapter = true)
data class PresignResponse(
    val provider: String,
    val uploadUrl: String,
    val method: String = "PUT",
    val objectKey: String,
    val publicUrl: String,
    val isPrivate: Boolean = false,
    val expiresIn: Int = 300,
    val headers: Map<String, String> = emptyMap(),
    val token: String? = null
)

interface KartCursorApi {

    @GET("api/marketplace/products")
    suspend fun getMarketplaceProducts(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 12,
        @Query("search") search: String? = null,
        @Query("campus") campus: String? = null,
        @Query("category") category: String? = null,
        @Query("condition") condition: String? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("sort") sort: String = "newest"
    ): MarketplacePageResponse

    @GET("api/orders")
    suspend fun getOrders(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("role") role: String = "buyer",
        @Query("status") status: String? = null
    ): OrdersCursorResponse

    @GET("api/wallet/transactions")
    suspend fun getWalletTransactions(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("type") type: String? = null
    ): WalletTransactionsCursorResponse

    @GET("api/wishlist")
    suspend fun getWishlist(
        @Query("include") include: String = "products",
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): WishlistCursorResponse

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("unreadOnly") unreadOnly: Boolean = false
    ): NotificationsCursorResponse

    @GET("api/reviews")
    suspend fun getReviews(
        @Query("productId") productId: String? = null,
        @Query("sellerId") sellerId: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20
    ): ReviewsCursorResponse

    @GET("api/messages")
    suspend fun getConversations(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 25
    ): ConversationsCursorResponse

    @GET("api/messages/{conversationId}")
    suspend fun getConversationMessages(
        @Path("conversationId") conversationId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50
    ): MessagesCursorResponse

    @POST("api/messages/{conversationId}")
    suspend fun sendConversationMessage(
        @Path("conversationId") conversationId: String,
        @Body payload: Map<String, String>
    ): SendMessageResponse

    @GET("api/presence")
    suspend fun getPresenceBatch(
        @Query("userIds") userIdsCsv: String
    ): PresenceBatchResponse

    @POST("api/presence")
    suspend fun touchPresence(): PresenceTouchResponse

    @GET("api/admin/console/{resource}")
    suspend fun getAdminConsolePage(
        @Path("resource") resource: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("includeKpis") includeKpis: Boolean = false
    ): AdminConsolePageResponse

    @POST("api/uploads/presign")
    suspend fun requestPresignedUpload(
        @Body body: PresignRequest
    ): PresignResponse

    @POST("api/orders/create")
    suspend fun createOrder(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Map<String, Any?>
}
