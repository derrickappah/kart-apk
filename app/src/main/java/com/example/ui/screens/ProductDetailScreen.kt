package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.domain.model.Review
import com.example.domain.model.conditionOptionFor
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartProductCard
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.ReportDialog
import com.example.ui.components.ReviewSubmissionDialog
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StreakBadge
import com.example.ui.components.UserAvatar
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import com.example.ui.viewmodel.ProductDetailViewModel
import kotlinx.coroutines.launch

private val PageHorizontalPadding = 20.dp
private val SheetCornerRadius = 24.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    viewModel: ProductDetailViewModel,
    currentUserId: String?,
    wishlistIds: Set<String>,
    buyNowEnabled: Boolean = true,
    onRefreshPlatformFlags: () -> Unit = {},
    onBack: () -> Unit,
    onWishlistToggle: (String) -> Unit,
    onRequireAuth: () -> Unit,
    onOpenChat: (String) -> Unit,
    onBuyNow: (String) -> Unit,
    onSellerClick: (String) -> Unit,
    onProductClick: (Product) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors
    val scope = rememberCoroutineScope()
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(productId, currentUserId) {
        onRefreshPlatformFlags()
        viewModel.loadProduct(productId, currentUserId)
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            onShowMessage(it)
            viewModel.clearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.loading -> {
                ProductDetailLoadingSkeleton(onBack = onBack)
            }
            state.error != null || state.bundle == null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    KartSubScreenTopBar(title = "Listing", onBack = onBack)
                    EmptyStateView(
                        asset = EmptyStateAsset.ERROR,
                        title = "Listing unavailable",
                        subtitle = state.error ?: "This item may have been sold or removed.",
                        actionLabel = "Go Back",
                        onActionClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
            else -> {
                val bundle = state.bundle!!
                val product = bundle.product
                val sellerId = product.sellerId ?: bundle.seller?.id ?: product.seller?.id
                val isOwner = currentUserId != null && currentUserId == sellerId
                val images = product.allImages.ifEmpty { listOf("") }
                val pagerState = rememberPagerState(pageCount = { images.size })
                val isWishlisted = product.id in wishlistIds
                val listState = rememberLazyListState()
                val density = LocalDensity.current
                val collapseOffsetPx = remember(density) { with(density) { 260.dp.toPx() } }

                val isHeaderCollapsed by remember(listState, collapseOffsetPx) {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 ||
                            listState.firstVisibleItemScrollOffset > collapseOffsetPx
                    }
                }
                val headerProgress by animateFloatAsState(
                    targetValue = if (isHeaderCollapsed) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "detailHeaderCollapse"
                )

                val sellerName = bundle.seller?.resolvedName
                    ?: product.seller?.displayName
                    ?: "Campus Seller"
                val sellerAvatar = bundle.seller?.avatarUrl ?: product.seller?.avatarUrl
                val isVerified = bundle.seller?.isVerified == true || product.seller?.isVerified == true
                val sellerRating = bundle.seller?.averageRating ?: product.seller?.averageRating ?: 0.0
                val sellerStreak = bundle.seller?.streakCount ?: product.seller?.streakCount ?: 0
                val reviewCount = bundle.reviews.size
                val displayTitle = KartFormatters.toSentenceCase(product.title)

                // Compute reviews index dynamically for smooth jump from top rating row
                val reviewsItemIndex = remember(product.description, sellerId) {
                    // 0: gallery, 1: header_meta, 2: highlights, 3?: description, 4?: seller
                    var idx = 3
                    if (!product.description.isNullOrBlank()) idx += 1
                    if (!sellerId.isNullOrBlank()) idx += 1
                    idx
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 112.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("product_detail_list")
                ) {
                    // 0: Hero Product Gallery with Overlapping Rounded Sheet Cap
                    item(key = "gallery") {
                        ProductHeroGallery(
                            images = images,
                            title = displayTitle,
                            pagerState = pagerState,
                            scrollOffsetProvider = {
                                if (listState.firstVisibleItemIndex == 0) {
                                    listState.firstVisibleItemScrollOffset.toFloat()
                                } else {
                                    0f
                                }
                            }
                        )
                    }

                    // 1: Product Labels, Balanced Title, Price & Early Trust Signals
                    item(key = "header_meta") {
                        ProductHeaderSection(
                            product = product,
                            displayTitle = displayTitle,
                            sellerRating = sellerRating,
                            reviewCount = reviewCount,
                            onRatingClick = {
                                scope.launch {
                                    listState.animateScrollToItem(reviewsItemIndex)
                                }
                            }
                        )
                    }

                    // 2: Unified Calm Feature Highlights Row
                    item(key = "highlights") {
                        ProductHighlightsSection(
                            campus = product.campus ?: "On Campus",
                            condition = product.condition,
                            isVerifiedSeller = isVerified
                        )
                    }

                    // 3: Comfortable Description Paragraph
                    if (!product.description.isNullOrBlank()) {
                        item(key = "description") {
                            ProductDescriptionSection(description = product.description)
                        }
                    }

                    // 4: Calm Seller Profile Card
                    if (!sellerId.isNullOrBlank()) {
                        item(key = "seller_card") {
                            ProductSellerSection(
                                sellerName = sellerName,
                                sellerAvatar = sellerAvatar,
                                isVerified = isVerified,
                                rating = sellerRating,
                                streak = sellerStreak,
                                followerCount = bundle.followerCount,
                                isFollowing = bundle.isFollowingSeller,
                                isOwner = isOwner,
                                onSellerClick = { onSellerClick(sellerId) },
                                onFollowToggle = {
                                    if (currentUserId == null) onRequireAuth()
                                    else viewModel.toggleFollow(sellerId)
                                }
                            )
                        }
                    }

                    // 5: Buyer Reviews Section
                    item(key = "reviews_section") {
                        ProductReviewsSection(
                            reviews = bundle.reviews,
                            isOwner = isOwner,
                            canReview = bundle.canReview,
                            hasUserReview = bundle.hasUserReviewed,
                            onWriteReview = {
                                if (currentUserId == null) onRequireAuth()
                                else showReviewDialog = true
                            }
                        )
                    }

                    // 6: Similar Items Horizontal Rail
                    if (bundle.similarProducts.isNotEmpty()) {
                        item(key = "similar_items") {
                            ProductSimilarSection(
                                products = bundle.similarProducts,
                                wishlistIds = wishlistIds,
                                onProductClick = onProductClick,
                                onWishlistToggle = onWishlistToggle
                            )
                        }
                    }
                }

                // Pinned Dynamic Top Navigation Bar (high-contrast frosted pills -> solid title bar on scroll)
                ProductDetailTopBar(
                    title = displayTitle,
                    collapseProgress = headerProgress,
                    isWishlisted = isWishlisted,
                    onBack = onBack,
                    onShare = {
                        viewModel.trackShare(product.id)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out ${product.title} (${KartFormatters.formatCedis(product.price)}) on Kart: https://www.kart.cx/marketplace/${product.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share listing"))
                    },
                    onWishlistToggle = { onWishlistToggle(product.id) },
                    onReport = {
                        if (currentUserId == null) onRequireAuth()
                        else showReportDialog = true
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Sticky Bottom Action Bar (Chat + Buy now with live total)
                ProductStickyBottomBar(
                    priceText = KartFormatters.formatCedis(product.price),
                    isOwner = isOwner,
                    isOutOfStock = product.isOutOfStock,
                    isActive = product.isActiveListing,
                    buyNowEnabled = buyNowEnabled,
                    actionLoading = state.actionLoading,
                    onChatClick = {
                        if (currentUserId == null) onRequireAuth()
                        else if (!sellerId.isNullOrBlank()) {
                            viewModel.startChatWithSeller(currentUserId, sellerId, product.id, onOpenChat)
                        }
                    },
                    onBuyNowClick = {
                        if (!buyNowEnabled) {
                            onShowMessage("Buy Now is currently turned off. Please chat with the seller instead.")
                        } else if (currentUserId == null) {
                            onRequireAuth()
                        } else {
                            onBuyNow(product.id)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (showReviewDialog) {
                    ReviewSubmissionDialog(
                        title = bundle.seller?.resolvedName ?: "Seller",
                        isSubmitting = state.actionLoading,
                        onDismiss = { showReviewDialog = false },
                        onSubmit = { rating, formatted ->
                            showReviewDialog = false
                            viewModel.submitReview(
                                productId = product.id,
                                sellerId = sellerId,
                                rating = rating,
                                comment = formatted,
                                currentUserId = currentUserId,
                                orderId = bundle.verifiedBuyerOrderId
                            )
                        }
                    )
                }

                if (showReportDialog) {
                    ReportDialog(
                        targetLabel = "Listing",
                        onDismiss = { showReportDialog = false },
                        onSubmit = { reason, details ->
                            showReportDialog = false
                            viewModel.submitReport(product.id, reason, details)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductHeroGallery(
    images: List<String>,
    title: String,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scrollOffsetProvider: () -> Float
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds()
            .background(surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .zIndex(0f)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Subtle parallax strictly clipped inside the hero container
                        translationY = scrollOffsetProvider() * 0.25f
                        clip = true
                    }
            ) { page ->
                AsyncImage(
                    model = images[page].ifBlank { null },
                    contentDescription = title,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Subtle top protective gradient for system status bar readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Clean gallery indicator resting comfortably above the overlapping rounded sheet
        if (images.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(bottom = SheetCornerRadius + 12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = KartThemeTokens.colors.subtleDivider.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                if (images.size <= 6) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(images.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(width = if (isSelected) 16.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) KartThemeTokens.colors.textPrimary
                                        else KartThemeTokens.colors.textMuted.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${images.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = KartThemeTokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Solid opaque rounded-top sheet cap overlapping the bottom of the product hero image
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .height(SheetCornerRadius)
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        ) {}
    }
}

@Composable
private fun ProductDetailTopBar(
    title: String,
    collapseProgress: Float,
    isWishlisted: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onWishlistToggle: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    val surfaceBg = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceBg.copy(alpha = collapseProgress))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            HeaderActionButton(
                icon = KartLucideIcons.ArrowLeft,
                contentDescription = "Back",
                collapseProgress = collapseProgress,
                onClick = onBack,
                testTag = "product_back_btn"
            )

            // Sticky product title smoothly revealed as the hero scrolls away
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .alpha(collapseProgress)
                    .graphicsLayer {
                        translationY = (1f - collapseProgress) * 10f
                    }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderActionButton(
                    icon = KartLucideIcons.Share2,
                    contentDescription = "Share listing",
                    collapseProgress = collapseProgress,
                    onClick = onShare,
                    testTag = "product_share_btn"
                )
                HeaderActionButton(
                    icon = if (isWishlisted) KartLucideIcons.HeartFilled else KartLucideIcons.HeartOutline,
                    contentDescription = if (isWishlisted) "Remove from wishlist" else "Save to wishlist",
                    collapseProgress = collapseProgress,
                    tint = if (isWishlisted) KartDanger else tokens.textPrimary,
                    iconScale = if (isWishlisted) 1.10f else 1f,
                    onClick = onWishlistToggle,
                    testTag = "product_wishlist_btn"
                )
                HeaderActionButton(
                    icon = KartLucideIcons.Flag,
                    contentDescription = "Report listing",
                    collapseProgress = collapseProgress,
                    onClick = onReport,
                    testTag = "product_report_btn"
                )
            }
        }

        if (collapseProgress > 0.05f) {
            HorizontalDivider(
                thickness = 1.dp,
                color = tokens.subtleDivider.copy(alpha = collapseProgress * 0.8f)
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    collapseProgress: Float,
    onClick: () -> Unit,
    tint: Color = KartThemeTokens.colors.textPrimary,
    iconScale: Float = 1f,
    testTag: String? = null
) {
    val tokens = KartThemeTokens.colors
    val floatingAlpha = 1f - collapseProgress
    val pillBg = tokens.wishlistPillBg.copy(alpha = tokens.wishlistPillBg.alpha * floatingAlpha)
    val borderColor = tokens.wishlistPillBorder.copy(alpha = tokens.wishlistPillBorder.alpha * floatingAlpha)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "headerActionPressScale"
    )
    val animatedIconScale by animateFloatAsState(
        targetValue = iconScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "headerActionIconScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (floatingAlpha > 0.1f) {
                        Modifier.shadow(
                            elevation = (3f * floatingAlpha).dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.16f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(pillBg)
                .border(width = 1.dp, color = borderColor, shape = CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        scaleX = animatedIconScale
                        scaleY = animatedIconScale
                    }
            )
        }
    }
}

@Composable
private fun ProductHeaderSection(
    product: Product,
    displayTitle: String,
    sellerRating: Double,
    reviewCount: Int,
    onRatingClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
            .padding(top = 2.dp, bottom = 18.dp)
    ) {
        // Tracked uppercase category label + concise low-noise condition badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!product.category.isNullOrBlank()) {
                Text(
                    text = product.category.trim().uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = KartPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (!product.condition.isNullOrBlank()) {
                val conditionLabel = conditionOptionFor(product.condition)?.name ?: product.condition.trim()
                Spacer(Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.conditionTagBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = KartLucideIcons.conditionIcon(conditionLabel),
                        contentDescription = null,
                        tint = tokens.conditionTagText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Condition: $conditionLabel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.sp
                        ),
                        color = tokens.conditionTagText
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Balanced, readable product title (stands out clearly without shouting)
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = tokens.textPrimary
        )

        Spacer(Modifier.height(10.dp))

        // Clean prominent price (no redundant "Price:" label)
        Text(
            text = KartFormatters.formatCedis(product.price),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            ),
            color = tokens.textPrimary
        )

        Spacer(Modifier.height(10.dp))

        // Trust signals immediately next to title & price (rating, reviews, campus, listing time)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRatingClick)
                    .padding(vertical = 4.dp)
                    .testTag("product_rating_summary")
            ) {
                Icon(
                    imageVector = KartLucideIcons.StarFilled,
                    contentDescription = null,
                    tint = KartWarning,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (sellerRating > 0.0) String.format("%.1f", sellerRating) else "New",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = tokens.textPrimary
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = when {
                        reviewCount == 1 -> "(1 review)"
                        reviewCount > 1 -> "($reviewCount reviews)"
                        else -> "seller"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = tokens.textSecondary
                )
            }

            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = tokens.textMuted
            )

            Text(
                text = product.campus ?: "On Campus",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = tokens.textMuted
            )

            Text(
                text = KartFormatters.timeAgo(product.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = tokens.textMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProductHighlightsSection(
    campus: String,
    condition: String?,
    isVerifiedSeller: Boolean
) {
    val tokens = KartThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
    ) {
        HorizontalDivider(thickness = 1.dp, color = tokens.subtleDivider.copy(alpha = 0.65f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            HighlightItem(
                icon = KartLucideIcons.ShieldCheck,
                title = "Escrow",
                subtitle = "PIN protected",
                modifier = Modifier.weight(1f)
            )
            HighlightItem(
                icon = KartLucideIcons.MapPin,
                title = "Handoff",
                subtitle = campus,
                modifier = Modifier.weight(1f)
            )
            val condOption = conditionOptionFor(condition)
            HighlightItem(
                icon = if (isVerifiedSeller) KartLucideIcons.BadgeCheck else KartLucideIcons.conditionIcon(condition),
                title = if (isVerifiedSeller) "Verified" else (condOption?.name ?: condition ?: "Condition"),
                subtitle = if (isVerifiedSeller) "Campus seller" else (condOption?.description ?: "Inspected"),
                modifier = Modifier.weight(1f)
            )
        }

        // Calm concise escrow reassurance note (no heavy colored alert box)
        Text(
            text = "Payment stays safe in Kart Escrow until you inspect the item and confirm with your PIN.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            ),
            color = tokens.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = tokens.subtleDivider.copy(alpha = 0.65f))
    }
}

@Composable
private fun HighlightItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, tokens.cardBorder, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            ),
            color = tokens.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProductDescriptionSection(description: String) {
    val tokens = KartThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
            .padding(top = 18.dp)
    ) {
        Text(
            text = "About this item",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tokens.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description.trim(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal
            ),
            color = tokens.textSecondary
        )
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 1.dp, color = tokens.subtleDivider.copy(alpha = 0.65f))
    }
}

@Composable
private fun ProductSellerSection(
    sellerName: String,
    sellerAvatar: String?,
    isVerified: Boolean,
    rating: Double,
    streak: Int,
    followerCount: Int,
    isFollowing: Boolean,
    isOwner: Boolean,
    onSellerClick: () -> Unit,
    onFollowToggle: () -> Unit
) {
    val tokens = KartThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
            .padding(top = 18.dp)
    ) {
        Text(
            text = "Seller",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tokens.textPrimary
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, tokens.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onSellerClick)
                .testTag("product_seller_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                UserAvatar(
                    avatarUrl = sellerAvatar,
                    displayName = sellerName,
                    size = 46.dp,
                    isVerified = isVerified
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sellerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (streak > 0) {
                            Spacer(Modifier.width(6.dp))
                            StreakBadge(streak)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = KartLucideIcons.StarFilled,
                            contentDescription = null,
                            tint = KartWarning,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (rating > 0) String.format("%.1f", rating) else "New seller",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary
                        )
                        Text(
                            text = " · $followerCount followers",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (!isOwner) {
                    Surface(
                        onClick = onFollowToggle,
                        shape = RoundedCornerShape(999.dp),
                        color = if (isFollowing) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isFollowing) tokens.subtleDivider else KartPrimary
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .testTag("product_follow_btn")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFollowing) tokens.textPrimary else KartPrimary
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = KartLucideIcons.ChevronRight,
                        contentDescription = "View profile",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 1.dp, color = tokens.subtleDivider.copy(alpha = 0.65f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductReviewsSection(
    reviews: List<Review>,
    isOwner: Boolean,
    canReview: Boolean,
    hasUserReview: Boolean,
    onWriteReview: () -> Unit
) {
    val tokens = KartThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
            .padding(top = 18.dp, bottom = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reviews",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = tokens.textPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(${reviews.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
            }
            if (!isOwner && canReview && !hasUserReview) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onWriteReview)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("product_write_review_btn")
                ) {
                    Text(
                        text = "Write a review",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = KartPrimary
                    )
                }
            } else if (!isOwner && hasUserReview) {
                Text(
                    text = "Reviewed",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textMuted
                )
            }
        }

        if (!isOwner && !canReview && !hasUserReview) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Verified buyers can leave a review after delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )
        }

        Spacer(Modifier.height(10.dp))

        if (reviews.isEmpty()) {
            Text(
                text = "No reviews for this listing yet. Verified buyers can share feedback after completing their campus handoff.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                color = tokens.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reviews.take(5).forEach { review ->
                    val (commentBody, tags) = KartFormatters.parseReviewComment(review.comment)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = review.reviewer?.displayName
                                        ?: if (!review.orderId.isNullOrBlank()) "Verified buyer" else "Campus buyer",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(review.rating.coerceIn(1, 5)) {
                                        Icon(
                                            imageVector = KartLucideIcons.StarFilled,
                                            contentDescription = null,
                                            tint = KartWarning,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                            if (tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    tags.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, tokens.cardBorder, RoundedCornerShape(999.dp))
                                                .padding(horizontal = 9.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = tokens.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            if (commentBody.isNotBlank()) {
                                Text(
                                    text = commentBody,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSimilarSection(
    products: List<Product>,
    wishlistIds: Set<String>,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit
) {
    val tokens = KartThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = tokens.subtleDivider.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = PageHorizontalPadding)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Similar items",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tokens.textPrimary,
            modifier = Modifier.padding(horizontal = PageHorizontalPadding)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(products, key = { it.id }) { item ->
                KartProductCard(
                    product = item,
                    isWishlisted = item.id in wishlistIds,
                    onProductClick = onProductClick,
                    onWishlistToggle = onWishlistToggle,
                    modifier = Modifier.width(156.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductStickyBottomBar(
    priceText: String,
    isOwner: Boolean,
    isOutOfStock: Boolean,
    isActive: Boolean,
    buyNowEnabled: Boolean,
    actionLoading: Boolean,
    onChatClick: () -> Unit,
    onBuyNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    val canBuy = !isOutOfStock && isActive && buyNowEnabled

    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(thickness = 1.dp, color = tokens.subtleDivider.copy(alpha = 0.85f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = PageHorizontalPadding, vertical = 12.dp)
            ) {
                if (isOwner) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Your listing",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textSecondary
                            )
                        }
                    }
                } else if (!buyNowEnabled) {
                    Button(
                        onClick = onChatClick,
                        enabled = !actionLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KartPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = KartPrimary.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("product_chat_btn")
                    ) {
                        if (actionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = KartLucideIcons.MessageCircleActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = "Chat with Seller · $priceText",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onChatClick,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tokens.subtleDivider),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = tokens.textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(50.dp)
                            .testTag("product_chat_btn")
                    ) {
                        Icon(
                            imageVector = KartLucideIcons.MessageCircleActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onBuyNowClick,
                        enabled = !actionLoading && canBuy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KartPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = KartPrimary.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1.45f)
                            .height(50.dp)
                            .testTag("product_buy_now_btn")
                    ) {
                        if (actionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                isOutOfStock -> "Out of Stock"
                                !isActive -> "Listing Unavailable"
                                else -> "Buy now · $priceText"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailLoadingSkeleton(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SheetCornerRadius)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius))
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageHorizontalPadding, vertical = 6.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.28f).height(14.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.78f).height(24.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.36f).height(26.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.62f).height(16.dp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBox(modifier = Modifier.weight(1f).height(64.dp), cornerRadius = 12.dp)
                    ShimmerBox(modifier = Modifier.weight(1f).height(64.dp), cornerRadius = 12.dp)
                    ShimmerBox(modifier = Modifier.weight(1f).height(64.dp), cornerRadius = 12.dp)
                }
                Spacer(Modifier.height(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(88.dp), cornerRadius = 12.dp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            HeaderActionButton(
                icon = KartLucideIcons.ArrowLeft,
                contentDescription = "Back",
                collapseProgress = 0f,
                onClick = onBack
            )
        }
    }
}
