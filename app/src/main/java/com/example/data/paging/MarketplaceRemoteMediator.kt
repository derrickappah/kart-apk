package com.example.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.data.local.KartDatabase
import com.example.data.local.ProductEntity
import com.example.data.local.RemoteKeyEntity
import com.example.data.remote.KartCursorApi
import com.example.domain.model.KartFormatters
import com.example.domain.model.MarketplaceFilter
import java.util.concurrent.TimeUnit

data class MarketplaceFilterSpec(
    val search: String? = null,
    val campus: String? = null,
    val category: String? = null,
    val condition: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: String = "newest"
) {
    fun cacheKey(): String = listOf(
        search.orEmpty(),
        campus.orEmpty(),
        category.orEmpty(),
        condition.orEmpty(),
        minPrice?.toString().orEmpty(),
        maxPrice?.toString().orEmpty(),
        sort
    ).joinToString("|")

    fun feedKey(): String = "products:${cacheKey()}"
}

fun MarketplaceFilter.toFilterSpec(): MarketplaceFilterSpec = MarketplaceFilterSpec(
    search = query.trim().takeIf { it.isNotEmpty() },
    campus = campus.trim().takeIf { it.isNotEmpty() },
    category = category.trim().takeIf { it.isNotEmpty() },
    condition = condition.trim().takeIf { it.isNotEmpty() },
    minPrice = minPrice.trim().toDoubleOrNull(),
    maxPrice = maxPrice.trim().toDoubleOrNull(),
    sort = sort.trim().ifEmpty { "newest" }
)

@OptIn(ExperimentalPagingApi::class)
class MarketplaceRemoteMediator(
    private val filter: MarketplaceFilterSpec,
    private val database: KartDatabase,
    private val api: KartCursorApi,
    private val cacheTtlMs: Long = TimeUnit.SECONDS.toMillis(90),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : RemoteMediator<Int, ProductEntity>() {

    private val feedKey = filter.feedKey()

    override suspend fun initialize(): InitializeAction {
        val remoteKey = database.remoteKeyDao().getByFeedKey(feedKey)
            ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val isFresh = nowMs() - remoteKey.updatedAtMs < cacheTtlMs
        val hasCachedRows = database.productDao().countByFeedKey(feedKey) > 0
        return if (isFresh && hasCachedRows) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ProductEntity>
    ): MediatorResult {
        return try {
            val cursor: String? = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val key = database.remoteKeyDao().getByFeedKey(feedKey)
                    if (key == null || !key.hasMore || key.nextCursor == null) {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }
                    key.nextCursor
                }
            }

            val pageSize = state.config.pageSize.coerceIn(10, 40)
            val response = api.getMarketplaceProducts(
                cursor = cursor,
                limit = pageSize,
                search = filter.search,
                campus = filter.campus,
                category = filter.category,
                condition = filter.condition,
                minPrice = filter.minPrice,
                maxPrice = filter.maxPrice,
                sort = filter.sort
            )

            if (!response.error.isNullOrBlank()) {
                return MediatorResult.Error(IllegalStateException(response.error))
            }

            val now = nowMs()
            database.withTransaction {
                val baseIndex = if (loadType == LoadType.REFRESH) {
                    database.productDao().clearByFeedKey(feedKey)
                    database.remoteKeyDao().clearByFeedKey(feedKey)
                    0
                } else {
                    database.productDao().countByFeedKey(feedKey)
                }

                val entities = response.products
                    .distinctBy { it.id }
                    .mapIndexed { idx, dto ->
                        val rawThumb = dto.imageUrl?.takeIf { it.isNotBlank() }
                            ?: dto.images?.firstOrNull { it.isNotBlank() }
                        val optimizedThumb = rawThumb?.let { url ->
                            KartFormatters.buildOptimizedImageUrl(url, KartFormatters.ImageVariant.CARD)
                                .ifBlank { url }
                        }
                        ProductEntity(
                            id = dto.id,
                            feedKey = feedKey,
                            positionIndex = baseIndex + idx,
                            title = dto.title,
                            price = dto.price,
                            currency = dto.currency?.takeIf { it.isNotBlank() } ?: "GHS",
                            imageUrl = optimizedThumb,
                            campus = dto.campus,
                            category = dto.category,
                            condition = dto.condition,
                            sellerId = dto.sellerId,
                            status = dto.status?.takeIf { it.isNotBlank() } ?: "Active",
                            stockQuantity = dto.stockQuantity ?: 1,
                            isBoosted = dto.isBoosted == true,
                            isFeatured = dto.isFeatured == true,
                            viewsCount = dto.viewsCount ?: 0,
                            advertisementId = dto.advertisementId,
                            adType = dto.adType,
                            createdAt = dto.createdAt.orEmpty(),
                            cachedAtMs = now
                        )
                    }

                if (loadType == LoadType.REFRESH) {
                    database.productDao().insertAll(entities)
                } else {
                    database.productDao().insertIgnore(entities)
                }

                database.remoteKeyDao().upsert(
                    RemoteKeyEntity(
                        feedKey = feedKey,
                        nextCursor = response.nextCursor,
                        hasMore = response.hasMore,
                        updatedAtMs = now
                    )
                )
            }

            MediatorResult.Success(
                endOfPaginationReached = !response.hasMore || response.nextCursor == null
            )
        } catch (t: Throwable) {
            MediatorResult.Error(t)
        }
    }
}
