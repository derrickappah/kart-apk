package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartCategories
import com.example.domain.model.Product
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FilterBottomSheet
import com.example.ui.components.KartCategoryTile
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartProductCard
import com.example.ui.components.KartSearchBar
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.ProductCardSkeleton
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel,
    initialCategory: String?,
    wishlistIds: Set<String>,
    contentMaxWidth: Dp,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(initialCategory) {
        if (!initialCategory.isNullOrBlank() && state.filter.category != initialCategory) {
            viewModel.setCategory(initialCategory)
        }
    }

    val gridColumns = if (contentMaxWidth >= 840.dp) 4 else if (contentMaxWidth >= 600.dp) 3 else 2
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(
        listState,
        state.hasMore,
        state.loading,
        state.loadingMore,
        state.refreshing,
        state.error
    ) {
        derivedStateOf {
            if (!state.hasMore || state.loading || state.loadingMore || state.refreshing || state.error != null) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 2
            }
        }
    }

    LaunchedEffect(shouldLoadMore, state.products.size) {
        if (shouldLoadMore) {
            viewModel.loadProducts(reset = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.loadProducts(reset = true, isRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "search_and_controls") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        KartSearchBar(
                            query = state.filter.query,
                            onQueryChange = { viewModel.updateSearch(it) },
                            onSearch = { viewModel.loadProducts(reset = true) },
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            Surface(
                                onClick = { showFilterSheet = true },
                                shape = RoundedCornerShape(14.dp),
                                color = KartPrimary,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("marketplace_filter_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = KartLucideIcons.SlidersHorizontal,
                                        contentDescription = "Filter and sort",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            val count = state.filter.activeFilterCount
                            if (count > 0) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 5.dp, y = (-5).dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.5.dp, KartPrimary, CircleShape)
                                ) {
                                    Text(
                                        text = "$count",
                                        color = KartPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Category Pill Strip
                item(key = "quick_category_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            KartChip(
                                label = "All",
                                selected = state.filter.category.isBlank(),
                                onClick = { viewModel.setCategory("") }
                            )
                        }
                        items(KartCategories, key = { it.name }) { cat ->
                            KartChip(
                                label = cat.name,
                                selected = state.filter.category == cat.name,
                                onClick = {
                                    viewModel.setCategory(if (state.filter.category == cat.name) "" else cat.name)
                                }
                            )
                        }
                    }
                }

                // Content Grid / Skeleton / Empty
                when {
                    state.loading && state.products.isEmpty() -> {
                        items(4) {
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
                    }
                    state.error != null && state.products.isEmpty() -> {
                        item(key = "mp_error") {
                            EmptyStateView(
                                asset = EmptyStateAsset.ERROR,
                                title = "Couldn't load marketplace",
                                subtitle = state.error ?: "Something went wrong.",
                                actionLabel = "Try Again",
                                onActionClick = { viewModel.loadProducts(reset = true) },
                                modifier = Modifier
                                    .fillParentMaxHeight(0.75f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                    state.products.isEmpty() -> {
                        item(key = "mp_empty") {
                            EmptyStateView(
                                asset = EmptyStateAsset.SEARCH,
                                title = "No matching items found",
                                subtitle = "Try adjusting your search keywords, price range, or campus filter.",
                                actionLabel = "Reset Filters",
                                onActionClick = { viewModel.resetFilters() },
                                modifier = Modifier
                                    .fillParentMaxHeight(0.75f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        val rows = state.products.chunked(gridColumns)
                        items(rows.size, key = { idx -> "mp_row_$idx" }) { rowIdx ->
                            val rowItems = rows[rowIdx]
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
                                        onProductClick = onProductClick,
                                        onWishlistToggle = onWishlistToggle,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        if (state.loadingMore) {
                            item(key = "loading_more_skeleton") {
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
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = state.filter,
            campuses = state.campuses,
            onDismiss = { showFilterSheet = false },
            onApply = { viewModel.applyFilter(it) }
        )
    }
}

@Composable
fun CategoriesScreen(
    contentMaxWidth: Dp,
    onBack: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val tokens = KartThemeTokens.colors
    val cols = if (contentMaxWidth >= 600.dp) 3 else 2
    val rows = KartCategories.chunked(cols)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "All Categories", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Explore all ${KartCategories.size} student categories across campus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(rows.size) { idx ->
                val row = rows[idx]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { cat ->
                        KartCategoryTile(
                            category = cat,
                            onClick = { onCategorySelected(cat.name) },
                            height = 122.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(cols - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
