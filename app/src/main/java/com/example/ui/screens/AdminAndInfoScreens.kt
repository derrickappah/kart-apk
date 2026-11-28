package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSearchBar
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatar
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartDangerContainer
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import com.example.ui.theme.KartWarningContainer
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    isAdmin: Boolean,
    currentUserId: String = "",
    viewModel: AdminViewModel,
    onBuyNowSettingChanged: (Boolean) -> Unit = {},
    onPlatformSettingsChanged: () -> Unit = {},
    onOpenProduct: (String) -> Unit = {},
    onOpenSeller: (String) -> Unit = {},
    onOpenOrder: (String) -> Unit = {},
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(
        "Overview", "Users", "Listings", "Orders", "KYC",
        "Withdrawals", "Refunds", "Reports", "Deletions", "Reviews", "Ads", "Plans", "Campuses", "Site Settings"
    )
    var selectedTab by remember { mutableStateOf("Overview") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var activeDetail by remember { mutableStateOf<AdminDetailTarget?>(null) }
    var inspectedImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    var showCampusDialog by remember { mutableStateOf(false) }
    var editingCampus by remember { mutableStateOf<com.example.domain.model.CampusRow?>(null) }
    var rejectingKycId by remember { mutableStateOf<String?>(null) }
    var manualApproveWithdrawalId by remember { mutableStateOf<String?>(null) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<com.example.domain.model.SubscriptionPlan?>(null) }

    fun navigateToTab(tab: String, filter: String = "All", query: String = "") {
        selectedTab = tab
        selectedFilter = filter
        searchQuery = query
    }

    if (!isAdmin) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            KartSubScreenTopBar(title = "Admin Console", onBack = onBack)
            EmptyStateView(
                asset = EmptyStateAsset.AUTH_ERROR,
                title = "Admin access required",
                subtitle = "This console is restricted to authorized Kart administrators.",
                actionLabel = "Go Back",
                onActionClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
        return
    }

    LaunchedEffect(Unit) { viewModel.loadDashboard() }
    DisposableEffect(Unit) {
        viewModel.startLiveUpdates()
        onDispose { viewModel.stopLiveUpdates() }
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }
    LaunchedEffect(selectedTab, searchQuery, selectedFilter) {
        if (selectedTab == "Users" || selectedTab == "Listings") {
            kotlinx.coroutines.delay(350L)
            when (selectedTab) {
                "Users" -> viewModel.searchUsersRemote(searchQuery, selectedFilter)
                "Listings" -> viewModel.searchProductsRemote(searchQuery, selectedFilter)
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab) {
        listState.scrollToItem(0)
    }

    val bundle = state.bundle
    val stats = remember(bundle) { AdminConsoleHelpers.computeOverviewStats(bundle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(
            title = "Kart Admin Console",
            onBack = onBack,
            actions = {
                if (state.loading && bundle != null) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = KartPrimary,
                        modifier = Modifier.padding(end = 12.dp).size(20.dp)
                    )
                } else {
                    IconButton(onClick = { viewModel.loadDashboard(forceRefresh = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh dashboard")
                    }
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs) { t ->
                val badge = when (t) {
                    "KYC" -> stats.pendingKyc
                    "Withdrawals" -> stats.pendingWithdrawals
                    "Refunds" -> stats.pendingRefunds
                    "Reports" -> stats.openReports
                    "Deletions" -> stats.pendingDeletions
                    else -> 0
                }
                val label = if (badge > 0) "$t ($badge)" else t
                KartChip(
                    label = label,
                    selected = selectedTab == t,
                    onClick = { navigateToTab(t) }
                )
            }
        }

        if (state.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KartDangerContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = state.error ?: "Failed to load admin data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KartDanger,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.loadDashboard(forceRefresh = true) }) {
                        Text("Retry", color = KartDanger, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selectedTab != "Overview" && selectedTab != "Site Settings" && bundle != null) {
            KartSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                placeholder = "Search ${selectedTab.lowercase()}...",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            val filterOptions = when (selectedTab) {
                "Users" -> listOf("All", "Active", "Banned", "Verified", "Admins")
                "Listings" -> listOf("All", "Active", "Suspended", "Sold")
                "Orders" -> listOf("All", "Pending", "Completed", "Refunded")
                "KYC", "Withdrawals", "Refunds", "Deletions" -> listOf("All", "Pending", "Approved", "Rejected")
                "Reports" -> listOf("All", "Open", "Product", "User", "Resolved")
                "Ads" -> listOf("All", "Active", "Paused", "Ended")
                "Plans" -> listOf("All", "Plans", "Subscriptions")
                else -> emptyList()
            }
            if (filterOptions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterOptions) { f ->
                        KartChip(
                            label = f,
                            selected = selectedFilter == f,
                            onClick = { selectedFilter = f }
                        )
                    }
                }
            }
        }

        if (state.loading && bundle == null) {
            AdminTabSkeleton(selectedTab = selectedTab)
        } else {
            val usersById = bundle?.usersById.orEmpty()
            val ordersById = bundle?.ordersById.orEmpty()
            val productsById = bundle?.productsById.orEmpty()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedTab != "Overview" && selectedTab != "Site Settings" && bundle != null) {
                    item(key = "stats_$selectedTab") {
                        AdminTabStatsStrip(
                            selectedTab = selectedTab,
                            bundle = bundle,
                            stats = stats
                        )
                    }
                }
                when (selectedTab) {
                    "Overview" -> item {
                        AdminOverviewDashboard(
                            bundle = bundle,
                            stats = stats,
                            isLiveConnected = state.isLiveConnected,
                            onNavigateTab = { tab, filter -> navigateToTab(tab, filter) }
                        )
                    }

                    "Users" -> {
                        val remoteList = state.remoteUsers
                        val displayed = remoteList ?: AdminConsoleHelpers.filterUsers(bundle?.users.orEmpty(), searchQuery, selectedFilter)
                        val hasMoreUsers = if (remoteList != null) state.remoteUsersHasMore else (bundle?.usersHasMore == true)
                        if (state.searchingUsers) {
                            item(key = "users_searching") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = KartPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Searching all ${stats.totalUsers} users...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                }
                            }
                        }
                        if (displayed.isEmpty() && !state.searchingUsers) {
                            item { AdminEmptyTabState("No matching users", "Try adjusting your search or filter.") }
                        } else {
                            items(displayed, key = { it.id }) { user ->
                                val isSelf = currentUserId.isNotBlank() && user.id == currentUserId
                                AdminCard(
                                    title = user.resolvedName,
                                    subtitle = listOfNotNull(
                                        user.email?.takeIf { it.isNotBlank() },
                                        user.campus?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    leading = { UserAvatar(avatarUrl = user.avatarUrl, displayName = user.resolvedName, size = 40.dp) },
                                    badge = {
                                        when {
                                            user.banned == true -> StatusPill("Banned", KartDangerContainer, KartDanger)
                                            user.isAdmin == true -> StatusPill("Admin", KartPrimary.copy(alpha = 0.14f), KartPrimary)
                                            user.isVerified == true -> StatusPill("Verified", KartSuccessContainer, KartSuccess)
                                        }
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.User(user) }
                                ) {
                                    if (isSelf) {
                                        StatusPill("Current Admin", KartSuccessContainer, KartSuccess)
                                    } else {
                                        val nextBan = user.banned != true
                                        KartOutlineButton(
                                            text = if (user.banned == true) "Unban User" else "Suspend User",
                                            enabled = !state.actionLoading,
                                            borderColor = if (user.banned == true) KartSuccess else KartDanger,
                                            contentColor = if (user.banned == true) KartSuccess else KartDanger,
                                            onClick = {
                                                pendingConfirm = Triple(
                                                    if (nextBan) "Suspend ${user.resolvedName}?" else "Reinstate ${user.resolvedName}?",
                                                    if (nextBan) "This immediately blocks the user from accessing Kart." else "This restores the user's access to Kart."
                                                ) { viewModel.toggleBan(user.id, nextBan) }
                                            }
                                        )
                                    }
                                }
                            }
                            if (hasMoreUsers) {
                                item(key = "users_load_more") {
                                    KartOutlineButton(
                                        text = when {
                                            state.loadingMoreUsers -> "Loading more users..."
                                            remoteList != null -> "Load More Matching Users (${displayed.size} loaded)"
                                            else -> "Load More Users (${displayed.size} of ${stats.totalUsers})"
                                        },
                                        enabled = !state.loadingMoreUsers && !state.actionLoading,
                                        onClick = { viewModel.loadMoreUsers() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    "Listings" -> {
                        val remoteList = state.remoteProducts
                        val displayed = remoteList ?: AdminConsoleHelpers.filterProducts(bundle?.products.orEmpty(), usersById, searchQuery, selectedFilter)
                        val hasMoreProducts = if (remoteList != null) state.remoteProductsHasMore else (bundle?.productsHasMore == true)
                        if (state.searchingProducts) {
                            item(key = "products_searching") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = KartPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Searching all ${stats.totalListings} listings...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                }
                            }
                        }
                        if (displayed.isEmpty() && !state.searchingProducts) {
                            item { AdminEmptyTabState("No matching listings", "Try adjusting your search or filter.") }
                        } else {
                            items(displayed, key = { it.id }) { p ->
                                val seller = p.sellerId?.let { usersById[it] }
                                val isSuspended = p.status.equals("Banned", true) || p.status.equals("Suspended", true)
                                AdminCard(
                                    title = p.title,
                                    subtitle = listOfNotNull(
                                        KartFormatters.formatCedis(p.price),
                                        seller?.resolvedName,
                                        p.campus?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    leading = p.primaryImage?.let { url ->
                                        {
                                            AsyncImage(
                                                model = KartFormatters.buildOptimizedImageUrl(url, KartFormatters.ImageVariant.CARD),
                                                contentDescription = p.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                    },
                                    badge = {
                                        StatusPill(
                                            text = p.status ?: "Active",
                                            containerColor = if (p.isActiveListing) KartSuccessContainer else KartDangerContainer,
                                            contentColor = if (p.isActiveListing) KartSuccess else KartDanger
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Listing(p) }
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        KartPrimaryButton(
                                            text = "Activate",
                                            enabled = !state.actionLoading && !p.status.equals("Active", true),
                                            onClick = { viewModel.updateProductStatus(p.id, "Active") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        KartOutlineButton(
                                            text = "Suspend",
                                            enabled = !state.actionLoading && !isSuspended,
                                            borderColor = KartDanger,
                                            contentColor = KartDanger,
                                            onClick = {
                                                pendingConfirm = Triple(
                                                    "Suspend Listing?",
                                                    "Suspend \"${p.title}\" so it can no longer be purchased?"
                                                ) { viewModel.updateProductStatus(p.id, "Banned") }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            if (hasMoreProducts) {
                                item(key = "products_load_more") {
                                    KartOutlineButton(
                                        text = when {
                                            state.loadingMoreProducts -> "Loading more listings..."
                                            remoteList != null -> "Load More Matching Listings (${displayed.size} loaded)"
                                            else -> "Load More Listings (${displayed.size} of ${stats.totalListings})"
                                        },
                                        enabled = !state.loadingMoreProducts && !state.actionLoading,
                                        onClick = { viewModel.loadMoreProducts() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    "Orders" -> {
                        val filtered = AdminConsoleHelpers.filterOrders(bundle?.orders.orEmpty(), usersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching orders", "Try adjusting your search or filter.") }
                        } else {
                            items(filtered, key = { it.id }) { o ->
                                val buyer = o.buyerId?.let { usersById[it] }
                                val seller = o.sellerId?.let { usersById[it] }
                                AdminCard(
                                    title = o.product?.title ?: "Order #${o.id.take(8)}",
                                    subtitle = listOfNotNull(
                                        KartFormatters.formatCedis(o.totalAmount),
                                        buyer?.let { "Buyer: ${it.resolvedName}" },
                                        seller?.let { "Seller: ${it.resolvedName}" },
                                        KartFormatters.timeAgo(o.createdAt)
                                    ).joinToString(" • "),
                                    leading = o.product?.primaryImage?.let { url ->
                                        {
                                            AsyncImage(
                                                model = KartFormatters.buildOptimizedImageUrl(url, KartFormatters.ImageVariant.CARD),
                                                contentDescription = o.product.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                    },
                                    badge = {
                                        StatusPill(
                                            text = o.status ?: "Pending",
                                            containerColor = KartPrimary.copy(alpha = 0.14f),
                                            contentColor = KartPrimary
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.OrderItem(o) }
                                ) {}
                            }
                        }
                    }

                    "KYC" -> {
                        val filtered = AdminConsoleHelpers.filterVerifications(bundle?.verifications.orEmpty(), usersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching KYC requests", "All clear for this filter.") }
                        } else {
                            items(filtered, key = { it.id }) { v ->
                                val applicant = v.userId?.let { usersById[it] }
                                val isPending = AdminConsoleHelpers.isPendingStatus(v.status)
                                AdminCard(
                                    title = v.fullName?.takeIf { it.isNotBlank() }
                                        ?: applicant?.resolvedName
                                        ?: "KYC #${v.id.take(8)}",
                                    subtitle = listOfNotNull(
                                        applicant?.email,
                                        v.resolvedStudentId?.let { "ID: $it" },
                                        v.campus?.takeIf { it.isNotBlank() } ?: applicant?.campus
                                    ).joinToString(" • "),
                                    badge = {
                                        val approved = v.status.equals("Approved", true) || v.status.equals("Verified", true)
                                        StatusPill(
                                            text = v.status ?: "Pending",
                                            containerColor = when {
                                                isPending -> KartWarningContainer
                                                approved -> KartSuccessContainer
                                                else -> KartDangerContainer
                                            },
                                            contentColor = when {
                                                isPending -> KartWarning
                                                approved -> KartSuccess
                                                else -> KartDanger
                                            }
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Kyc(v) }
                                ) {
                                    if (isPending) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartPrimaryButton(
                                                text = "Approve",
                                                enabled = !state.actionLoading,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Approve KYC Verification?",
                                                        "Grant verified identity status to this account after inspecting the ID and selfie?"
                                                    ) { viewModel.resolveVerification(v.id, true) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Reject",
                                                enabled = !state.actionLoading,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = { rejectingKycId = v.id },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Withdrawals" -> {
                        val filtered = AdminConsoleHelpers.filterWithdrawals(bundle?.withdrawals.orEmpty(), usersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching withdrawals", "All clear for this filter.") }
                        } else {
                            items(filtered, key = { it.id }) { w ->
                                val requester = w.userId?.let { usersById[it] }
                                val isPending = AdminConsoleHelpers.isPendingStatus(w.status)
                                val summary = w.resolvedAccountSummary
                                AdminCard(
                                    title = "${KartFormatters.formatCedis(w.amount)} • ${requester?.resolvedName ?: "Seller"}",
                                    subtitle = listOfNotNull(
                                        requester?.email,
                                        w.payoutMethod ?: "MoMo",
                                        summary
                                    ).joinToString(" • "),
                                    badge = {
                                        val approved = w.status.equals("Approved", true) || w.status.equals("Completed", true)
                                        StatusPill(
                                            text = w.status ?: "Pending",
                                            containerColor = when {
                                                isPending -> KartWarningContainer
                                                approved -> KartSuccessContainer
                                                else -> KartDangerContainer
                                            },
                                            contentColor = when {
                                                isPending -> KartWarning
                                                approved -> KartSuccess
                                                else -> KartDanger
                                            }
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Withdrawal(w) }
                                ) {
                                    if (isPending) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartPrimaryButton(
                                                text = "Approve",
                                                enabled = !state.actionLoading,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Approve Withdrawal?",
                                                        "Release ${KartFormatters.formatCedis(w.amount)} via ${w.payoutMethod ?: "MoMo"} to ${summary ?: "the listed account"}?"
                                                    ) { viewModel.resolveWithdrawal(w.id, true) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Reject",
                                                enabled = !state.actionLoading,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Reject Withdrawal?",
                                                        "Reject this ${KartFormatters.formatCedis(w.amount)} withdrawal request?"
                                                    ) { viewModel.resolveWithdrawal(w.id, false) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Refunds" -> {
                        val filtered = AdminConsoleHelpers.filterRefunds(bundle?.refunds.orEmpty(), usersById, ordersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching refund disputes", "All clear for this filter.") }
                        } else {
                            items(filtered, key = { it.id }) { r ->
                                val order = r.orderId?.let { ordersById[it] }
                                val buyer = (r.buyerId ?: order?.buyerId)?.let { usersById[it] }
                                val isPending = AdminConsoleHelpers.isPendingStatus(r.status)
                                AdminCard(
                                    title = order?.product?.title ?: "Refund #${r.id.take(8)}",
                                    subtitle = listOfNotNull(
                                        order?.let { KartFormatters.formatCedis(it.totalAmount) },
                                        buyer?.resolvedName,
                                        r.reason?.takeIf { it.isNotBlank() } ?: "Dispute"
                                    ).joinToString(" • "),
                                    badge = {
                                        val approved = r.status.equals("Approved", true) || r.status.equals("Refunded", true)
                                        StatusPill(
                                            text = r.status ?: "Pending",
                                            containerColor = when {
                                                isPending -> KartWarningContainer
                                                approved -> KartSuccessContainer
                                                else -> KartDangerContainer
                                            },
                                            contentColor = when {
                                                isPending -> KartWarning
                                                approved -> KartSuccess
                                                else -> KartDanger
                                            }
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Refund(r) }
                                ) {
                                    if (isPending) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartPrimaryButton(
                                                text = "Refund Buyer",
                                                enabled = !r.orderId.isNullOrBlank() && !state.actionLoading,
                                                onClick = {
                                                    val oid = r.orderId.orEmpty()
                                                    if (oid.isBlank()) {
                                                        onShowMessage("Cannot approve refund: Order ID is missing.")
                                                    } else {
                                                        pendingConfirm = Triple(
                                                            "Issue Buyer Refund?",
                                                            "Approve this refund dispute and return escrow funds to the buyer?"
                                                        ) { viewModel.resolveRefund(oid, r.id, true) }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Reject",
                                                enabled = !state.actionLoading,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Reject Refund Dispute?",
                                                        "Reject this buyer's refund request?"
                                                    ) { viewModel.resolveRefund(r.orderId.orEmpty(), r.id, false) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Reports" -> {
                        val filtered = AdminConsoleHelpers.filterReports(bundle?.reports.orEmpty(), usersById, productsById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching reports", "Try adjusting your search or filter.") }
                        } else {
                            items(filtered, key = { it.id }) { rep ->
                                val reporter = rep.reporterId?.let { usersById[it] }
                                val targetLabel = rep.resolvedTargetId?.let { tid ->
                                    productsById[tid]?.title ?: usersById[tid]?.resolvedName
                                }
                                AdminCard(
                                    title = rep.reason ?: "Report",
                                    subtitle = listOfNotNull(
                                        targetLabel?.let { "Target: $it" },
                                        reporter?.let { "By: ${it.resolvedName}" },
                                        rep.resolvedDetails
                                    ).joinToString(" • "),
                                    badge = {
                                        StatusPill(
                                            text = rep.resolvedTargetType.replaceFirstChar { it.uppercase() },
                                            containerColor = KartWarningContainer,
                                            contentColor = KartWarning
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Report(rep) }
                                ) {}
                            }
                        }
                    }

                    "Deletions" -> {
                        val filtered = AdminConsoleHelpers.filterDeletionRequests(bundle?.deletionRequests.orEmpty(), usersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No deletion requests", "All clear for this filter.") }
                        } else {
                            items(filtered, key = { it.id }) { req ->
                                val user = req.userId?.let { usersById[it] }
                                val isPending = AdminConsoleHelpers.isPendingStatus(req.status)
                                val approved = req.status.equals("approved", true)
                                AdminCard(
                                    title = user?.resolvedName ?: req.email ?: "Deletion #${req.id.take(8)}",
                                    subtitle = listOfNotNull(
                                        req.email ?: user?.email,
                                        req.reason?.takeIf { it.isNotBlank() } ?: "Account deletion requested",
                                        KartFormatters.timeAgo(req.createdAt)
                                    ).joinToString(" • "),
                                    badge = {
                                        StatusPill(
                                            text = (req.status ?: "pending").replaceFirstChar { it.uppercase() },
                                            containerColor = when {
                                                isPending -> KartWarningContainer
                                                approved -> KartSuccessContainer
                                                else -> KartDangerContainer
                                            },
                                            contentColor = when {
                                                isPending -> KartWarning
                                                approved -> KartSuccess
                                                else -> KartDanger
                                            }
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.DeletionRequest(req) }
                                ) {
                                    if (isPending) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartPrimaryButton(
                                                text = "Approve",
                                                enabled = !state.actionLoading,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Approve Account Deletion?",
                                                        "This will ban the user's account, suspend all their active listings, and mark this deletion request as approved."
                                                    ) { viewModel.resolveDeletionRequest(req.id, req.userId, true) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Reject",
                                                enabled = !state.actionLoading,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Reject Account Deletion?",
                                                        "Reject this user's account deletion request?"
                                                    ) { viewModel.resolveDeletionRequest(req.id, req.userId, false) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Reviews" -> {
                        val filtered = AdminConsoleHelpers.filterReviews(bundle?.reviews.orEmpty(), usersById, productsById, searchQuery)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching reviews", "Try adjusting your search.") }
                        } else {
                            items(filtered, key = { it.id }) { rev ->
                                val reviewer = (rev.reviewerId ?: rev.buyerId)?.let { usersById[it] }
                                val seller = rev.sellerId?.let { usersById[it] }
                                AdminCard(
                                    title = "${reviewer?.resolvedName ?: rev.reviewer?.displayName ?: "Buyer"} → ${seller?.resolvedName ?: "Seller"}",
                                    subtitle = rev.comment?.takeIf { it.isNotBlank() } ?: "No written comment",
                                    badge = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Icon(Icons.Outlined.Star, contentDescription = null, tint = KartWarning, modifier = Modifier.size(16.dp))
                                            Text("${rev.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.ReviewItem(rev) }
                                ) {}
                            }
                        }
                    }

                    "Ads" -> {
                        val filtered = AdminConsoleHelpers.filterAds(bundle?.advertisements.orEmpty(), productsById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching ads", "Try adjusting your search or filter.") }
                        } else {
                            items(filtered, key = { it.id }) { ad ->
                                val product = ad.product ?: ad.productId?.let { productsById[it] }
                                AdminCard(
                                    title = product?.title ?: "${ad.adType ?: "Promotion"} #${ad.id.take(8)}",
                                    subtitle = "${ad.adType ?: "Boost"} • ${ad.views ?: 0} views • ${ad.clicks ?: 0} clicks • ${KartFormatters.formatCedis(ad.resolvedPrice)}",
                                    badge = {
                                        StatusPill(
                                            text = ad.status ?: "Active",
                                            containerColor = KartPrimary.copy(alpha = 0.14f),
                                            contentColor = KartPrimary
                                        )
                                    },
                                    onClick = { activeDetail = AdminDetailTarget.Ad(ad) }
                                ) {}
                            }
                        }
                    }

                    "Plans" -> {
                        val q = searchQuery.trim().lowercase()
                        val showPlans = selectedFilter != "Subscriptions"
                        val showSubs = selectedFilter != "Plans"
                        if (showPlans) {
                            item(key = "add_plan_btn") {
                                KartPrimaryButton(
                                    text = "+ Add Subscription Plan",
                                    enabled = !state.actionLoading,
                                    onClick = {
                                        editingPlan = null
                                        showPlanDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val filteredPlans = bundle?.subscriptionPlans.orEmpty().filter { plan ->
                                q.isEmpty() || plan.name.lowercase().contains(q) ||
                                    plan.features.orEmpty().any { it.lowercase().contains(q) }
                            }
                            if (filteredPlans.isEmpty()) {
                                item(key = "plans_empty") { AdminEmptyTabState("No matching subscription plans", "Add a plan or adjust your search.") }
                            } else {
                                items(filteredPlans, key = { "plan_${it.id}" }) { plan ->
                                    AdminCard(
                                        title = plan.name,
                                        subtitle = listOfNotNull(
                                            if (plan.price <= 0.0) "Free" else KartFormatters.formatCedis(plan.price),
                                            "${plan.durationMonths} mo",
                                            plan.features.orEmpty().take(3).joinToString(", ").takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        badge = {
                                            StatusPill(
                                                text = if (plan.price <= 0.0) "Free" else KartFormatters.formatCedis(plan.price),
                                                containerColor = KartPrimary.copy(alpha = 0.14f),
                                                contentColor = KartPrimary
                                            )
                                        }
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartOutlineButton(
                                                text = "Edit",
                                                enabled = !state.actionLoading,
                                                onClick = {
                                                    editingPlan = plan
                                                    showPlanDialog = true
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Delete",
                                                enabled = !state.actionLoading,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Delete Plan?",
                                                        "Delete subscription plan \"${plan.name}\"?"
                                                    ) { viewModel.deleteSubscriptionPlan(plan.id) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (showSubs) {
                            val filteredSubs = bundle?.subscriptions.orEmpty().filter { sub ->
                                val subscriber = sub.userId?.let { usersById[it] }
                                q.isEmpty() || listOfNotNull(
                                    sub.id,
                                    sub.status,
                                    sub.paymentReference,
                                    sub.plan?.name,
                                    subscriber?.resolvedName,
                                    subscriber?.email
                                ).any { it.lowercase().contains(q) }
                            }
                            item(key = "subs_header") {
                                Text(
                                    text = "User Subscriptions (${filteredSubs.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = KartThemeTokens.colors.textSecondary,
                                    modifier = Modifier.padding(top = if (showPlans) 6.dp else 0.dp)
                                )
                            }
                            if (filteredSubs.isEmpty()) {
                                item(key = "subs_empty") { AdminEmptyTabState("No matching user subscriptions", "No subscriptions match your filter.") }
                            } else {
                                items(filteredSubs, key = { "sub_${it.id}" }) { sub ->
                                    val subscriber = sub.userId?.let { usersById[it] }
                                    val isActiveSub = sub.status.equals("Active", true)
                                    val isCancelledSub = sub.status.equals("Cancelled", true)
                                    AdminCard(
                                        title = "${subscriber?.resolvedName ?: sub.userId?.take(8) ?: "User"} • ${sub.plan?.name ?: "Plan"}",
                                        subtitle = listOfNotNull(
                                            subscriber?.email,
                                            sub.paymentReference,
                                            sub.endDate?.let { "Ends ${KartFormatters.timeAgo(it)}" }
                                        ).joinToString(" • "),
                                        badge = {
                                            StatusPill(
                                                text = sub.status ?: "Pending",
                                                containerColor = when {
                                                    isActiveSub -> KartSuccessContainer
                                                    isCancelledSub -> KartDangerContainer
                                                    else -> KartWarningContainer
                                                },
                                                contentColor = when {
                                                    isActiveSub -> KartSuccess
                                                    isCancelledSub -> KartDanger
                                                    else -> KartWarning
                                                }
                                            )
                                        },
                                        onClick = {
                                            subscriber?.let { activeDetail = AdminDetailTarget.User(it) }
                                        }
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            KartPrimaryButton(
                                                text = "Activate",
                                                enabled = !state.actionLoading && !isActiveSub,
                                                onClick = { viewModel.updateSubscriptionStatus(sub.id, "Active") },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Cancel",
                                                enabled = !state.actionLoading && !isCancelledSub,
                                                borderColor = KartDanger,
                                                contentColor = KartDanger,
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Cancel Subscription?",
                                                        "Mark ${subscriber?.resolvedName ?: "this user"}'s ${sub.plan?.name ?: "subscription"} as Cancelled?"
                                                    ) { viewModel.updateSubscriptionStatus(sub.id, "Cancelled") }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Campuses" -> {
                        item(key = "add_campus_btn") {
                            KartPrimaryButton(
                                text = "+ Add Campus",
                                enabled = !state.actionLoading,
                                onClick = {
                                    editingCampus = null
                                    showCampusDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        val filtered = AdminConsoleHelpers.filterCampuses(bundle?.campuses.orEmpty(), searchQuery)
                        val loadedPrefix = if ((bundle?.users?.size ?: 0) < stats.totalUsers || (bundle?.products?.size ?: 0) < stats.totalListings) "loaded " else ""
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching campuses", "Try adjusting your search or add a new campus.") }
                        } else {
                            items(filtered, key = { it.id ?: it.name }) { c ->
                                val userCount = bundle?.users?.count { it.campus.equals(c.name, true) } ?: 0
                                val listingCount = bundle?.products?.count { it.campus.equals(c.name, true) && it.isActiveListing } ?: 0
                                AdminCard(
                                    title = c.name,
                                    subtitle = "${c.abbreviation ?: "Campus"} • $userCount ${loadedPrefix}users • $listingCount ${loadedPrefix}active listings",
                                    onClick = { activeDetail = AdminDetailTarget.Campus(c) }
                                ) {}
                            }
                        }
                    }

                    "Site Settings" -> item {
                        val flags = bundle?.platformFlags ?: com.example.domain.model.PlatformFlags()
                        val buyNowEnabled = bundle?.buyNowEnabled ?: flags.buyNowEnabled
                        val maintenanceMode = bundle?.isMaintenanceMode ?: flags.isMaintenanceMode
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Storefront Controls",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = KartThemeTokens.colors.textSecondary
                                        )
                                        StatusPill(
                                            text = if (buyNowEnabled) "Buy Now Active" else "Buy Now Off",
                                            containerColor = if (buyNowEnabled) KartSuccessContainer else KartWarningContainer,
                                            contentColor = if (buyNowEnabled) KartSuccess else KartWarning
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                            Text("Buy Now on Product Pages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (buyNowEnabled) {
                                                    "Buyers can tap Buy Now on product details pages to complete checkout."
                                                } else {
                                                    "Buy Now is hidden on product details pages and direct checkout is paused. Buyers can still chat with sellers."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = KartThemeTokens.colors.textSecondary
                                            )
                                        }
                                        Switch(
                                            checked = buyNowEnabled,
                                            enabled = !state.actionLoading,
                                            onCheckedChange = { nextEnabled ->
                                                if (!nextEnabled) {
                                                    pendingConfirm = Triple(
                                                        "Turn Off Buy Now?",
                                                        "This hides the Buy Now button on all product detail pages and pauses direct checkout until you turn it back on."
                                                    ) {
                                                        viewModel.setBuyNowEnabled(false) { enabled ->
                                                            onBuyNowSettingChanged(enabled)
                                                            onPlatformSettingsChanged()
                                                        }
                                                    }
                                                } else {
                                                    viewModel.setBuyNowEnabled(true) { enabled ->
                                                        onBuyNowSettingChanged(enabled)
                                                        onPlatformSettingsChanged()
                                                    }
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KartPrimary),
                                            modifier = Modifier.testTag("admin_buy_now_switch")
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                            Text("Maintenance Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (maintenanceMode) {
                                                    "Kart is currently in maintenance mode for non-admin users."
                                                } else {
                                                    "Marketplace is live for all campus buyers and sellers."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = KartThemeTokens.colors.textSecondary
                                            )
                                        }
                                        Switch(
                                            checked = maintenanceMode,
                                            enabled = !state.actionLoading,
                                            onCheckedChange = { nextEnabled ->
                                                pendingConfirm = Triple(
                                                    if (nextEnabled) "Enable Maintenance Mode?" else "Disable Maintenance Mode?",
                                                    if (nextEnabled) "Non-admin users will see a maintenance notice until you disable it."
                                                    else "Restore normal marketplace access for all users?"
                                                ) { viewModel.setMaintenanceMode(nextEnabled) { onPlatformSettingsChanged() } }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KartWarning)
                                        )
                                    }
                                }
                            }

                            var dailyInput by remember(flags.promoDailyPrice) {
                                mutableStateOf(formatEditableDecimal(flags.promoDailyPrice))
                            }
                            var weeklyInput by remember(flags.promoWeeklyPrice) {
                                mutableStateOf(formatEditableDecimal(flags.promoWeeklyPrice))
                            }
                            var featuredInput by remember(flags.promoFeaturedPrice) {
                                mutableStateOf(formatEditableDecimal(flags.promoFeaturedPrice))
                            }
                            val parsedDaily = dailyInput.trim().toDoubleOrNull()
                            val parsedWeekly = weeklyInput.trim().toDoubleOrNull()
                            val parsedFeatured = featuredInput.trim().toDoubleOrNull()
                            val ratesValid = parsedDaily != null && parsedDaily >= 0.0 &&
                                parsedWeekly != null && parsedWeekly >= 0.0 &&
                                parsedFeatured != null && parsedFeatured >= 0.0

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text(
                                        text = "Promotion Pricing (GHS)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                    Text(
                                        text = "Live promotion tier prices synced with seller Boost & Featured checkout.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                    KartTextField(
                                        value = dailyInput,
                                        onValueChange = { dailyInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Daily Boost Price (24h, GHS)",
                                        placeholder = "5.00"
                                    )
                                    KartTextField(
                                        value = weeklyInput,
                                        onValueChange = { weeklyInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Weekly Boost Price (7d, GHS)",
                                        placeholder = "25.00"
                                    )
                                    KartTextField(
                                        value = featuredInput,
                                        onValueChange = { featuredInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Featured Listing Price (30d, GHS)",
                                        placeholder = "50.00"
                                    )
                                    KartPrimaryButton(
                                        text = "Save Promotion Pricing",
                                        enabled = !state.actionLoading && ratesValid,
                                        onClick = {
                                            val d = parsedDaily ?: return@KartPrimaryButton
                                            val w = parsedWeekly ?: return@KartPrimaryButton
                                            val f = parsedFeatured ?: return@KartPrimaryButton
                                            pendingConfirm = Triple(
                                                "Update Promotion Pricing?",
                                                "Set Daily Boost to ${KartFormatters.formatCedis(d)}, Weekly Boost to ${KartFormatters.formatCedis(w)}, and Featured Listing to ${KartFormatters.formatCedis(f)}?"
                                            ) {
                                                viewModel.updatePromotionRates(d, w, f) {
                                                    onPlatformSettingsChanged()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            var serviceFeeInput by remember(flags.marketplaceServiceFee) {
                                mutableStateOf(formatEditableDecimal(flags.marketplaceServiceFee))
                            }
                            var txFixedInput by remember(flags.transactionFeeFixed) {
                                mutableStateOf(formatEditableDecimal(flags.transactionFeeFixed))
                            }
                            var txPercentInput by remember(flags.transactionFeePercent) {
                                mutableStateOf(formatEditableDecimal(flags.transactionFeePercent))
                            }
                            var withdrawalFeeInput by remember(flags.withdrawalFeeFlat) {
                                mutableStateOf(formatEditableDecimal(flags.withdrawalFeeFlat))
                            }
                            var minDepositInput by remember(flags.minDepositAmount) {
                                mutableStateOf(formatEditableDecimal(flags.minDepositAmount))
                            }
                            var minWithdrawalInput by remember(flags.minWithdrawalAmount) {
                                mutableStateOf(formatEditableDecimal(flags.minWithdrawalAmount))
                            }
                            var maxPriceInput by remember(flags.maxListingPrice) {
                                mutableStateOf(formatEditableDecimal(flags.maxListingPrice))
                            }
                            var supportEmailInput by remember(flags.supportEmail) {
                                mutableStateOf(flags.supportEmail)
                            }
                            var whatsappInput by remember(flags.whatsappSupportNumber) {
                                mutableStateOf(flags.whatsappSupportNumber)
                            }

                            val pServiceFee = serviceFeeInput.trim().toDoubleOrNull()
                            val pTxFixed = txFixedInput.trim().toDoubleOrNull()
                            val pTxPercent = txPercentInput.trim().toDoubleOrNull()
                            val pWithdrawalFee = withdrawalFeeInput.trim().toDoubleOrNull()
                            val pMinDeposit = minDepositInput.trim().toDoubleOrNull()
                            val pMinWithdrawal = minWithdrawalInput.trim().toDoubleOrNull()
                            val pMaxPrice = maxPriceInput.trim().toDoubleOrNull()
                            val financialValid = pServiceFee != null && pServiceFee >= 0.0 &&
                                pTxFixed != null && pTxFixed >= 0.0 &&
                                pTxPercent != null && pTxPercent >= 0.0 &&
                                pWithdrawalFee != null && pWithdrawalFee >= 0.0 &&
                                pMinDeposit != null && pMinDeposit >= 0.0 &&
                                pMinWithdrawal != null && pMinWithdrawal >= 0.0 &&
                                pMaxPrice != null && pMaxPrice > 0.0 &&
                                supportEmailInput.trim().isNotBlank()

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text(
                                        text = "Fees, Limits & Support",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                    KartTextField(
                                        value = serviceFeeInput,
                                        onValueChange = { serviceFeeInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Marketplace Service Fee (GHS)",
                                        placeholder = "2"
                                    )
                                    KartTextField(
                                        value = txFixedInput,
                                        onValueChange = { txFixedInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Transaction Fee Flat (GHS)",
                                        placeholder = "1"
                                    )
                                    KartTextField(
                                        value = txPercentInput,
                                        onValueChange = { txPercentInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Transaction Fee (%)",
                                        placeholder = "0"
                                    )
                                    KartTextField(
                                        value = withdrawalFeeInput,
                                        onValueChange = { withdrawalFeeInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Withdrawal Fee Flat (GHS)",
                                        placeholder = "1"
                                    )
                                    KartTextField(
                                        value = minDepositInput,
                                        onValueChange = { minDepositInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Minimum Deposit (GHS)",
                                        placeholder = "10"
                                    )
                                    KartTextField(
                                        value = minWithdrawalInput,
                                        onValueChange = { minWithdrawalInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Minimum Withdrawal (GHS)",
                                        placeholder = "10"
                                    )
                                    KartTextField(
                                        value = maxPriceInput,
                                        onValueChange = { maxPriceInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Max Listing Price (GHS)",
                                        placeholder = "50000"
                                    )
                                    KartTextField(
                                        value = supportEmailInput,
                                        onValueChange = { supportEmailInput = it },
                                        label = "Platform Support Email",
                                        placeholder = "support@kart.com"
                                    )
                                    KartTextField(
                                        value = whatsappInput,
                                        onValueChange = { whatsappInput = it },
                                        label = "WhatsApp Support Number",
                                        placeholder = "+233500502158"
                                    )
                                    KartPrimaryButton(
                                        text = "Save Fees, Limits & Support",
                                        enabled = !state.actionLoading && financialValid,
                                        onClick = {
                                            pendingConfirm = Triple(
                                                "Save Platform Settings?",
                                                "Update platform fees, wallet limits, and support contact settings live across Kart?"
                                            ) {
                                                viewModel.savePlatformSettings(
                                                    updates = listOf(
                                                        com.example.data.repository.KartRepository.KEY_MARKETPLACE_SERVICE_FEE to (pServiceFee ?: 2.0),
                                                        com.example.data.repository.KartRepository.KEY_TRANSACTION_FEE_FIXED to (pTxFixed ?: 1.0),
                                                        com.example.data.repository.KartRepository.KEY_TRANSACTION_FEE_PERCENT to (pTxPercent ?: 0.0),
                                                        com.example.data.repository.KartRepository.KEY_WITHDRAWAL_FEE_FLAT to (pWithdrawalFee ?: 1.0),
                                                        com.example.data.repository.KartRepository.KEY_MIN_DEPOSIT_AMOUNT to (pMinDeposit ?: 10.0),
                                                        com.example.data.repository.KartRepository.KEY_MIN_WITHDRAWAL_AMOUNT to (pMinWithdrawal ?: 10.0),
                                                        com.example.data.repository.KartRepository.KEY_MAX_LISTING_PRICE to (pMaxPrice ?: 50000.0),
                                                        com.example.data.repository.KartRepository.KEY_PLATFORM_SUPPORT_EMAIL to supportEmailInput.trim(),
                                                        com.example.data.repository.KartRepository.KEY_WHATSAPP_SUPPORT_NUMBER to whatsappInput.trim()
                                                    ),
                                                    successMsg = "Fees, limits & support settings saved"
                                                ) { onPlatformSettingsChanged() }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeDetail != null && bundle != null) {
        val liveTarget = remember(activeDetail, bundle) {
            AdminConsoleHelpers.resolveLiveTarget(activeDetail!!, bundle)
        }
        AdminDetailBottomSheet(
            target = liveTarget,
            bundle = bundle,
            currentUserId = currentUserId,
            accessToken = state.accessToken,
            actionLoading = state.actionLoading,
            onDismiss = { activeDetail = null },
            onSelectTarget = { next -> activeDetail = next },
            onInspectImage = { url, title -> inspectedImage = url to title },
            onOpenProduct = onOpenProduct,
            onOpenSeller = onOpenSeller,
            onOpenOrder = onOpenOrder,
            onFilterCampus = { tab, campusName -> navigateToTab(tab = tab, query = campusName) },
            onRequestConfirm = { title, message, action -> pendingConfirm = Triple(title, message, action) },
            onToggleBan = viewModel::toggleBan,
            onToggleAdminRole = viewModel::toggleAdminRole,
            onUpdateProductStatus = viewModel::updateProductStatus,
            onDeleteProduct = viewModel::deleteProduct,
            onResolveVerification = viewModel::resolveVerification,
            onRequestRejectKyc = { kycId -> rejectingKycId = kycId },
            onResolveWithdrawal = viewModel::resolveWithdrawal,
            onRequestManualApproveWithdrawal = { wid -> manualApproveWithdrawalId = wid },
            onRetryWithdrawalPaystack = viewModel::retryWithdrawalPaystack,
            onResolveRefund = viewModel::resolveRefund,
            onEditCampus = { campus ->
                editingCampus = campus
                showCampusDialog = true
            },
            onDeleteCampus = { campus ->
                campus.id?.let { viewModel.deleteCampus(it) }
            },
            onUpdateAdStatus = viewModel::updateAdStatus,
            onDeleteAd = viewModel::deleteAd,
            onUpdateReportStatus = viewModel::updateReportStatus,
            onResolveDeletionRequest = viewModel::resolveDeletionRequest,
            onDeleteReview = viewModel::deleteReview,
            onCopied = onShowMessage
        )
    }

    if (showCampusDialog) {
        var campusName by remember(editingCampus) { mutableStateOf(editingCampus?.name.orEmpty()) }
        var campusAbbr by remember(editingCampus) { mutableStateOf(editingCampus?.abbreviation.orEmpty()) }
        val isEditing = editingCampus != null
        AlertDialog(
            onDismissRequest = { showCampusDialog = false },
            title = { Text(if (isEditing) "Edit Campus" else "Add Campus", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KartTextField(
                        value = campusName,
                        onValueChange = { campusName = it },
                        label = "Campus Name",
                        placeholder = "e.g. University of Ghana (Legon)"
                    )
                    KartTextField(
                        value = campusAbbr,
                        onValueChange = { campusAbbr = it },
                        label = "Abbreviation (Optional)",
                        placeholder = "e.g. UG"
                    )
                }
            },
            confirmButton = {
                KartPrimaryButton(
                    text = if (isEditing) "Save" else "Add",
                    enabled = campusName.trim().isNotBlank() && !state.actionLoading,
                    onClick = {
                        val targetId = editingCampus?.id
                        showCampusDialog = false
                        if (isEditing && !targetId.isNullOrBlank()) {
                            viewModel.updateCampus(targetId, campusName.trim(), campusAbbr.trim().ifBlank { null })
                        } else {
                            viewModel.addCampus(campusName.trim(), campusAbbr.trim().ifBlank { null })
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCampusDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPlanDialog) {
        var planName by remember(editingPlan) { mutableStateOf(editingPlan?.name.orEmpty()) }
        var planPrice by remember(editingPlan) {
            mutableStateOf(editingPlan?.let { formatEditableDecimal(it.price) } ?: "0")
        }
        var planMonths by remember(editingPlan) {
            mutableStateOf((editingPlan?.durationMonths ?: 1).toString())
        }
        var planFeatures by remember(editingPlan) {
            mutableStateOf(editingPlan?.features.orEmpty().joinToString("\n"))
        }
        val parsedPrice = planPrice.toDoubleOrNull()
        val parsedMonths = planMonths.toIntOrNull()
        val planValid = planName.trim().isNotBlank() &&
            parsedPrice != null && parsedPrice >= 0.0 &&
            parsedMonths != null && parsedMonths >= 1
        val isEditingPlan = editingPlan != null
        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text(if (isEditingPlan) "Edit Subscription Plan" else "Add Subscription Plan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KartTextField(
                        value = planName,
                        onValueChange = { planName = it },
                        label = "Plan Name",
                        placeholder = "e.g. Monthly"
                    )
                    KartTextField(
                        value = planPrice,
                        onValueChange = { planPrice = KartFormatters.sanitizeDecimalInput(it) },
                        label = "Price (GHS)",
                        placeholder = "0.00"
                    )
                    KartTextField(
                        value = planMonths,
                        onValueChange = { planMonths = it.filter(Char::isDigit) },
                        label = "Duration (Months)",
                        placeholder = "1"
                    )
                    KartTextField(
                        value = planFeatures,
                        onValueChange = { planFeatures = it },
                        label = "Features (one per line or comma-separated)",
                        placeholder = "Unlimited listings\nPriority support"
                    )
                }
            },
            confirmButton = {
                KartPrimaryButton(
                    text = if (isEditingPlan) "Save" else "Create",
                    enabled = planValid && !state.actionLoading,
                    onClick = {
                        val targetId = editingPlan?.id
                        val featList = planFeatures.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                        val priceVal = parsedPrice ?: 0.0
                        val monthsVal = parsedMonths ?: 1
                        showPlanDialog = false
                        if (isEditingPlan && !targetId.isNullOrBlank()) {
                            viewModel.updateSubscriptionPlan(targetId, planName.trim(), priceVal, monthsVal, featList)
                        } else {
                            viewModel.createSubscriptionPlan(planName.trim(), priceVal, monthsVal, featList)
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showPlanDialog = false }) { Text("Cancel") }
            }
        )
    }

    manualApproveWithdrawalId?.let { wid ->
        var manualRef by remember(wid) { mutableStateOf("") }
        var manualTxId by remember(wid) { mutableStateOf("") }
        var manualNotes by remember(wid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manualApproveWithdrawalId = null },
            title = { Text("Manual Withdrawal Approval", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Mark this withdrawal as manually paid outside Paystack.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KartThemeTokens.colors.textSecondary
                    )
                    KartTextField(
                        value = manualRef,
                        onValueChange = { manualRef = it },
                        label = "Manual Reference (Optional)",
                        placeholder = "e.g. MOMO-REF-123"
                    )
                    KartTextField(
                        value = manualTxId,
                        onValueChange = { manualTxId = it },
                        label = "Transaction ID (Optional)",
                        placeholder = "e.g. TXN-98765"
                    )
                    KartTextField(
                        value = manualNotes,
                        onValueChange = { manualNotes = it },
                        label = "Notes (Optional)",
                        placeholder = "Sent directly via MTN MoMo..."
                    )
                }
            },
            confirmButton = {
                KartPrimaryButton(
                    text = "Confirm Payout",
                    enabled = !state.actionLoading,
                    onClick = {
                        manualApproveWithdrawalId = null
                        viewModel.manualApproveWithdrawal(wid, manualRef, manualTxId, manualNotes)
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { manualApproveWithdrawalId = null }) { Text("Cancel") }
            }
        )
    }

    inspectedImage?.let { (url, title) ->
        AdminFullScreenImageDialog(
            imageUrl = url,
            title = title,
            accessToken = state.accessToken,
            onDismiss = { inspectedImage = null }
        )
    }

    rejectingKycId?.let { kycId ->
        var rejectNotes by remember(kycId) { mutableStateOf("") }
        val quickReasons = listOf("Blurry ID photo", "Selfie does not match ID", "Invalid or expired ID")
        AlertDialog(
            onDismissRequest = { rejectingKycId = null },
            title = { Text("Reject KYC Verification", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a clear reason for rejecting this verification request. The user will see this note when resubmitting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KartThemeTokens.colors.textSecondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickReasons) { preset ->
                            KartChip(
                                label = preset,
                                selected = rejectNotes == preset,
                                onClick = { rejectNotes = preset }
                            )
                        }
                    }
                    KartTextField(
                        value = rejectNotes,
                        onValueChange = { rejectNotes = it },
                        label = "Admin Notes (Required)",
                        placeholder = "Explain why the submission was rejected..."
                    )
                }
            },
            confirmButton = {
                KartPrimaryButton(
                    text = "Reject",
                    enabled = rejectNotes.trim().isNotBlank() && !state.actionLoading,
                    onClick = {
                        val notes = rejectNotes.trim()
                        rejectingKycId = null
                        viewModel.resolveVerification(kycId, false, notes)
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { rejectingKycId = null }) { Text("Cancel") }
            }
        )
    }

    pendingConfirm?.let { (title, message, action) ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                KartPrimaryButton(
                    text = "Confirm",
                    onClick = {
                        pendingConfirm = null
                        action()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

private fun formatEditableDecimal(value: Double): String =
    if (value % 1.0 == 0.0 && value <= 9_000_000_000_000.0 && value >= -9_000_000_000_000.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
    }

@Composable
private fun AdminTabStatsStrip(
    selectedTab: String,
    bundle: com.example.data.repository.AdminDashboardBundle,
    stats: AdminOverviewStats
) {
    val metrics: List<Triple<String, String, Color>> = when (selectedTab) {
        "Users" -> listOf(
            Triple("Total", "${stats.totalUsers}", KartPrimary),
            Triple("Verified", "${stats.verifiedUsers}", KartSuccess),
            Triple("Admins", "${stats.adminUsers}", KartPrimary),
            Triple("Banned", "${stats.bannedUsers}", KartDanger)
        )
        "Listings" -> listOf(
            Triple("Total", "${stats.totalListings}", KartPrimary),
            Triple("Active", "${stats.activeListings}", KartSuccess),
            Triple("Suspended", "${stats.suspendedListings}", KartDanger),
            Triple("Sold", "${stats.soldListings}", KartWarning)
        )
        "Orders" -> {
            val completed = bundle.orders.count { it.status.equals("Completed", true) || it.status.equals("Delivered", true) }
            listOf(
                Triple("Orders", "${stats.totalOrders}", KartPrimary),
                Triple("GMV", KartFormatters.formatCedis(stats.totalGmv), KartSuccess),
                Triple("Fees", KartFormatters.formatCedis(stats.totalPlatformFees), KartPrimary),
                Triple("Done", "$completed", KartSuccess)
            )
        }
        "KYC" -> {
            val approved = bundle.verifications.count { it.status.equals("Approved", true) || it.status.equals("Verified", true) }
            val rejected = bundle.verifications.count { it.status.equals("Rejected", true) }
            listOf(
                Triple("Total", "${bundle.verifications.size}", KartPrimary),
                Triple("Pending", "${stats.pendingKyc}", KartWarning),
                Triple("Approved", "$approved", KartSuccess),
                Triple("Rejected", "$rejected", KartDanger)
            )
        }
        "Withdrawals" -> {
            val pendingVol = bundle.withdrawals.filter { AdminConsoleHelpers.isPendingStatus(it.status) }.sumOf { it.amount }
            val approved = bundle.withdrawals.count { it.status.equals("Approved", true) || it.status.equals("Completed", true) }
            val rejected = bundle.withdrawals.count { it.status.equals("Rejected", true) }
            listOf(
                Triple("Pending", "${stats.pendingWithdrawals}", KartWarning),
                Triple("Queued", KartFormatters.formatCedis(pendingVol), KartPrimary),
                Triple("Approved", "$approved", KartSuccess),
                Triple("Rejected", "$rejected", KartDanger)
            )
        }
        "Refunds" -> {
            val approved = bundle.refunds.count { it.status.equals("Approved", true) || it.status.equals("Refunded", true) }
            val rejected = bundle.refunds.count { it.status.equals("Rejected", true) }
            listOf(
                Triple("Total", "${bundle.refunds.size}", KartPrimary),
                Triple("Pending", "${stats.pendingRefunds}", KartWarning),
                Triple("Refunded", "$approved", KartSuccess),
                Triple("Rejected", "$rejected", KartDanger)
            )
        }
        "Reports" -> {
            val prodReps = bundle.reports.count { it.resolvedTargetType.equals("product", true) || it.resolvedTargetType.equals("listing", true) }
            val userReps = bundle.reports.count { it.resolvedTargetType.equals("user", true) || it.resolvedTargetType.equals("profile", true) }
            listOf(
                Triple("Total", "${bundle.reports.size}", KartPrimary),
                Triple("Open", "${stats.openReports}", KartWarning),
                Triple("Listings", "$prodReps", KartDanger),
                Triple("Users", "$userReps", KartPrimary)
            )
        }
        "Deletions" -> {
            val approved = bundle.deletionRequests.count { it.status.equals("approved", true) }
            val rejected = bundle.deletionRequests.count { it.status.equals("rejected", true) }
            listOf(
                Triple("Total", "${bundle.deletionRequests.size}", KartPrimary),
                Triple("Pending", "${stats.pendingDeletions}", KartWarning),
                Triple("Approved", "$approved", KartSuccess),
                Triple("Rejected", "$rejected", KartDanger)
            )
        }
        "Reviews" -> {
            val avg = if (bundle.reviews.isEmpty()) 0.0 else bundle.reviews.map { it.rating }.average()
            val fiveStar = bundle.reviews.count { it.rating == 5 }
            val lowStar = bundle.reviews.count { it.rating <= 2 }
            listOf(
                Triple("Total", "${stats.totalReviews}", KartPrimary),
                Triple("Avg", String.format("%.1f★", avg), KartWarning),
                Triple("5-Star", "$fiveStar", KartSuccess),
                Triple("Low", "$lowStar", KartDanger)
            )
        }
        "Ads" -> {
            val views = bundle.advertisements.sumOf { it.views ?: 0 }
            val clicks = bundle.advertisements.sumOf { it.clicks ?: 0 }
            listOf(
                Triple("Total", "${bundle.advertisements.size}", KartPrimary),
                Triple("Active", "${stats.activeAds}", KartSuccess),
                Triple("Views", "$views", KartPrimary),
                Triple("Clicks", "$clicks", KartWarning)
            )
        }
        "Plans" -> {
            val activeSubs = bundle.subscriptions.count { it.status.equals("Active", true) }
            val pendingSubs = bundle.subscriptions.count { AdminConsoleHelpers.isPendingStatus(it.status) }
            listOf(
                Triple("Plans", "${bundle.subscriptionPlans.size}", KartPrimary),
                Triple("Active", "$activeSubs", KartSuccess),
                Triple("Pending", "$pendingSubs", KartWarning),
                Triple("Subs", "${bundle.subscriptions.size}", KartPrimary)
            )
        }
        "Campuses" -> listOf(
            Triple("Campuses", "${bundle.campuses.size}", KartPrimary),
            Triple("Users", "${stats.totalUsers}", KartSuccess),
            Triple("Listings", "${stats.activeListings}", KartPrimary)
        )
        "Site Settings" -> listOf(
            Triple("Buy Now", if (bundle.buyNowEnabled) "Active" else "Off", if (bundle.buyNowEnabled) KartSuccess else KartWarning),
            Triple("Store", if (bundle.isMaintenanceMode) "Maint." else "Live", if (bundle.isMaintenanceMode) KartWarning else KartSuccess),
            Triple("Daily", KartFormatters.formatCedis(bundle.platformFlags.promoDailyPrice), KartPrimary)
        )
        else -> emptyList()
    }
    if (metrics.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        metrics.forEach { (label, value, accent) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = KartThemeTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminTabSkeleton(selectedTab: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
        when (selectedTab) {
            "Overview" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ShimmerBox(modifier = Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)))
                    ShimmerBox(modifier = Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ShimmerBox(modifier = Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)))
                    ShimmerBox(modifier = Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)))
                }
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(18.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ShimmerBox(modifier = Modifier.weight(1f).height(180.dp).clip(RoundedCornerShape(18.dp)))
                    ShimmerBox(modifier = Modifier.weight(1f).height(180.dp).clip(RoundedCornerShape(18.dp)))
                }
            }
            "Site Settings" -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(16.dp)))
            }
            "Users" -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)))
                repeat(4) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            ShimmerBox(modifier = Modifier.size(40.dp).clip(CircleShape))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp))
                                ShimmerBox(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp))
                            }
                            ShimmerBox(modifier = Modifier.size(width = 56.dp, height = 24.dp).clip(RoundedCornerShape(999.dp)))
                        }
                    }
                }
            }
            "Listings", "Orders", "Ads" -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)))
                repeat(4) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            ShimmerBox(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(16.dp))
                                ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp))
                            }
                            ShimmerBox(modifier = Modifier.size(width = 60.dp, height = 24.dp).clip(RoundedCornerShape(999.dp)))
                        }
                    }
                }
            }
            "KYC", "Withdrawals", "Refunds", "Deletions" -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)))
                repeat(3) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
                                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp))
                                }
                                ShimmerBox(modifier = Modifier.size(width = 64.dp, height = 24.dp).clip(RoundedCornerShape(999.dp)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ShimmerBox(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)))
                                ShimmerBox(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)))
                            }
                        }
                    }
                }
            }
            else -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)))
                repeat(4) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp))
                            ShimmerBox(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminEmptyTabState(title: String, subtitle: String) {
    EmptyStateView(
        asset = EmptyStateAsset.SEARCH,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
}

@Composable
private fun AdminMetricRow(
    label: String,
    value: String,
    subtitle: String? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (highlight) KartWarning else KartPrimary
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open $label",
                        tint = KartThemeTokens.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminCard(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                leading?.invoke()
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                badge?.invoke()
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View details",
                        tint = KartThemeTokens.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun StaticInfoScreen(
    slug: String,
    onBack: () -> Unit
) {
    val (title, sections) = remember(slug) {
        when (slug) {
            "about" -> "About Kart" to listOf(
                "Built for Campus Commerce" to "Kart (kart.cx) is Ghana's trusted student-to-student campus marketplace. Buy and sell textbooks, laptops, clothing, dorm essentials, and services right on your university campus.",
                "Escrow-First Trust" to "Every paid order on Kart is protected by Escrow. Funds are safely held until the buyer physically inspects the item on campus and confirms delivery with their Delivery PIN or OTP.",
                "Verified Campus Community" to "ID and selfie verification badges help you recognize trusted buyers and sellers across University of Ghana, KNUST, UCC, Ashesi, UPSA, GIMPA, and more."
            )
            "faq" -> "Frequently Asked Questions" to listOf(
                "How does Kart Escrow work?" to "When you pay via Mobile Money, Card, or Kart Wallet, funds stay safely held by Kart until you meet the seller, inspect your item, and confirm delivery with your 6-digit PIN or OTP.",
                "How do sellers withdraw earnings?" to "Once a buyer confirms delivery, earnings move to your Available Wallet Balance. Withdraw anytime to MTN MoMo, Telecel Cash, AT Money, or your Ghanaian bank account.",
                "Can I request a refund?" to "Yes. If an item is not as described or the seller does not deliver, tap 'Request Refund' on your order page before confirming delivery."
            )
            "safety" -> "Campus Safety Guide" to listOf(
                "Meet in Public Campus Spots" to "Always arrange handoffs in well-lit, busy campus locations such as libraries, student centers, or hostel receptions during daylight hours.",
                "Never Pay Outside Kart Escrow" to "Keep payments inside Kart so your funds remain protected until you inspect your purchase.",
                "Inspect Before Confirming" to "Check electronics, textbooks, and clothing thoroughly before entering your Delivery PIN or sharing your delivery OTP."
            )
            "return-policy" -> "Return & Refund Policy" to listOf(
                "Escrow Dispute Window" to "Buyers can open a refund request directly from the Order Details page whenever an item differs materially from its listing or is never handed over.",
                "Fair Resolution" to "Kart's moderation team reviews chat logs, listing photos, and order history to issue fair refunds back to your Kart Wallet or original payment source."
            )
            "terms" -> "Terms of Service" to listOf(
                "Marketplace Eligibility" to "Users must provide accurate account details and list only lawful goods and campus services permitted under university and Ghanaian regulations.",
                "Escrow & Platform Fees" to "Kart applies transparent service fees on completed marketplace transactions to maintain escrow security, verification, and campus support."
            )
            else -> "Privacy Policy" to listOf(
                "Data Protection" to "Kart collects only the account, campus, and transaction information necessary to operate secure peer-to-peer commerce and identity verification.",
                "Account Deletion" to "You may request permanent deletion of your Kart account and associated personal data at any time from Settings → Delete Account."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = title, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sections) { (heading, body) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = KartThemeTokens.colors.textSecondary)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
