package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.KartCategories
import com.example.domain.model.KartConditionOptions
import com.example.domain.model.KartFormatters
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.KartSortOptions
import com.example.domain.model.MarketplaceFilter
import com.example.ui.theme.KartConnectivityOfflineDark
import com.example.ui.theme.KartConnectivityOfflineLight
import com.example.ui.theme.KartConnectivityOnline
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import kotlinx.coroutines.delay

data class BottomTabItem(
    val route: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val testTag: String
)

val KartBottomTabs = listOf(
    BottomTabItem("home", "Home", KartLucideIcons.HomeActive, KartLucideIcons.HomeInactive, "nav_tab_home"),
    BottomTabItem("marketplace", "Shop", KartLucideIcons.StoreActive, KartLucideIcons.StoreInactive, "nav_tab_shop"),
    BottomTabItem("seller_create", "Add", KartLucideIcons.PlusCircleActive, KartLucideIcons.PlusCircleInactive, "nav_tab_add"),
    BottomTabItem("messages", "Chat", KartLucideIcons.MessageCircleActive, KartLucideIcons.MessageCircleInactive, "nav_tab_chat"),
    BottomTabItem("profile", "Profile", KartLucideIcons.UserCircleActive, KartLucideIcons.UserCircleInactive, "nav_tab_profile")
)

@Composable
fun KartTopBar(
    isTransparent: Boolean,
    isLoggedIn: Boolean,
    unreadNotifications: Int,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillProgress: Float? = null
) {
    val tokens = KartThemeTokens.colors
    val animatedFill by animateFloatAsState(
        targetValue = if (isTransparent) 0f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "navBackdropFill"
    )
    val progress = (fillProgress ?: animatedFill).coerceIn(0f, 1f)

    val iconColor = lerp(Color.White, tokens.textPrimary, progress)
    val badgeRingColor = lerp(
        Color.Black.copy(alpha = 0.50f),
        MaterialTheme.colorScheme.background,
        progress
    )
    val signInBg = lerp(Color.White, MaterialTheme.colorScheme.onBackground, progress)
    val signInContent = lerp(Color(0xFF111827), MaterialTheme.colorScheme.background, progress)

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Dedicated GPU-composited solid backdrop layer with subtle shadow and bottom divider
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = progress
                }
                .shadow(
                    elevation = 2.dp,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalDivider(
                color = tokens.cardBorder,
                thickness = 1.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLogoClick)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("navbar_logo")
                ) {
                    Image(
                        painter = painterResource(R.drawable.kart_logo),
                        contentDescription = "Kart logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(28.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isLoggedIn) {
                        Box {
                            IconButton(
                                onClick = onNotificationsClick,
                                modifier = Modifier.testTag("navbar_notifications_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (progress < 0.99f) {
                                        Icon(
                                            imageVector = KartLucideIcons.Bell,
                                            contentDescription = null,
                                            tint = Color.Black.copy(alpha = 0.42f * (1f - progress)),
                                            modifier = Modifier
                                                .offset(y = 1.dp)
                                                .size(26.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = KartLucideIcons.Bell,
                                        contentDescription = "Notifications",
                                        tint = iconColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            if (unreadNotifications > 0) {
                                val badgeShape = RoundedCornerShape(999.dp)
                                val badgeLabel = if (unreadNotifications > 99) "99+" else "$unreadNotifications"
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 6.dp)
                                        .shadow(
                                            elevation = 2.dp,
                                            shape = badgeShape,
                                            ambientColor = Color.Black.copy(alpha = 0.25f),
                                            spotColor = Color.Black.copy(alpha = 0.30f)
                                        )
                                        .clip(badgeShape)
                                        .background(KartDanger)
                                        .border(1.5.dp, badgeRingColor, badgeShape)
                                        .height(18.dp)
                                        .padding(horizontal = if (badgeLabel.length > 1) 5.dp else 0.dp)
                                        .then(
                                            if (badgeLabel.length == 1) Modifier.width(18.dp) else Modifier
                                        )
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        color = Color.White,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 10.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = onSignInClick,
                            shape = RoundedCornerShape(10.dp),
                            color = signInBg,
                            contentColor = signInContent,
                            modifier = Modifier.testTag("navbar_signin_btn")
                        ) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KartSubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val tokens = KartThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("subscreen_back_btn")
            ) {
                Icon(
                    imageVector = KartLucideIcons.ArrowLeft,
                    contentDescription = "Navigate back",
                    tint = tokens.textPrimary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            actions()
        }
        HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)
    }
}

enum class ConnectivityBannerState {
    HIDDEN,
    OFFLINE,
    BACK_ONLINE
}

@Composable
fun rememberConnectivityBannerState(
    isOnline: Boolean,
    backOnlineDurationMs: Long = 2_500L
): ConnectivityBannerState {
    var hasBeenOffline by rememberSaveable { mutableStateOf(!isOnline) }
    var bannerState by rememberSaveable {
        mutableStateOf(
            if (isOnline) ConnectivityBannerState.HIDDEN else ConnectivityBannerState.OFFLINE
        )
    }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            hasBeenOffline = true
            bannerState = ConnectivityBannerState.OFFLINE
        } else if (hasBeenOffline) {
            bannerState = ConnectivityBannerState.BACK_ONLINE
            delay(backOnlineDurationMs)
            hasBeenOffline = false
            bannerState = ConnectivityBannerState.HIDDEN
        } else {
            bannerState = ConnectivityBannerState.HIDDEN
        }
    }

    return bannerState
}

@Composable
fun KartBottomChromeDock(
    showBottomBar: Boolean,
    currentRoute: String,
    unreadMessages: Int,
    onTabSelected: (String) -> Unit,
    bannerState: ConnectivityBannerState,
    bannerVisibilityState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    var lastVisibleState by remember {
        mutableStateOf(
            if (bannerState != ConnectivityBannerState.HIDDEN) bannerState
            else ConnectivityBannerState.OFFLINE
        )
    }
    if (bannerState != ConnectivityBannerState.HIDDEN) {
        lastVisibleState = bannerState
    }

    val offlineBgColor = if (tokens.isDark) KartConnectivityOfflineDark else KartConnectivityOfflineLight
    val displayedTargetColor = when (lastVisibleState) {
        ConnectivityBannerState.BACK_ONLINE -> KartConnectivityOnline
        ConnectivityBannerState.OFFLINE,
        ConnectivityBannerState.HIDDEN -> offlineBgColor
    }
    val displayedBgColor by animateColorAsState(
        targetValue = displayedTargetColor,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "connectivityBannerBg"
    )

    val insetTargetColor = when (bannerState) {
        ConnectivityBannerState.OFFLINE -> offlineBgColor
        ConnectivityBannerState.BACK_ONLINE -> KartConnectivityOnline
        ConnectivityBannerState.HIDDEN -> MaterialTheme.colorScheme.background
    }
    val bottomInsetColor by animateColorAsState(
        targetValue = insetTargetColor,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "connectivityBottomInsetBg"
    )

    val isBannerComposed = bannerVisibilityState.currentState || bannerVisibilityState.targetState
    val handlesNavBars = showBottomBar || isBannerComposed

    Column(modifier = modifier.fillMaxWidth()) {
        if (showBottomBar) {
            KartBottomNav(
                currentRoute = currentRoute,
                unreadMessages = unreadMessages,
                onTabSelected = onTabSelected,
                applyNavigationBarsPadding = false
            )
        }

        AnimatedVisibility(
            visibleState = bannerVisibilityState,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(displayedBgColor)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("connectivity_status_banner")
            ) {
                if (tokens.isDark && lastVisibleState == ConnectivityBannerState.OFFLINE) {
                    HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 32.dp)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    AnimatedContent(
                        targetState = lastVisibleState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 120))
                        },
                        label = "connectivityBannerText"
                    ) { state ->
                        Text(
                            text = if (state == ConnectivityBannerState.BACK_ONLINE) "You're back online" else "You're offline",
                            color = if (state == ConnectivityBannerState.BACK_ONLINE) Color.White else Color(0xFFF3F4F6),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (handlesNavBars) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomInsetColor)
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
fun KartBottomNav(
    currentRoute: String,
    unreadMessages: Int,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationBarsPadding: Boolean = true
) {
    val tokens = KartThemeTokens.colors
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("kart_bottom_nav")
    ) {
        Column {
            HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                    .height(62.dp)
                    .padding(horizontal = 6.dp)
            ) {
                KartBottomTabs.forEach { tab ->
                    val active = currentRoute == tab.route ||
                        (tab.route == "profile" && currentRoute == "login")
                    val tint = if (active) KartPrimary else tokens.textMuted

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabSelected(tab.route) }
                            .padding(vertical = 6.dp)
                            .testTag(tab.testTag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (active) tab.activeIcon else tab.inactiveIcon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                            if (tab.route == "messages" && unreadMessages > 0) {
                                val badgeShape = RoundedCornerShape(999.dp)
                                val badgeLabel = if (unreadMessages > 99) "99+" else unreadMessages.toString()
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 9.dp, y = (-5).dp)
                                        .shadow(
                                            elevation = 2.dp,
                                            shape = badgeShape,
                                            ambientColor = Color.Black.copy(alpha = 0.25f),
                                            spotColor = Color.Black.copy(alpha = 0.30f)
                                        )
                                        .clip(badgeShape)
                                        .background(KartDanger)
                                        .border(1.5.dp, MaterialTheme.colorScheme.background, badgeShape)
                                        .height(17.dp)
                                        .padding(horizontal = if (badgeLabel.length > 1) 4.5.dp else 0.dp)
                                        .then(
                                            if (badgeLabel.length == 1) Modifier.width(17.dp) else Modifier
                                        )
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        color = Color.White,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 9.5.sp,
                                            lineHeight = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) KartPrimary else tokens.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KartNavigationRail(
    currentRoute: String,
    unreadMessages: Int,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.border(1.dp, tokens.cardBorder)
    ) {
        Spacer(Modifier.height(16.dp))
        KartBottomTabs.forEach { tab ->
            val active = currentRoute == tab.route
            NavigationRailItem(
                selected = active,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    Box {
                        Icon(
                            imageVector = if (active) tab.activeIcon else tab.inactiveIcon,
                            contentDescription = tab.label
                        )
                        if (tab.route == "messages" && unreadMessages > 0) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(KartDanger)
                            )
                        }
                    }
                },
                label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = KartPrimary,
                    selectedTextColor = KartPrimary,
                    indicatorColor = KartPrimary.copy(alpha = 0.14f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: MarketplaceFilter,
    campuses: List<String> = KartGhanaCampuses,
    onDismiss: () -> Unit,
    onApply: (MarketplaceFilter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = KartThemeTokens.colors
    val availableCampuses = campuses.ifEmpty { KartGhanaCampuses }
    var category by remember(currentFilter) { mutableStateOf(currentFilter.category) }
    var selectedConditions by remember(currentFilter) {
        mutableStateOf(
            currentFilter.condition
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        )
    }
    var campus by remember(currentFilter) { mutableStateOf(currentFilter.campus) }
    var showCampusSuggestions by remember { mutableStateOf(false) }
    val campusBringIntoViewRequester = remember { BringIntoViewRequester() }
    val matchingCampuses = remember(campus, availableCampuses) {
        val q = campus.trim()
        if (q.isEmpty()) availableCampuses.take(5)
        else availableCampuses.filter { c ->
            c.contains(q, ignoreCase = true) ||
                KartFormatters.abbreviateCampus(c).contains(q, ignoreCase = true)
        }.take(6)
    }
    LaunchedEffect(showCampusSuggestions, matchingCampuses.size) {
        if (showCampusSuggestions) {
            campusBringIntoViewRequester.bringIntoView()
        }
    }
    var minPrice by remember(currentFilter) { mutableStateOf(currentFilter.minPrice) }
    var maxPrice by remember(currentFilter) { mutableStateOf(currentFilter.maxPrice) }
    var sort by remember(currentFilter) { mutableStateOf(currentFilter.sort) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Filter & Sort",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Reset all",
                    style = MaterialTheme.typography.labelLarge,
                    color = KartPrimary,
                    modifier = Modifier
                        .clickable {
                            category = ""
                            selectedConditions = emptySet()
                            campus = ""
                            showCampusSuggestions = false
                            minPrice = ""
                            maxPrice = ""
                            sort = "newest"
                        }
                        .padding(8.dp)
                )
            }

            // Sort
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort by", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KartSortOptions.forEach { (key, label) ->
                        KartChip(
                            label = label,
                            selected = sort == key,
                            onClick = { sort = key }
                        )
                    }
                }
            }

            // Price range
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Price Range (₵ GHS)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KartTextField(
                        value = minPrice,
                        onValueChange = { minPrice = com.example.domain.model.KartFormatters.sanitizeDecimalInput(it) },
                        label = "Min",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    KartTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = com.example.domain.model.KartFormatters.sanitizeDecimalInput(it) },
                        label = "Max",
                        placeholder = "5000",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Item Condition (replicated 1:1 from Kart web mobile filter sheet)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Item Condition",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedConditions.isNotEmpty()) {
                            Text(
                                text = "(${selectedConditions.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = KartPrimary
                            )
                        }
                    }
                    if (selectedConditions.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedConditions = emptySet() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                KartConditionSelector(
                    selectedConditions = selectedConditions,
                    onToggleCondition = { name ->
                        selectedConditions = if (name in selectedConditions) {
                            selectedConditions - name
                        } else {
                            selectedConditions + name
                        }
                    }
                )
            }

            // Category
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KartChip(label = "All", selected = category.isBlank(), onClick = { category = "" })
                    KartCategories.forEach { cat ->
                        KartChip(
                            label = cat.name,
                            selected = category == cat.name,
                            onClick = { category = if (category == cat.name) "" else cat.name }
                        )
                    }
                }
            }

            // Campus
            val trimmedQuery = campus.trim()
            val normalizedCustomCampus = trimmedQuery.uppercase(java.util.Locale.US)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.bringIntoViewRequester(campusBringIntoViewRequester)
            ) {
                KartTextField(
                    value = campus,
                    onValueChange = {
                        campus = it
                        showCampusSuggestions = true
                    },
                    label = "Campus",
                    placeholder = "Search or enter your campus...",
                    leadingIcon = {
                        Icon(
                            imageVector = KartLucideIcons.Search,
                            contentDescription = null,
                            tint = tokens.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = if (campus.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    campus = ""
                                    showCampusSuggestions = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.X,
                                    contentDescription = "Clear campus",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            showCampusSuggestions = true
                        }
                    }
                )
                if (showCampusSuggestions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                    ) {
                        if (matchingCampuses.isNotEmpty()) {
                            matchingCampuses.forEachIndexed { idx, match ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            campus = match
                                            showCampusSuggestions = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = KartLucideIcons.MapPin,
                                        contentDescription = null,
                                        tint = KartPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = match,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (idx < matchingCampuses.lastIndex) {
                                    HorizontalDivider(color = tokens.cardBorder)
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        campus = normalizedCustomCampus
                                        showCampusSuggestions = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.Plus,
                                    contentDescription = null,
                                    tint = KartPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No matching campus found",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                    Text(
                                        text = "Use \"$normalizedCustomCampus\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KartPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            KartPrimaryButton(
                text = "Apply Filters",
                onClick = {
                    val orderedConditions = KartConditionOptions
                        .map { it.name }
                        .filter { it in selectedConditions }
                        .joinToString(",")
                    val parsedMin = minPrice.toDoubleOrNull()
                    val parsedMax = maxPrice.toDoubleOrNull()
                    val (resolvedMin, resolvedMax) = if (parsedMin != null && parsedMax != null && parsedMin > parsedMax) {
                        maxPrice to minPrice
                    } else {
                        minPrice to maxPrice
                    }
                    val trimmedCampus = campus.trim()
                    val resolvedCampus = if (trimmedCampus.isEmpty()) {
                        ""
                    } else {
                        availableCampuses.firstOrNull {
                            it.equals(trimmedCampus, ignoreCase = true)
                        } ?: trimmedCampus.uppercase(java.util.Locale.US)
                    }
                    onApply(
                        currentFilter.copy(
                            category = category,
                            condition = orderedConditions,
                            campus = resolvedCampus,
                            minPrice = resolvedMin,
                            maxPrice = resolvedMax,
                            sort = sort
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "apply_filters_btn"
            )
        }
    }
}

/**
 * Replicates the Kart web repo's Item Condition cards:
 * - Icon container (36.dp rounded-xl), condition title + description, and checkbox indicator (20.dp rounded-lg)
 * - Supports both multi-select (Marketplace filters) and single-select (Create/Edit Listing)
 */
@Composable
fun KartConditionSelector(
    selectedConditions: Set<String>,
    onToggleCondition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    val containerShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, tokens.cardBorder, containerShape)
    ) {
        KartConditionOptions.forEachIndexed { index, option ->
            val selected = selectedConditions.any { it.equals(option.name, ignoreCase = true) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) tokens.textPrimary.copy(alpha = 0.04f)
                        else Color.Transparent
                    )
                    .clickable { onToggleCondition(option.name) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("condition_option_${option.name.lowercase().replace(" ", "_")}")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textSecondary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) KartPrimary else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (selected) KartPrimary else tokens.cardBorder,
                            shape = CircleShape
                        )
                ) {
                    if (selected) {
                        Icon(
                            imageVector = KartLucideIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (index < KartConditionOptions.lastIndex) {
                HorizontalDivider(
                    color = tokens.cardBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
