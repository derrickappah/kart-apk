package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
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
                    val tokens = com.example.ui.theme.KartThemeTokens.colors
                    val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
                    var isSearchFocused by remember { mutableStateOf(false) }
                    val filterInteraction = remember { MutableInteractionSource() }
                    val filterPressed by filterInteraction.collectIsPressedAsState()
                    val filterScale by animateFloatAsState(
                        targetValue = if (filterPressed) 0.93f else 1f,
                        animationSpec = tween(durationMillis = 120, easing = easeOutStrong),
                        label = "shopFilterScale"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSearchFocused) KartPrimary else tokens.cardBorder,
                        animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                        label = "shopSearchBorder"
                    )
                    val searchIconColor by animateColorAsState(
                        targetValue = if (isSearchFocused) KartPrimary else tokens.textMuted,
                        animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                        label = "shopSearchIcon"
                    )
                    val filterCount = state.filter.activeFilterCount
                    val hasFilters = filterCount > 0
                    val filterBg by animateColorAsState(
                        targetValue = if (hasFilters) KartPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                        label = "shopFilterBg"
                    )
                    val filterContentColor by animateColorAsState(
                        targetValue = if (hasFilters) Color.White else tokens.textPrimary,
                        animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                        label = "shopFilterContent"
                    )
                    val pillShape = RoundedCornerShape(26.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(52.dp)
                            .shadow(
                                elevation = if (isSearchFocused) 6.dp else 2.dp,
                                shape = pillShape,
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = Color.Black.copy(alpha = 0.12f)
                            )
                            .clip(pillShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isSearchFocused) 1.5.dp else 1.dp,
                                color = borderColor,
                                shape = pillShape
                            )
                            .padding(start = 16.dp, end = 6.dp)
                    ) {
                        Icon(
                            imageVector = KartLucideIcons.Search,
                            contentDescription = "Search",
                            tint = searchIconColor,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.filter.query.isEmpty()) {
                                Text(
                                    text = "Search campus listings...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textMuted,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = state.filter.query,
                                onValueChange = { viewModel.updateSearch(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = tokens.textPrimary
                                ),
                                cursorBrush = SolidColor(KartPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { viewModel.loadProducts(reset = true) }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isSearchFocused = it.isFocused }
                                    .testTag("marketplace_search_input")
                            )
                        }

                        AnimatedVisibility(
                            visible = state.filter.query.isNotEmpty(),
                            enter = fadeIn(tween(140, easing = easeOutStrong)) +
                                scaleIn(initialScale = 0.82f, animationSpec = tween(140, easing = easeOutStrong)),
                            exit = fadeOut(tween(110, easing = easeOutStrong)) +
                                scaleOut(targetScale = 0.82f, animationSpec = tween(110, easing = easeOutStrong))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.updateSearch("")
                                        viewModel.loadProducts(reset = true)
                                    }
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.X,
                                    contentDescription = "Clear search",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .graphicsLayer {
                                    scaleX = filterScale
                                    scaleY = filterScale
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(filterBg)
                                .clickable(
                                    interactionSource = filterInteraction,
                                    indication = null,
                                    onClick = { showFilterSheet = true }
                                )
                                .padding(horizontal = if (hasFilters) 12.dp else 11.dp)
                                .testTag("marketplace_filter_button")
                        ) {
                            Icon(
                                imageVector = KartLucideIcons.SlidersHorizontal,
                                contentDescription = "Filter and sort",
                                tint = filterContentColor,
                                modifier = Modifier.size(17.dp)
                            )
                            if (hasFilters) {
                                Text(
                                    text = "$filterCount",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = filterContentColor
                                )
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
