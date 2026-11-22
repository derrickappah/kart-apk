package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.KartCategories
import com.example.domain.model.KartConditionOptions
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.KartSortOptions
import com.example.domain.model.MarketplaceFilter
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens

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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-6).dp, y = 8.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(KartDanger)
                                        .border(1.5.dp, badgeRingColor, CircleShape)
                                ) {
                                    Text(
                                        text = if (unreadNotifications > 9) "9+" else "$unreadNotifications",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
fun KartBottomNav(
    currentRoute: String,
    unreadMessages: Int,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 10.dp, y = (-5).dp)
                                        .clip(CircleShape)
                                        .background(KartDanger)
                                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (unreadMessages > 99) "99+" else unreadMessages.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: MarketplaceFilter,
    campuses: List<String> = KartGhanaCampuses,
    onDismiss: () -> Unit,
    onApply: (MarketplaceFilter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = KartThemeTokens.colors
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
                        onValueChange = { minPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = "Min",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    KartTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Campus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KartChip(label = "All Campuses", selected = campus.isBlank(), onClick = { campus = "" })
                    campuses.take(10).forEach { camp ->
                        KartChip(
                            label = camp,
                            selected = campus == camp,
                            onClick = { campus = if (campus == camp) "" else camp }
                        )
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
                    onApply(
                        currentFilter.copy(
                            category = category,
                            condition = orderedConditions,
                            campus = campus,
                            minPrice = minPrice,
                            maxPrice = maxPrice,
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
