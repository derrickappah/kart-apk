package com.example.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.model.Product

@Entity(
    tableName = "marketplace_products",
    primaryKeys = ["feedKey", "id"],
    indices = [Index(value = ["feedKey", "positionIndex"])]
)
data class ProductEntity(
    val id: String,
    val feedKey: String,
    val positionIndex: Int,
    val title: String,
    val price: Double,
    val currency: String = "GHS",
    val imageUrl: String?,
    val campus: String?,
    val category: String?,
    val condition: String?,
    val sellerId: String?,
    val status: String = "Active",
    val stockQuantity: Int = 1,
    val isBoosted: Boolean,
    val isFeatured: Boolean,
    val viewsCount: Int,
    val advertisementId: String? = null,
    val adType: String? = null,
    val createdAt: String,
    val cachedAtMs: Long
) {
    fun toDomainProduct(): Product = Product(
        id = id,
        title = title,
        price = price,
        currency = currency,
        category = category,
        condition = condition,
        campus = campus,
        imageUrl = imageUrl,
        images = imageUrl?.let { listOf(it) },
        sellerId = sellerId,
        status = status,
        stockQuantity = stockQuantity,
        isBoosted = isBoosted,
        isFeatured = isFeatured,
        viewsCount = viewsCount,
        createdAt = createdAt,
        advertisementId = advertisementId,
        adType = adType
    )
}

@Entity(
    tableName = "user_orders",
    indices = [Index(value = ["role", "createdAt"])]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val role: String, // "buyer" or "seller"
    val productId: String?,
    val productTitle: String?,
    val productImageUrl: String?,
    val totalAmount: Double,
    val status: String,
    val escrowStatus: String?,
    val createdAt: String
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["conversationId", "createdAtMs", "id"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val isRead: Boolean,
    val createdAt: String,
    val createdAtMs: Long
)

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val feedKey: String,
    val nextCursor: String?,
    val hasMore: Boolean,
    val updatedAtMs: Long
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM marketplace_products WHERE feedKey = :feedKey ORDER BY positionIndex ASC")
    fun pagingSource(feedKey: String): PagingSource<Int, ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ProductEntity>)

    @Query("DELETE FROM marketplace_products WHERE feedKey = :feedKey")
    suspend fun clearByFeedKey(feedKey: String)

    @Query("SELECT COUNT(*) FROM marketplace_products WHERE feedKey = :feedKey")
    suspend fun countByFeedKey(feedKey: String): Int
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM user_orders WHERE role = :role ORDER BY createdAt DESC")
    fun pagingSource(role: String): PagingSource<Int, OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OrderEntity>)

    @Query("DELETE FROM user_orders WHERE role = :role")
    suspend fun clearByRole(role: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAtMs ASC, id ASC")
    fun pagingSource(conversationId: String): PagingSource<Int, ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE feedKey = :feedKey LIMIT 1")
    suspend fun getByFeedKey(feedKey: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE feedKey = :feedKey")
    suspend fun clearByFeedKey(feedKey: String)
}

@Database(
    entities = [
        ProductEntity::class,
        OrderEntity::class,
        ChatMessageEntity::class,
        RemoteKeyEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KartDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun remoteKeyDao(): RemoteKeyDao

    companion object {
        fun build(context: Context): KartDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KartDatabase::class.java,
                "kart_local.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
