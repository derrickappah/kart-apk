package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartTopFourCategories
import com.example.domain.model.Product
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartCategoryTile
import com.example.ui.components.KartProductCard
import com.example.ui.components.KartSectionHeader
import com.example.ui.components.ProductCardSkeleton
import com.example.ui.components.PromotedBannerCarousel
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    wishlistIds: Set<String>,
    contentMaxWidth: Dp,
    topBar: @Composable (fillProgress: Float) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSeeAllCategories: () -> Unit,
    onExploreMarketplace: () -> Unit,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit,
    onInfoPageClick: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(state.error, state.feed != null) {
        if (state.error != null && state.feed != null) {
            onShowMessage(state.error ?: "Could not refresh listings.")
        }
    }
    val density = LocalDensity.current
    val fillStartPx = remember(density) { with(density) { 240.dp.toPx() } }
    val fillEndPx = remember(density) { with(density) { 310.dp.toPx() } }

    // Fluid density-aware fill synced to the 420dp hero exit zone (matches repo 280-300px threshold)
    val topBarFillProgress by remember(listState, fillStartPx, fillEndPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                ((listState.firstVisibleItemScrollOffset - fillStartPx) / (fillEndPx - fillStartPx))
                    .coerceIn(0f, 1f)
            }
        }
    }

    val isWide = contentMaxWidth >= 600.dp
    val gridColumns = if (contentMaxWidth >= 840.dp) 4 else if (isWide) 3 else 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.loadHome(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Promoted Banner Carousel
                item(key = "hero_banner") {
                    PromotedBannerCarousel(
                        products = state.feed?.heroProducts.orEmpty(),
                        onProductClick = { p ->
                            viewModel.trackAdClick(p.advertisementId)
                            onProductClick(p)
                        },
                        onAdView = { adId -> viewModel.trackAdView(adId) },
                        onExploreClick = onExploreMarketplace
                    )
                }

                // Shop by Category (2x2 on phones, 4-col on tablets matching app/page.js)
                item(key = "categories_header") {
                    KartSectionHeader(
                        title = "Shop by Category",
                        actionLabel = "See all",
                        onActionClick = onSeeAllCategories,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                item(key = "categories_grid") {
                    val rows = KartTopFourCategories.chunked(if (isWide) 4 else 2)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        rows.forEach { rowCats ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowCats.forEach { cat ->
                                    KartCategoryTile(
                                        category = cat,
                                        onClick = { onCategoryClick(cat.name) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Error State if initial load failed with no cached data
                if (state.error != null && state.feed == null) {
                    item(key = "home_error") {
                        EmptyStateView(
                            asset = EmptyStateAsset.OFFLINE,
                            title = "Could not load campus listings",
                            subtitle = state.error ?: "Check your connection and try again.",
                            actionLabel = "Retry",
                            onActionClick = { viewModel.loadHome() },
                            modifier = Modifier
                                .fillParentMaxHeight(0.58f)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    // Featured Horizontal Slider
                    item(key = "featured_header") {
                        KartSectionHeader(
                            title = "Featured Deals",
                            actionLabel = "View all",
                            onActionClick = onExploreMarketplace,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp)
                        )
                    }

                    item(key = "featured_slider") {
                        if (state.loading && state.feed == null) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(4) {
                                    ProductCardSkeleton(modifier = Modifier.width(164.dp))
                                }
                            }
                        } else {
                            val featured = state.feed?.featuredProducts.orEmpty()
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(featured, key = { "feat_${it.id}" }) { product ->
                                    KartProductCard(
                                        product = product,
                                        isWishlisted = product.id in wishlistIds,
                                        onProductClick = {
                                            viewModel.trackAdClick(it.advertisementId)
                                            onProductClick(it)
                                        },
                                        onWishlistToggle = onWishlistToggle,
                                        modifier = Modifier.width(166.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Latest on Campus Grid
                    item(key = "latest_header") {
                        KartSectionHeader(
                            title = "Recommended for you",
                            actionLabel = "Browse Shop",
                            onActionClick = onExploreMarketplace,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp)
                        )
                    }

                    if (state.loading && state.feed == null) {
                        items(3) { rowIdx ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                repeat(gridColumns) {
                                    ProductCardSkeleton(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        val latest = state.feed?.latestProducts.orEmpty()
                        if (latest.isEmpty()) {
                            item(key = "latest_empty") {
                                EmptyStateView(
                                    asset = EmptyStateAsset.LISTINGS,
                                    title = "No campus items yet",
                                    subtitle = "Be the first to list an item for students on your campus.",
                                    actionLabel = "Browse Shop",
                                    onActionClick = onExploreMarketplace,
                                    modifier = Modifier
                                        .fillParentMaxHeight(0.58f)
                                        .fillMaxWidth()
                                )
                            }
                        } else {
                            val chunked = latest.chunked(gridColumns)
                            items(chunked.size, key = { idx -> "latest_row_$idx" }) { rowIdx ->
                                val rowItems = chunked[rowIdx]
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    rowItems.forEach { product ->
                                        KartProductCard(
                                            product = product,
                                            isWishlisted = product.id in wishlistIds,
                                            onProductClick = {
                                                viewModel.trackAdClick(it.advertisementId)
                                                onProductClick(it)
                                            },
                                            onWishlistToggle = onWishlistToggle,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(gridColumns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            topBar(topBarFillProgress)
        }
    }
}
