package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
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
    onOpenUserProfile: (String) -> Unit
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
    var activeTab by remember { mutableStateOf("listings") }
    var followSheetType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resolvedId, currentUserId) {
        viewModel.loadProfile(resolvedId, currentUserId)
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(20.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(150.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(90.dp))
            }
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

                            // Social Links (Kart bundled social icons)
                            val hasSocials = !profile.instagramUrl.isNullOrBlank() ||
                                !profile.snapchatUrl.isNullOrBlank() ||
                                !profile.whatsappNumber.isNullOrBlank()
                            if (hasSocials) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!profile.instagramUrl.isNullOrBlank()) {
                                        SocialIconButton(R.drawable.ic_social_instagram, "Instagram") {
                                            val url = if (profile.instagramUrl.startsWith("http")) profile.instagramUrl
                                            else "https://instagram.com/${profile.instagramUrl.removePrefix("@")}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                    if (!profile.snapchatUrl.isNullOrBlank()) {
                                        SocialIconButton(R.drawable.ic_social_snapchat, "Snapchat") {
                                            val url = if (profile.snapchatUrl.startsWith("http")) profile.snapchatUrl
                                            else "https://snapchat.com/add/${profile.snapchatUrl.removePrefix("@")}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                    if (!profile.whatsappNumber.isNullOrBlank()) {
                                        SocialIconButton(R.drawable.ic_social_whatsapp, "WhatsApp") {
                                            val digits = profile.whatsappNumber.filter(Char::isDigit)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
                                        }
                                    }
                                }
                            }

                            if (!isOwnProfile) {
                                if (bundle.isFollowing) {
                                    KartSecondaryButton(
                                        text = "Following",
                                        onClick = { viewModel.toggleFollow(resolvedId) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    KartPrimaryButton(
                                        text = "Follow Seller",
                                        onClick = {
                                            if (currentUserId == null) onRequireAuth()
                                            else viewModel.toggleFollow(resolvedId)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
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
                                    text = "Verify Student Identity",
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
                    item {
                        Spacer(Modifier.height(8.dp))
                        KartOutlineButton(
                            text = "Sign Out",
                            icon = KartLucideIcons.LogOut,
                            borderColor = KartDanger,
                            contentColor = KartDanger,
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "profile_signout_btn"
                        )
                    }
                }
            }

            followSheetType?.let { type ->
                FollowersListSheet(
                    title = if (type == "followers") "Followers" else "Following",
                    users = state.followList,
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
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var displayName by remember(initialProfile) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
    var username by remember(initialProfile) { mutableStateOf(initialProfile?.username.orEmpty()) }
    var bio by remember(initialProfile) { mutableStateOf(initialProfile?.bio.orEmpty()) }
    var campus by remember(initialProfile) { mutableStateOf(initialProfile?.campus ?: KartGhanaCampuses.first()) }
    var phone by remember(initialProfile) { mutableStateOf(initialProfile?.phone.orEmpty()) }
    var instagram by remember(initialProfile) { mutableStateOf(initialProfile?.instagramUrl.orEmpty()) }
    var snapchat by remember(initialProfile) { mutableStateOf(initialProfile?.snapchatUrl.orEmpty()) }
    var whatsapp by remember(initialProfile) { mutableStateOf(initialProfile?.whatsappNumber.orEmpty()) }
    var avatarUrl by remember(initialProfile) { mutableStateOf(initialProfile?.avatarUrl) }
    var showAvatarSheet by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(context.contentResolver, uri) { uploadedUrl ->
                avatarUrl = uploadedUrl
            }
        }
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Edit Profile", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Campus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KartGhanaCampuses.take(6).forEach { c ->
                            KartChip(c, campus == c, { campus = c })
                        }
                    }
                }
            }
            item { KartTextField(phone, { phone = it }, "Phone Number", placeholder = "+233500000000") }
            item { KartTextField(instagram, { instagram = it }, "Instagram Handle / URL", placeholder = "@yourhandle") }
            item { KartTextField(snapchat, { snapchat = it }, "Snapchat Username", placeholder = "your_snap") }
            item { KartTextField(whatsapp, { whatsapp = it }, "WhatsApp Number", placeholder = "+233500000000") }

            item {
                KartPrimaryButton(
                    text = "Save Profile",
                    loading = state.actionLoading,
                    onClick = {
                        viewModel.saveProfile(userId, displayName, username, bio, campus, phone, instagram, snapchat, whatsapp, avatarUrl, onSaved)
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

        val products = state.wishlist.mapNotNull { it.product }.filter { it.id in wishlistIds || wishlistIds.isEmpty() }
        when {
            state.loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    repeat(3) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp)) }
                }
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
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    LaunchedEffect(userId) { viewModel.loadNotifications(userId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Notifications", onBack = onBack)

        when {
            state.loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    repeat(5) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp)) }
                }
            }
            state.notifications.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.NOTIFICATIONS,
                    title = "All caught up!",
                    subtitle = "Order updates, chat alerts, and escrow confirmations will appear here.",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.notifications, key = { it.id }) { notif ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteNotification(notif.id)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(KartDanger)
                                        .padding(horizontal = 24.dp)
                                ) {
                                    Icon(KartLucideIcons.Trash2, contentDescription = "Delete notification", tint = Color.White)
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (notif.isRead == true) MaterialTheme.colorScheme.background
                                        else KartPrimary.copy(alpha = 0.06f)
                                    )
                                    .clickable { viewModel.markNotificationRead(notif.id) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (notif.isRead == true) Color.Transparent else KartPrimary)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        notif.title ?: "Kart Update",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!notif.message.isNullOrBlank()) {
                                        Text(notif.message, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    }
                                    Text(KartFormatters.timeAgo(notif.createdAt), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                }
                            }
                        }
                        HorizontalDivider(color = tokens.cardBorder)
                    }
                }
            }
        }
    }
}
