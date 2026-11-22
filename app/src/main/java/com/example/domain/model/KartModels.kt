package com.example.domain.model

import androidx.annotation.DrawableRes
import com.example.R
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class CategoryItem(
    val name: String,
    val subtitle: String,
    @DrawableRes val imageRes: Int
)

val KartCategories: List<CategoryItem> = listOf(
    CategoryItem("Clothing", "Outfits, shoes, bags", R.drawable.cat_clothing),
    CategoryItem("Electronics", "Phones, laptops, audio", R.drawable.cat_electronics),
    CategoryItem("Dorm Furniture", "Chairs, desks, lamps", R.drawable.cat_furniture),
    CategoryItem("Kitchenware", "Cookware, kettles, blenders", R.drawable.cat_kitchenware),
    CategoryItem("Textbooks", "Course books, guides, notes", R.drawable.cat_textbooks),
    CategoryItem("Beauty & Grooming", "Skincare, hair, perfumes", R.drawable.cat_beauty),
    CategoryItem("Sports & Fitness", "Footballs, gym, mats", R.drawable.cat_sports),
    CategoryItem("Health & Wellness", "Vitamins, juices, snacks", R.drawable.cat_wellness),
    CategoryItem("Games & Consoles", "PS5, Switch, games", R.drawable.cat_games),
    CategoryItem("Tickets & Events", "Concerts, raves, passes", R.drawable.cat_tickets),
    CategoryItem("Musical Instruments", "Guitars, keyboards, mics", R.drawable.cat_instruments),
    CategoryItem("School Supplies", "Calculators, bags, stationery", R.drawable.cat_school),
    CategoryItem("Home Appliances", "Fridges, microwaves, fans", R.drawable.cat_appliances),
    CategoryItem("Services & Tutoring", "Lessons, tech support, repair", R.drawable.cat_services),
    CategoryItem("Arts & Crafts", "Paints, canvas, design", R.drawable.cat_crafts)
)

val KartTopFourCategories: List<CategoryItem> = KartCategories.take(4)

data class ConditionOption(
    val name: String,
    val description: String
)

val KartConditionOptions: List<ConditionOption> = listOf(
    ConditionOption("New", "Brand new, sealed or unused"),
    ConditionOption("Like New", "Flawless, barely used"),
    ConditionOption("Good", "Fully working, minor wear"),
    ConditionOption("Fair", "Noticeable wear, works fine"),
    ConditionOption("Acceptable", "Heavy signs of use, functional")
)

val KartConditions: List<String> = KartConditionOptions.map { it.name }

fun conditionOptionFor(condition: String?): ConditionOption? {
    val normalized = condition?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return KartConditionOptions.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: when {
            normalized.equals("Brand New", ignoreCase = true) -> KartConditionOptions.first()
            normalized.equals("Used", ignoreCase = true) -> KartConditionOptions.last()
            else -> null
        }
}

val KartSortOptions: List<Pair<String, String>> = listOf(
    "newest" to "Newest First",
    "oldest" to "Oldest First",
    "price-low" to "Price: Low to High",
    "price-high" to "Price: High to Low"
)

val KartReviewTags: List<String> = listOf(
    "Fair Price",
    "Punctual",
    "Item as Described",
    "Friendly",
    "Quick Response"
)

val KartGhanaCampuses: List<String> = listOf(
    "University of Ghana (Legon)",
    "KNUST (Kumasi)",
    "University of Cape Coast (UCC)",
    "Ashesi University",
    "UPSA",
    "GIMPA",
    "Central University",
    "UDS",
    "UMaT",
    "Accra Technical University",
    "Academic City University",
    "Lancaster University Ghana"
)

enum class EmptyStateAsset(@DrawableRes val drawableRes: Int) {
    ACTIVE_LISTINGS(R.drawable.empty_active_listings),
    AUTH_ERROR(R.drawable.empty_auth_error),
    ERROR(R.drawable.empty_error),
    LISTINGS(R.drawable.empty_listings),
    MESSAGES(R.drawable.empty_messages),
    NOTIFICATIONS(R.drawable.empty_notifications),
    OFFLINE(R.drawable.empty_offline),
    ORDERS(R.drawable.empty_orders),
    PROMOTIONS(R.drawable.empty_promotions),
    REVIEWS(R.drawable.empty_reviews),
    SEARCH(R.drawable.empty_search),
    VERIFICATION_PENDING(R.drawable.empty_verification_pending),
    VERIFICATION(R.drawable.empty_verification),
    VERIFY_EMAIL(R.drawable.empty_verify_email),
    VERIFY_IDENTITY(R.drawable.empty_verify_identity),
    WALLET(R.drawable.empty_wallet),
    WISHLIST(R.drawable.empty_wishlist)
}

@JsonClass(generateAdapter = true)
data class SellerSnippet(
    val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val username: String? = null,
    val email: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = false,
    @Json(name = "average_rating") val averageRating: Double? = null,
    @Json(name = "reviews_count") val reviewsCount: Int? = null,
    @Json(name = "streak_count") val streakCount: Int? = null,
    val campus: String? = null
)

@JsonClass(generateAdapter = true)
data class AdRef(
    val id: String,
    val status: String? = null,
    @Json(name = "ad_type") val adType: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null
)

@JsonClass(generateAdapter = true)
data class Product(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    val currency: String? = "GHS",
    val category: String? = null,
    val condition: String? = null,
    val campus: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    val images: List<String>? = null,
    @Json(name = "seller_id") val sellerId: String? = null,
    val status: String? = "Active",
    @Json(name = "stock_quantity") val stockQuantity: Int? = 1,
    @Json(name = "is_boosted") val isBoosted: Boolean? = false,
    @Json(name = "is_featured") val isFeatured: Boolean? = false,
    @Json(name = "views_count") val viewsCount: Int? = 0,
    @Json(name = "shares_count") val sharesCount: Int? = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "advertisement_id") val advertisementId: String? = null,
    @Json(name = "ad_type") val adType: String? = null,
    val seller: SellerSnippet? = null,
    val advertisements: List<AdRef>? = null
) {
    val primaryImage: String?
        get() = imageUrl?.takeIf { it.isNotBlank() } ?: images?.firstOrNull { it.isNotBlank() }

    val allImages: List<String>
        get() {
            val combined = buildList {
                imageUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                images?.filter { it.isNotBlank() }?.forEach { if (!contains(it)) add(it) }
            }
            return combined
        }
}

@JsonClass(generateAdapter = true)
data class ActiveAdvertisementRow(
    val id: String,
    @Json(name = "ad_type") val adType: String? = null,
    val status: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    val views: Int? = 0,
    val clicks: Int? = 0,
    val price: Double? = 0.0,
    @Json(name = "duration_days") val durationDays: Int? = null,
    @Json(name = "product_id") val productId: String? = null,
    val product: Product? = null
)

@JsonClass(generateAdapter = true)
data class MarketplaceProductsResponse(
    val products: List<Product> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class Profile(
    val id: String,
    @Json(name = "display_name") val displayName: String? = null,
    val username: String? = null,
    val email: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val campus: String? = null,
    val phone: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = false,
    @Json(name = "verification_status") val verificationStatus: String? = null,
    @Json(name = "is_admin") val isAdmin: Boolean? = false,
    val banned: Boolean? = false,
    @Json(name = "average_rating") val averageRating: Double? = 0.0,
    @Json(name = "reviews_count") val reviewsCount: Int? = 0,
    @Json(name = "free_listings_used") val freeListingsUsed: Int? = 0,
    @Json(name = "streak_count") val streakCount: Int? = 0,
    @Json(name = "instagram") val instagramUrl: String? = null,
    @Json(name = "snapchat") val snapchatUrl: String? = null,
    @Json(name = "whatsapp_number") val whatsappNumber: String? = null,
    @Json(name = "email_verified") val emailVerified: Boolean? = false,
    @Json(name = "phone_verified") val phoneVerified: Boolean? = false,
    @Json(name = "momo_number") val momoNumber: String? = null,
    @Json(name = "momo_network") val momoNetwork: String? = null,
    @Json(name = "bank_name") val bankName: String? = null,
    @Json(name = "bank_account_number") val bankAccountNumber: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val resolvedName: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")
            ?: "Kart User"
}

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    val user: AuthUser? = null,
    val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val msg: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderProduct(
    val id: String? = null,
    val title: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    val images: List<String>? = null
) {
    val primaryImage: String?
        get() = imageUrl?.takeIf { it.isNotBlank() } ?: images?.firstOrNull { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class Order(
    val id: String,
    @Json(name = "buyer_id") val buyerId: String? = null,
    @Json(name = "seller_id") val sellerId: String? = null,
    @Json(name = "product_id") val productId: String? = null,
    val quantity: Int? = 1,
    @Json(name = "unit_price") val unitPrice: Double? = 0.0,
    @Json(name = "total_amount") val totalAmount: Double? = 0.0,
    @Json(name = "platform_fee_total") val platformFeeTotal: Double? = 0.0,
    @Json(name = "seller_payout_amount") val sellerPayoutAmount: Double? = 0.0,
    val status: String? = "Pending",
    val currency: String? = "GHS",
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "payment_reference") val paymentReference: String? = null,
    @Json(name = "delivery_verified") val deliveryVerified: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null,
    val product: OrderProduct? = null,
    val seller: SellerSnippet? = null,
    val buyer: SellerSnippet? = null
)

@JsonClass(generateAdapter = true)
data class Review(
    val id: String,
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "seller_id") val sellerId: String? = null,
    @Json(name = "reviewer_id") val reviewerId: String? = null,
    @Json(name = "buyer_id") val buyerId: String? = null,
    @Json(name = "order_id") val orderId: String? = null,
    val rating: Int = 5,
    val comment: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val reviewer: SellerSnippet? = null
)

@JsonClass(generateAdapter = true)
data class WalletTransactionOrder(
    val id: String? = null,
    val product: OrderProduct? = null
)

@JsonClass(generateAdapter = true)
data class WalletTransaction(
    val id: String,
    @Json(name = "wallet_id") val walletId: String? = null,
    val amount: Double = 0.0,
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val order: WalletTransactionOrder? = null
)

@JsonClass(generateAdapter = true)
data class Wallet(
    val id: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    val balance: Double = 0.0,
    @Json(name = "pending_balance") val pendingBalance: Double = 0.0,
    @Json(name = "wallet_transactions") val transactions: List<WalletTransaction>? = null
)

@JsonClass(generateAdapter = true)
data class WishlistRow(
    val id: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "product_id") val productId: String,
    @Json(name = "created_at") val createdAt: String? = null,
    val product: Product? = null
)

@JsonClass(generateAdapter = true)
data class NotificationItem(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    val type: String? = null,
    val title: String? = null,
    val message: String? = null,
    val link: String? = null,
    @Json(name = "is_read") val isRead: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ConversationRow(
    val id: String,
    val participants: List<String> = emptyList(),
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageRow(
    val id: String? = null,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    val content: String = "",
    @Json(name = "is_read") val isRead: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
)

data class EnrichedConversation(
    val id: String,
    val otherUser: Profile,
    val product: Product? = null,
    val lastMessage: MessageRow? = null,
    val unreadCount: Int = 0,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionPlan(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    @Json(name = "duration_months") val durationMonths: Int = 1,
    val features: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class UserSubscription(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "plan_id") val planId: String? = null,
    val status: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val plan: SubscriptionPlan? = null
)

@JsonClass(generateAdapter = true)
data class PlatformSetting(
    val key: String,
    val value: Any? = null
)

@JsonClass(generateAdapter = true)
data class CampusRow(
    val id: String? = null,
    val name: String,
    val abbreviation: String? = null
)

@JsonClass(generateAdapter = true)
data class FollowRow(
    val id: String? = null,
    @Json(name = "follower_id") val followerId: String? = null,
    @Json(name = "following_id") val followingId: String? = null
)

@JsonClass(generateAdapter = true)
data class VerificationRequestRow(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    val status: String? = null,
    @Json(name = "id_image_url") val idImageUrl: String? = null,
    @Json(name = "selfie_image_url") val selfieImageUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ReportRow(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String? = null,
    @Json(name = "target_type") val targetType: String? = null,
    @Json(name = "target_id") val targetId: String? = null,
    val reason: String? = null,
    val details: String? = null,
    val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WithdrawalRequestRow(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    val amount: Double = 0.0,
    val status: String? = null,
    @Json(name = "payout_method") val payoutMethod: String? = null,
    @Json(name = "account_details") val accountDetails: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RefundRequestRow(
    val id: String,
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "buyer_id") val buyerId: String? = null,
    val reason: String? = null,
    val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class MarketplaceFilter(
    val query: String = "",
    val category: String = "",
    val condition: String = "",
    val campus: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val sort: String = "newest"
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            category.takeIf { it.isNotBlank() },
            condition.takeIf { it.isNotBlank() },
            if (minPrice.isNotBlank() || maxPrice.isNotBlank()) "price" else null,
            campus.takeIf { it.isNotBlank() },
            sort.takeIf { it.isNotBlank() && it != "newest" }
        ).size
}

data class UserSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}
