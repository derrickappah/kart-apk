package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.CategoryItem
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.ui.theme.KartConditionOverlay
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartHeroCtaText
import com.example.ui.theme.KartHeroGold
import com.example.ui.theme.KartHeroNavyDeep
import com.example.ui.theme.KartHeroNavyMid
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KartProductCard(
    product: Product,
    isWishlisted: Boolean,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onProductClick(product) }
            .testTag("product_card_${product.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
        ) {
            val cardImageModel = remember(product.primaryImage) {
                product.primaryImage
                    ?.let { KartFormatters.buildOptimizedImageUrl(it, KartFormatters.ImageVariant.CARD) }
                    ?.ifBlank { null }
            }
            AsyncImage(
                model = cardImageModel,
                contentDescription = product.title,
                placeholder = painterResource(R.drawable.img_placeholder),
                error = painterResource(R.drawable.img_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Condition badge (bottom-left matching repo MarketplaceGrid & FeaturedSlider)
            if (!product.condition.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KartConditionOverlay)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = product.condition.trim().uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }
            }

            // Wishlist button (top-right matching WishlistButton.js: bg-white/80 dark:bg-black/40, active:scale-90)
            KartWishlistButton(
                isWishlisted = isWishlisted,
                onClick = { onWishlistToggle(product.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .testTag("wishlist_btn_${product.id}")
            )
        }

        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = KartFormatters.toSentenceCase(product.title),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = KartFormatters.formatCedis(product.price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = KartPrimary
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = KartLucideIcons.MapPin,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = KartFormatters.abbreviateCampus(product.campus).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Replicates Kart web repo WishlistButton.js:
 * - Visual 36.dp frosted circular pill (bg-white/80 in Light, bg-black/40 in Dark) centered in a 48.dp touch target
 * - Tactile active:scale-90 press response + heart fill transition in red-500 when saved
 */
@Composable
fun KartWishlistButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wishlistButtonPressScale"
    )
    val heartScale by animateFloatAsState(
        targetValue = if (isWishlisted) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "wishlistHeartScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .clip(CircleShape)
                .background(tokens.wishlistPillBg)
                .border(0.75.dp, tokens.wishlistPillBorder, CircleShape)
        ) {
            Icon(
                imageVector = if (isWishlisted) KartLucideIcons.HeartFilled else KartLucideIcons.HeartOutline,
                contentDescription = if (isWishlisted) "Remove from wishlist" else "Save to wishlist",
                tint = if (isWishlisted) KartDanger else tokens.wishlistIconInactive,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
            )
        }
    }
}

@Composable
fun KartCategoryTile(
    category: CategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 116.dp
) {
    val tokens = KartThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.categoryTile)
            .border(1.dp, tokens.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("category_tile_${category.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.64f)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = tokens.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Image(
            painter = painterResource(category.imageRes),
            contentDescription = category.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(76.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
        )
    }
}

internal const val HERO_INFINITE_VIRTUAL_PAGE_COUNT = 100_000

internal fun heroStartPage(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val mid = HERO_INFINITE_VIRTUAL_PAGE_COUNT / 2
    return mid - (mid % itemCount)
}

internal fun heroActualIndex(virtualPage: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return virtualPage.mod(itemCount)
}

internal fun heroShortestTargetPage(
    currentPage: Int,
    targetIndex: Int,
    itemCount: Int,
    virtualPageCount: Int
): Int {
    if (itemCount <= 1 || virtualPageCount <= 1) return 0
    val safeTarget = targetIndex.mod(itemCount)
    val currentIdx = currentPage.mod(itemCount)
    val forwardDelta = (safeTarget - currentIdx + itemCount) % itemCount
    val backwardDelta = forwardDelta - itemCount
    val shortestDelta = if (forwardDelta <= -backwardDelta) forwardDelta else backwardDelta
    return (currentPage + shortestDelta).coerceIn(0, virtualPageCount - 1)
}

@Composable
fun PromotedBannerCarousel(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onAdView: (String?) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        // Editorial fallback matching PromotedBanner.js
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(KartHeroNavyDeep, KartHeroNavyMid, KartPrimary.copy(alpha = 0.40f))
                    )
                )
                .padding(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 28.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Buy & sell effortlessly across campus",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = Offset(0f, 3f),
                            blurRadius = 8f
                        )
                    ),
                    color = Color.White
                )
                Text(
                    text = "Discover textbooks, dorm essentials, electronics, and student deals nearby.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = onExploreClick,
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = KartHeroCtaText,
                    shadowElevation = 6.dp,
                    modifier = Modifier.testTag("hero_explore_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "BROWSE MARKETPLACE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = KartHeroCtaText
                        )
                        Icon(
                            imageVector = KartLucideIcons.ArrowRight,
                            contentDescription = null,
                            tint = KartHeroCtaText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
        return
    }

    val count = products.size
    val isInfinite = count > 1
    val virtualPageCount = if (isInfinite) HERO_INFINITE_VIRTUAL_PAGE_COUNT else count
    val startPage = heroStartPage(count)
    val scope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition(label = "arrowNudge")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nudgeX"
    )

    key(count) {
        val pagerState = rememberPagerState(
            initialPage = startPage,
            pageCount = { virtualPageCount }
        )

        // Auto-advance every 4.5s matching PromotedBanner.js, pausing while user drags
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, products) {
            val activeIndex = heroActualIndex(pagerState.currentPage, count)
            val active = products.getOrNull(activeIndex)
            onAdView(active?.advertisementId)
            if (isInfinite && !pagerState.isScrollInProgress) {
                if (pagerState.currentPage <= count || pagerState.currentPage >= virtualPageCount - count - 1) {
                    pagerState.scrollToPage(startPage + activeIndex)
                }
                delay(4500L)
                val nextPage = pagerState.currentPage + 1
                if (nextPage < virtualPageCount) {
                    pagerState.animateScrollToPage(nextPage)
                } else {
                    pagerState.scrollToPage(startPage)
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(420.dp)
                .clipToBounds()
                .background(KartHeroNavyDeep)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val actualIndex = heroActualIndex(page, count)
                val item = products[actualIndex]
            val isCurrent = pagerState.currentPage == page
            val imageScale by animateFloatAsState(
                targetValue = if (isCurrent) 1.05f else 1f,
                animationSpec = tween(durationMillis = 5000, easing = FastOutSlowInEasing),
                label = "heroImageKenBurns"
            )
            val priceDigits = remember(item.price) {
                KartFormatters.formatCedis(item.price).removePrefix("₵").trim()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .clickable { onProductClick(item) }
                    .testTag("hero_slide_${item.id}")
            ) {
                val bannerImageModel = remember(item.primaryImage) {
                    item.primaryImage
                        ?.let { KartFormatters.buildOptimizedImageUrl(it, KartFormatters.ImageVariant.DETAIL) }
                        ?.ifBlank { null }
                }
                AsyncImage(
                    model = bannerImageModel,
                    contentDescription = item.title,
                    placeholder = painterResource(R.drawable.img_placeholder),
                    error = painterResource(R.drawable.img_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = imageScale
                            scaleY = imageScale
                        }
                )

                // Top protective scrim for transparent header icons (h-20 from-black/40 to-transparent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom protective scrim for title, price & CTA (h-36 from-black/75 via-black/30 to-transparent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Black.copy(alpha = 0.30f),
                                1f to Color.Black.copy(alpha = 0.75f)
                            )
                        )
                )

                // Clean compact overlay: price + strikethrough original price (10-25% off) & compact VIEW DEAL pill
                val discountPct = remember(item.id) { (kotlin.math.abs(item.id.hashCode()) % 16) + 10 }
                val originalPrice = remember(item.price, discountPct) {
                    item.price / (1.0 - (discountPct / 100.0))
                }
                val originalPriceText = remember(originalPrice) {
                    KartFormatters.formatCedis(originalPrice)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "₵",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f
                                    )
                                ),
                                color = KartHeroGold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = priceDigits,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.3).sp,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.70f),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 5f
                                    )
                                ),
                                color = Color.White
                            )
                        }

                        if (item.price > 0.0) {
                            Text(
                                text = originalPriceText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    HeroViewDealButton(
                        arrowOffsetDp = arrowOffset,
                        onClick = { onProductClick(item) }
                    )
                }
            }
        }

        // Centered dot-to-pill slide indicators matching PromotedBanner.js (bottom-2.5, h-1.5, active w-6)
        if (count > 1) {
            val currentActualIndex = heroActualIndex(pagerState.currentPage, count)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .testTag("hero_carousel_indicators")
            ) {
                repeat(count) { index ->
                    val isSelected = currentActualIndex == index
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 6.dp,
                        animationSpec = tween(durationMillis = 300),
                        label = "heroIndicatorWidth"
                    )
                    val indicatorColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.40f),
                        animationSpec = tween(durationMillis = 300),
                        label = "heroIndicatorColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(indicatorWidth)
                            .clip(CircleShape)
                            .background(indicatorColor)
                            .clickable {
                                val targetPage = heroShortestTargetPage(
                                    currentPage = pagerState.currentPage,
                                    targetIndex = index,
                                    itemCount = count,
                                    virtualPageCount = virtualPageCount
                                )
                                scope.launch { pagerState.animateScrollToPage(targetPage) }
                            }
                            .testTag("hero_indicator_$index")
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun HeroViewDealButton(
    arrowOffsetDp: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroViewDealScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = Color.White,
        contentColor = KartHeroCtaText,
        shadowElevation = 6.dp,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Text(
                text = "VIEW DEAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                ),
                color = KartHeroCtaText
            )
            Icon(
                imageVector = KartLucideIcons.ArrowRight,
                contentDescription = null,
                tint = KartHeroCtaText,
                modifier = Modifier
                    .size(12.dp)
                    .offset(x = arrowOffsetDp.dp)
            )
        }
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp = 44.dp,
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) {
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "K" }

    Box(modifier = modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(KartPrimary.copy(alpha = 0.16f))
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KartPrimary
            )
            val resolvedAvatarUrl = avatarUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { url ->
                    val normalized = if (url.contains("api.dicebear.com")) {
                        url.replace("/svg?", "/png?").replace(Regex("/svg$"), "/png")
                    } else url
                    KartFormatters.buildOptimizedImageUrl(normalized, KartFormatters.ImageVariant.AVATAR)
                        .ifBlank { null }
                }
            if (resolvedAvatarUrl != null) {
                AsyncImage(
                    model = resolvedAvatarUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isVerified) {
            Icon(
                imageVector = KartLucideIcons.BadgeCheck,
                contentDescription = "Verified campus student",
                tint = KartPrimary,
                modifier = Modifier
                    .size((size.value * 0.36f).dp.coerceAtLeast(14.dp))
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun StreakBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(KartWarning.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = KartLucideIcons.Flame,
            contentDescription = "Streak",
            tint = KartWarning,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "${count}d streak",
            style = MaterialTheme.typography.labelSmall,
            color = KartWarning,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStateView(
    asset: EmptyStateAsset,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Image(
            painter = painterResource(asset.drawableRes),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(168.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.height(20.dp))
            KartPrimaryButton(text = actionLabel, onClick = onActionClick)
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val tokens = KartThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(tokens.shimmerBase, tokens.shimmerHighlight, tokens.shimmerBase),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f)
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f),
            cornerRadius = 12.dp
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp), cornerRadius = 6.dp)
        Spacer(Modifier.height(6.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(16.dp), cornerRadius = 6.dp)
        Spacer(Modifier.height(6.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(11.dp), cornerRadius = 6.dp)
    }
}
