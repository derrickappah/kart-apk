package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.Product
import com.example.domain.model.Profile
import com.example.ui.components.AvatarPickerSheet
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FollowersListSheet
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartProductCard
import com.example.ui.components.KartSecondaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StreakBadge
import com.example.ui.components.UserAvatar
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    targetUserId: String?,
    currentUserId: String?,
    viewModel: ProfileViewModel,
    wishlistIds: Set<String>,
    blockedUserIds: Set<String> = emptySet(),
    isEmbeddedTab: Boolean,
    onBack: () -> Unit,
    onRequireAuth: () -> Unit,
    onEditProfile: () -> Unit,
    onSellerHub: () -> Unit,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
    onWishlist: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onVerifyIdentity: () -> Unit,
    onAdminConsole: () -> Unit,
    onSignOut: () -> Unit,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onBlockUser: (String, () -> Unit) -> Unit = { _, _ -> },
    onUnblockUser: (String, () -> Unit) -> Unit = { _, _ -> },
    onShowMessage: (String) -> Unit = {}
) {
    val resolvedId = targetUserId ?: currentUserId
    if (resolvedId.isNullOrBlank()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            EmptyStateView(
                asset = EmptyStateAsset.AUTH_ERROR,
                title = "Sign in to your Kart profile",
                subtitle = "Track orders, manage your campus shop, save wishlist items, and withdraw earnings.",
                actionLabel = "Sign In",
                onActionClick = onRequireAuth
            )
        }
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors
    val isOwnProfile = resolvedId == currentUserId
    val isTargetBlocked = !isOwnProfile && resolvedId in blockedUserIds
    var activeTab by remember { mutableStateOf("listings") }
    var followSheetType by remember { mutableStateOf<String?>(null) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(resolvedId, currentUserId) {
        viewModel.loadProfile(resolvedId, currentUserId)
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            onShowMessage(it)
            viewModel.clearFeedback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isEmbeddedTab) {
            KartSubScreenTopBar(title = state.bundle?.profile?.resolvedName ?: "Profile", onBack = onBack)
        }

        if (state.loading && state.bundle == null) {
            ProfileScreenSkeleton(isOwnProfile = isOwnProfile)
        } else if (state.bundle == null) {
            EmptyStateView(
                asset = EmptyStateAsset.ERROR,
                title = "Profile unavailable",
                subtitle = state.error ?: "Could not load this profile.",
                actionLabel = "Retry",
                onActionClick = { viewModel.loadProfile(resolvedId, currentUserId) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            val bundle = state.bundle!!
            val profile = bundle.profile

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    avatarUrl = profile.avatarUrl,
                                    displayName = profile.resolvedName,
                                    size = 68.dp,
                                    isVerified = profile.isVerified == true
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = profile.resolvedName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = profile.campus ?: "@${profile.username ?: "student"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                    if ((profile.streakCount ?: 0) > 0) {
                                        StreakBadge(profile.streakCount ?: 0)
                                    }
                                }
                                if (isOwnProfile) {
                                    IconButton(onClick = onEditProfile, modifier = Modifier.testTag("profile_edit_btn")) {
                                        Icon(KartLucideIcons.Pencil, contentDescription = "Edit profile", tint = KartPrimary)
                                    }
                                }
                            }

                            if (!profile.bio.isNullOrBlank()) {
                                Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            }

                            // Stats Row
                            Row(
                                horizontalArrangement = Arrangement.SpaceAround,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ProfileStat("${bundle.listings.size}", "Listings") {}
                                ProfileStat(
                                    if ((profile.averageRating ?: 0.0) > 0) String.format("%.1f ★", profile.averageRating) else "New",
                                    "Rating"
                                ) {}
                                ProfileStat("${bundle.followersCount}", "Followers") {
                                    followSheetType = "followers"
                                    viewModel.loadFollowList(resolvedId, "followers")
                                }
                                ProfileStat("${bundle.followingCount}", "Following") {
                                    followSheetType = "following"
                                    viewModel.loadFollowList(resolvedId, "following")
                                }
                            }

                            // Social Links (Kart bundled social icons — sanitized to prevent arbitrary URI launch)
                            val resolvedWhatsapp = profile.whatsappNumber?.takeIf { it.isNotBlank() } ?: profile.phone
                            val safeInstagramUrl = KartFormatters.sanitizeSocialUrl(profile.instagramUrl, "instagram")
                            val safeSnapchatUrl = KartFormatters.sanitizeSocialUrl(profile.snapchatUrl, "snapchat")
                            val safeWhatsappUrl = KartFormatters.sanitizeSocialUrl(resolvedWhatsapp, "whatsapp")
                            val hasSocials = safeInstagramUrl != null || safeSnapchatUrl != null || safeWhatsappUrl != null
                            if (hasSocials) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                ) {
                                    safeInstagramUrl?.let { url ->
                                        SocialIconButton(R.drawable.ic_social_instagram, "Instagram") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                    safeSnapchatUrl?.let { url ->
                                        SocialIconButton(R.drawable.ic_social_snapchat, "Snapchat") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                    safeWhatsappUrl?.let { url ->
                                        SocialIconButton(R.drawable.ic_social_whatsapp, "WhatsApp") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                }
                            }

                            if (!isOwnProfile) {
                                if (isTargetBlocked) {
                                    KartOutlineButton(
                                        text = "Unblock User",
                                        borderColor = KartDanger,
                                        contentColor = KartDanger,
                                        onClick = {
                                            if (currentUserId == null) onRequireAuth()
                                            else onUnblockUser(resolvedId) {
                                                viewModel.loadProfile(resolvedId, currentUserId)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (bundle.isFollowing) {
                                            KartSecondaryButton(
                                                text = "Following",
                                                onClick = {
                                                    if (currentUserId == null) onRequireAuth()
                                                    else viewModel.toggleFollow(resolvedId, currentUserId)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            KartPrimaryButton(
                                                text = "Follow Seller",
                                                onClick = {
                                                    if (currentUserId == null) onRequireAuth()
                                                    else viewModel.toggleFollow(resolvedId, currentUserId)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        KartOutlineButton(
                                            text = "Block",
                                            borderColor = KartDanger,
                                            contentColor = KartDanger,
                                            onClick = {
                                                if (currentUserId == null) onRequireAuth()
                                                else showBlockConfirm = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dashboard Shortcuts for Own Profile
                if (isOwnProfile) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardQuickTile("Seller Studio", "Manage shop", KartLucideIcons.StoreActive, onSellerHub, Modifier.weight(1f))
                                DashboardQuickTile("Kart Wallet", KartFormatters.formatCedis(bundle.walletBalance), KartLucideIcons.Wallet, onWallet, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardQuickTile("My Orders", "Escrow & track", KartLucideIcons.ReceiptText, onOrders, Modifier.weight(1f))
                                DashboardQuickTile("Wishlist", "${wishlistIds.size} saved", KartLucideIcons.HeartOutline, onWishlist, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardQuickTile("Notifications", "Alerts", KartLucideIcons.Bell, onNotifications, Modifier.weight(1f))
                                DashboardQuickTile("Settings", "Security & KYC", KartLucideIcons.Settings, onSettings, Modifier.weight(1f))
                            }
                            if (profile.isVerified != true) {
                                KartOutlineButton(
                                    text = "Verify Identity",
                                    icon = KartLucideIcons.ShieldCheck,
                                    onClick = onVerifyIdentity,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (profile.isAdmin == true) {
                                KartPrimaryButton(
                                    text = "Open Admin Console",
                                    icon = KartLucideIcons.Shield,
                                    onClick = onAdminConsole,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Tabs: Listings vs Reviews
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KartChip("Listings (${bundle.listings.size})", activeTab == "listings", { activeTab = "listings" })
                        KartChip("Reviews (${bundle.reviews.size})", activeTab == "reviews", { activeTab = "reviews" })
                    }
                }

                if (activeTab == "listings") {
                    if (bundle.listings.isEmpty()) {
                        item {
                            EmptyStateView(
                                asset = EmptyStateAsset.LISTINGS,
                                title = "No listings yet",
                                subtitle = if (isOwnProfile) "Publish your first campus item from Seller Studio." else "This user hasn't posted listings yet.",
                                modifier = Modifier
                                    .fillParentMaxHeight(0.55f)
                                    .fillMaxWidth()
                            )
                        }
                    } else {
                        val rows = bundle.listings.chunked(2)
                        items(rows.size) { idx ->
                            val row = rows[idx]
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { prod ->
                                    KartProductCard(
                                        product = prod,
                                        isWishlisted = prod.id in wishlistIds,
                                        onProductClick = onProductClick,
                                        onWishlistToggle = onWishlistToggle,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    if (bundle.reviews.isEmpty()) {
                        item {
                            EmptyStateView(
                                asset = EmptyStateAsset.REVIEWS,
                                title = "No reviews yet",
                                subtitle = "Completed campus trades will build this seller's reputation.",
                                modifier = Modifier
                                    .fillParentMaxHeight(0.55f)
                                    .fillMaxWidth()
                            )
                        }
                    } else {
                        items(bundle.reviews, key = { it.id }) { rev ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(rev.reviewer?.displayName ?: "Buyer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Row {
                                            repeat(rev.rating.coerceIn(1, 5)) {
                                                Icon(KartLucideIcons.StarFilled, contentDescription = null, tint = KartWarning, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    val (body, _) = KartFormatters.parseReviewComment(rev.comment)
                                    if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                }
                            }
                        }
                    }
                }

                if (isOwnProfile) {
                }
            }

            if (showBlockConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBlockConfirm = false },
                    title = { Text("Block ${profile.resolvedName}?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Blocking removes any mutual follows and prevents this user from messaging or following you.")
                    },
                    confirmButton = {
                        KartPrimaryButton(
                            text = "Block User",
                            containerColor = KartDanger,
                            onClick = {
                                showBlockConfirm = false
                                onBlockUser(resolvedId) {
                                    viewModel.loadProfile(resolvedId, currentUserId)
                                }
                            }
                        )
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            followSheetType?.let { type ->
                FollowersListSheet(
                    title = if (type == "followers") "Followers" else "Following",
                    users = state.followList,
                    isLoading = state.followListLoading,
                    onUserClick = onOpenUserProfile,
                    onDismiss = { followSheetType = null }
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
    }
}

@Composable
private fun SocialIconButton(drawableRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, KartThemeTokens.colors.cardBorder, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Image(painter = painterResource(drawableRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DashboardQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = KartPrimary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    userId: String,
    initialProfile: Profile?,
    campuses: List<String> = KartGhanaCampuses,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val availableCampuses = campuses.ifEmpty { KartGhanaCampuses }
    var displayName by remember(initialProfile) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
    var username by remember(initialProfile) { mutableStateOf(initialProfile?.username.orEmpty()) }
    var bio by remember(initialProfile) { mutableStateOf(initialProfile?.bio.orEmpty()) }
    var campusQuery by remember(initialProfile) {
        mutableStateOf(initialProfile?.campus.orEmpty())
    }
    var showCampusSuggestions by remember { mutableStateOf(false) }
    val editListState = rememberLazyListState()
    val matchingCampuses = remember(campusQuery, availableCampuses) {
        val q = campusQuery.trim()
        if (q.isEmpty()) availableCampuses.take(5)
        else availableCampuses.filter { c ->
            c.contains(q, ignoreCase = true) ||
                KartFormatters.abbreviateCampus(c).contains(q, ignoreCase = true)
        }.take(6)
    }
    LaunchedEffect(showCampusSuggestions, campusQuery) {
        if (showCampusSuggestions) {
            editListState.animateScrollToItem(4)
        }
    }
    var phone by remember(initialProfile) { mutableStateOf(initialProfile?.phone.orEmpty()) }
    var instagram by remember(initialProfile) { mutableStateOf(initialProfile?.instagramUrl.orEmpty()) }
    var snapchat by remember(initialProfile) { mutableStateOf(initialProfile?.snapchatUrl.orEmpty()) }
    var whatsapp by remember(initialProfile) { mutableStateOf(initialProfile?.whatsappNumber.orEmpty()) }
    var avatarUrl by remember(initialProfile) { mutableStateOf(initialProfile?.avatarUrl) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Edit Profile", onBack = onBack)

        LazyColumn(
            state = editListState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .imePadding()
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.clickable { showAvatarSheet = true }) {
                        UserAvatar(avatarUrl = avatarUrl, displayName = displayName.ifBlank { "User" }, size = 84.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(KartPrimary)
                        ) {
                            Icon(KartLucideIcons.Camera, contentDescription = "Change avatar", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            item { KartTextField(displayName, { displayName = it }, "Display Name", placeholder = "Kwame Mensah") }
            item { KartTextField(username, { username = it }, "Username", placeholder = "kwame_ug") }
            item { KartTextField(bio, { bio = it }, "Bio", placeholder = "Computer Science student selling tech & books", singleLine = false, maxLines = 3) }
            item {
                val tokens = KartThemeTokens.colors
                val trimmedQuery = campusQuery.trim()
                val normalizedCustomCampus = trimmedQuery.uppercase(java.util.Locale.US)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KartTextField(
                        value = campusQuery,
                        onValueChange = {
                            campusQuery = it
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
                        trailingIcon = if (campusQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        campusQuery = ""
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
                                                campusQuery = match
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
                                            campusQuery = normalizedCustomCampus
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
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KartTextField(phone, { phone = it }, "Phone Number", placeholder = "+233500000000")
                    Text(
                        text = "Changing your phone number requires re-verification in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KartThemeTokens.colors.textSecondary
                    )
                }
            }
            item { KartTextField(instagram, { instagram = it }, "Instagram Handle / URL", placeholder = "@yourhandle") }
            item { KartTextField(snapchat, { snapchat = it }, "Snapchat Username", placeholder = "your_snap") }
            item { KartTextField(whatsapp, { whatsapp = it }, "WhatsApp Number (Optional)", placeholder = "+233500000000") }

            item {
                KartPrimaryButton(
                    text = "Save Profile",
                    loading = state.actionLoading,
                    enabled = displayName.trim().length >= 2 && !state.actionLoading,
                    onClick = {
                        if (displayName.trim().length < 2) {
                            onShowMessage("Display name must be at least 2 characters.")
                            return@KartPrimaryButton
                        }
                        val trimmedCampus = campusQuery.trim()
                        val resolvedCampus = availableCampuses.firstOrNull {
                            it.equals(trimmedCampus, ignoreCase = true)
                        } ?: trimmedCampus.uppercase(java.util.Locale.US)
                        viewModel.saveProfile(userId, displayName, username, bio, resolvedCampus, phone, instagram, snapchat, whatsapp, avatarUrl, onSaved)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showAvatarSheet) {
        AvatarPickerSheet(
            onSelectPresetUrl = { avatarUrl = it },
            onPickFromGallery = {
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { showAvatarSheet = false }
        )
    }

    if (pendingCropUri != null) {
        AvatarCropDialog(
            imageUri = pendingCropUri!!,
            onCancel = { pendingCropUri = null },
            onCropped = { croppedUri ->
                pendingCropUri = null
                viewModel.uploadAvatar(context.contentResolver, croppedUri) { uploadedUrl ->
                    avatarUrl = uploadedUrl
                }
            }
        )
    }
    }
}

@Composable
fun WishlistScreen(
    userId: String,
    viewModel: ProfileViewModel,
    wishlistIds: Set<String>,
    onBack: () -> Unit,
    onProductClick: (Product) -> Unit,
    onWishlistToggle: (String) -> Unit,
    onExploreShop: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.loadWishlist(userId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Saved Wishlist", onBack = onBack)

        val products = state.wishlist.mapNotNull { it.product }.filter { it.id in wishlistIds }
        when {
            state.loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    repeat(3) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp)) }
                }
            }
            state.error != null && state.wishlist.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ERROR,
                    title = "Could not load wishlist",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadWishlist(userId) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            products.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.WISHLIST,
                    title = "Your wishlist is empty",
                    subtitle = "Tap the heart icon on any campus item to bookmark it for later.",
                    actionLabel = "Explore Shop",
                    onActionClick = onExploreShop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                val rows = products.chunked(2)
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(rows.size) { idx ->
                        val row = rows[idx]
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { prod ->
                                KartProductCard(
                                    product = prod,
                                    isWishlisted = true,
                                    onProductClick = onProductClick,
                                    onWishlistToggle = onWishlistToggle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    userId: String,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onNotificationClick: (com.example.domain.model.NotificationItem) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onMarkedAllRead: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    var unreadOnly by remember { mutableStateOf(false) }
    val easeOutStrong = remember { androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }

    LaunchedEffect(userId) { viewModel.loadNotifications(userId) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            onShowMessage(it)
            viewModel.clearFeedback()
        }
    }

    val unreadCount = remember(state.notifications) { state.notifications.count { it.isRead != true } }
    val visibleNotifications = remember(state.notifications, unreadOnly) {
        if (unreadOnly) state.notifications.filter { it.isRead != true } else state.notifications
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(
            title = "Notifications",
            onBack = onBack,
            actions = {
                if (unreadCount > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(KartPrimary.copy(alpha = 0.12f))
                            .clickable {
                                viewModel.markAllNotificationsRead(userId, onDone = onMarkedAllRead)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Mark all read",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KartPrimary
                        )
                    }
                }
            }
        )

        if (!state.loading && state.notifications.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                listOf(false to "All (${state.notifications.size})", true to "Unread ($unreadCount)").forEach { (isUnreadTab, label) ->
                    val selected = unreadOnly == isUnreadTab
                    val bgColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) KartPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = androidx.compose.animation.core.tween(180, easing = easeOutStrong),
                        label = "notifTabBg"
                    )
                    val textColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) Color.White else tokens.textSecondary,
                        animationSpec = androidx.compose.animation.core.tween(180, easing = easeOutStrong),
                        label = "notifTabText"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bgColor)
                            .clickable { unreadOnly = isUnreadTab }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                }
            }
        }

        when {
            state.loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    repeat(5) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
            state.error != null && state.notifications.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ERROR,
                    title = "Could not load notifications",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadNotifications(userId) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            visibleNotifications.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.NOTIFICATIONS,
                    title = if (unreadOnly) "No unread notifications" else "All caught up!",
                    subtitle = if (unreadOnly) "You've read all your recent updates."
                    else "Order updates, chat alerts, and escrow confirmations will appear here.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleNotifications, key = { it.id }) { notif ->
                        val isUnread = notif.isRead != true
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteNotification(notif.id)
                                    true
                                } else false
                            }
                        )
                        val notifIcon = when {
                            notif.type?.contains("order", ignoreCase = true) == true ||
                                notif.title?.contains("order", ignoreCase = true) == true -> KartLucideIcons.Package
                            notif.type?.contains("message", ignoreCase = true) == true ||
                                notif.type?.contains("chat", ignoreCase = true) == true -> KartLucideIcons.MessageCircleInactive
                            notif.type?.contains("verify", ignoreCase = true) == true ||
                                notif.type?.contains("escrow", ignoreCase = true) == true -> KartLucideIcons.ShieldCheck
                            else -> KartLucideIcons.Bell
                        }
                        val cardShape = RoundedCornerShape(16.dp)
                        val borderColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isUnread) KartPrimary.copy(alpha = 0.30f) else tokens.cardBorder,
                            animationSpec = androidx.compose.animation.core.tween(200, easing = easeOutStrong),
                            label = "notifCardBorder"
                        )

                        Box(
                            modifier = Modifier.animateItem(
                                placementSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 240,
                                    easing = easeOutStrong
                                )
                            )
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                    Box(
                                        contentAlignment = Alignment.CenterEnd,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(cardShape)
                                            .background(if (isSwiping) KartDanger else Color.Transparent)
                                            .padding(horizontal = 22.dp)
                                    ) {
                                        if (isSwiping) {
                                            Icon(
                                                KartLucideIcons.Trash2,
                                                contentDescription = "Delete notification",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .then(
                                            if (isUnread) Modifier.background(KartPrimary.copy(alpha = 0.05f))
                                            else Modifier
                                        )
                                        .border(1.dp, borderColor, cardShape)
                                        .clickable {
                                            if (isUnread) viewModel.markNotificationRead(notif.id)
                                            onNotificationClick(notif)
                                        }
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(
                                                    if (isUnread) KartPrimary.copy(alpha = 0.14f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                        ) {
                                            Icon(
                                                imageVector = notifIcon,
                                                contentDescription = null,
                                                tint = if (isUnread) KartPrimary else tokens.textSecondary,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = notif.title ?: "Kart Update",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                                                    color = tokens.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = KartFormatters.timeAgo(notif.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isUnread) KartPrimary else tokens.textMuted
                                                )
                                                if (isUnread) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(KartPrimary)
                                                    )
                                                }
                                            }
                                            if (!notif.message.isNullOrBlank()) {
                                                Text(
                                                    text = notif.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = tokens.textSecondary,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreenSkeleton(isOwnProfile: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header Card Skeleton
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .height(13.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                    if (isOwnProfile) {
                        ShimmerBox(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                // Bio line
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                // 4-column Stats Row
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(4) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerBox(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            ShimmerBox(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions 2x2 Grid (own profile)
        if (isOwnProfile) {
            repeat(2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(2) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }

        // Tabs row skeleton
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(96.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            ShimmerBox(
                modifier = Modifier
                    .width(96.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        }

        // Listings 2-column grid skeleton
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(2) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
private fun AvatarCropDialog(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember(imageUri) { mutableStateOf(1f) }
    var offsetX by remember(imageUri) { mutableStateOf(0f) }
    var offsetY by remember(imageUri) { mutableStateOf(0f) }
    var cropSizePx by remember { mutableStateOf(0f) }

    LaunchedEffect(imageUri) {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val optsBounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, optsBounds)
                }
                val maxDim = maxOf(optsBounds.outWidth, optsBounds.outHeight).coerceAtLeast(1)
                var sample = 1
                while (maxDim / sample > 1600) sample *= 2
                val optsDecode = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(imageUri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, optsDecode)
                }
            }.getOrNull()
        }
        bitmap = loaded
    }

    val easeOutStrong = remember { androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val enterAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        enterAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = easeOutStrong)
        )
    }

    androidx.activity.compose.BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = enterAnim.value
                alpha = p
                val s = 0.96f + (0.04f * p)
                scaleX = s
                scaleY = s
            }
            .background(Color(0xFF0A0A0C))
    ) {
        // Full-bleed interactive photo layer behind circular cutout scrim
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        val limit = (cropSizePx * (newScale - 1f) * 0.7f).coerceAtLeast(160f)
                        offsetX = (offsetX + pan.x).coerceIn(-limit, limit)
                        offsetY = (offsetY + pan.y).coerceIn(-limit, limit)
                    }
                }
        ) {
            val bmp = bitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Crop preview",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .aspectRatio(1f)
                        .onSizeChanged { cropSizePx = it.width.toFloat() }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            }

            // Translucent dark scrim with crisp circular cutout
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            ) {
                drawRect(Color.Black.copy(alpha = 0.68f))
                val radius = (size.width - 48.dp.toPx()) / 2f
                drawCircle(
                    color = Color.Transparent,
                    radius = radius,
                    center = center,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    KartLucideIcons.X,
                    contentDescription = "Cancel",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "Move and Scale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = scale != 1f || offsetX != 0f || offsetY != 0f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (scale != 1f || offsetX != 0f || offsetY != 0f) KartPrimary else Color.Transparent
                )
            }
        }

        // Bottom Action Bar (properly inset above system nav bar)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            KartOutlineButton(
                text = "Cancel",
                borderColor = Color.White.copy(alpha = 0.32f),
                contentColor = Color.White,
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            val cropScope = androidx.compose.runtime.rememberCoroutineScope()
            var cropping by remember { mutableStateOf(false) }
            KartPrimaryButton(
                text = "Use Photo",
                loading = cropping,
                enabled = bitmap != null && !cropping,
                onClick = {
                    val src = bitmap ?: return@KartPrimaryButton
                    cropping = true
                    cropScope.launch {
                        val savedUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                val outSize = 512
                                val outBmp = android.graphics.Bitmap.createBitmap(
                                    outSize,
                                    outSize,
                                    android.graphics.Bitmap.Config.ARGB_8888
                                )
                                try {
                                    val canvas = android.graphics.Canvas(outBmp)
                                    val viewSize = cropSizePx.coerceAtLeast(1f)
                                    val baseScale = maxOf(
                                        outSize.toFloat() / src.width.toFloat(),
                                        outSize.toFloat() / src.height.toFloat()
                                    )
                                    val totalScale = baseScale * scale
                                    val dx = (outSize - src.width * totalScale) / 2f + (offsetX * (outSize / viewSize))
                                    val dy = (outSize - src.height * totalScale) / 2f + (offsetY * (outSize / viewSize))
                                    val matrix = android.graphics.Matrix().apply {
                                        postScale(totalScale, totalScale)
                                        postTranslate(dx, dy)
                                    }
                                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                                    canvas.drawBitmap(src, matrix, paint)

                                    val outFile = java.io.File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
                                    java.io.FileOutputStream(outFile).use { fos ->
                                        outBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, fos)
                                    }
                                    Uri.fromFile(outFile)
                                } finally {
                                    outBmp.recycle()
                                }
                            }.getOrNull()
                        }
                        cropping = false
                        if (savedUri != null) {
                            onCropped(savedUri)
                        } else {
                            onCancel()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
