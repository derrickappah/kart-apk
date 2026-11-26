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
    onOpenProduct: (String) -> Unit = {},
    onOpenSeller: (String) -> Unit = {},
    onOpenOrder: (String) -> Unit = {},
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(
        "Overview", "Users", "Listings", "Orders", "KYC",
        "Withdrawals", "Refunds", "Reports", "Deletions", "Reviews", "Ads", "Campuses", "Site Settings"
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
                    "Deletions" -> bundle?.deletionRequests?.count { AdminConsoleHelpers.isPendingStatus(it.status) } ?: 0
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
                        val filtered = AdminConsoleHelpers.filterUsers(bundle?.users.orEmpty(), searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching users", "Try adjusting your search or filter.") }
                        } else {
                            items(filtered, key = { it.id }) { user ->
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
                        }
                    }

                    "Listings" -> {
                        val filtered = AdminConsoleHelpers.filterProducts(bundle?.products.orEmpty(), usersById, searchQuery, selectedFilter)
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching listings", "Try adjusting your search or filter.") }
                        } else {
                            items(filtered, key = { it.id }) { p ->
                                val seller = p.sellerId?.let { usersById[it] }
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
                                                model = url,
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
                                            enabled = !p.status.equals("Active", true),
                                            onClick = { viewModel.updateProductStatus(p.id, "Active") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        KartOutlineButton(
                                            text = "Suspend",
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
                                                model = url,
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
                                AdminCard(
                                    title = "${KartFormatters.formatCedis(w.amount)} • ${requester?.resolvedName ?: "Seller"}",
                                    subtitle = listOfNotNull(
                                        requester?.email,
                                        w.payoutMethod ?: "MoMo",
                                        w.accountDetails?.takeIf { it.isNotBlank() }
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
                                                onClick = {
                                                    pendingConfirm = Triple(
                                                        "Approve Withdrawal?",
                                                        "Release ${KartFormatters.formatCedis(w.amount)} via ${w.payoutMethod ?: "MoMo"} to ${w.accountDetails ?: "the listed account"}?"
                                                    ) { viewModel.resolveWithdrawal(w.id, true) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            KartOutlineButton(
                                                text = "Reject",
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
                                    subtitle = "${ad.adType ?: "Boost"} • ${ad.views ?: 0} views • ${ad.clicks ?: 0} clicks • ${KartFormatters.formatCedis(ad.price)}",
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
                        if (filtered.isEmpty()) {
                            item { AdminEmptyTabState("No matching campuses", "Try adjusting your search or add a new campus.") }
                        } else {
                            items(filtered, key = { it.id ?: it.name }) { c ->
                                val userCount = bundle?.users?.count { it.campus.equals(c.name, true) } ?: 0
                                val listingCount = bundle?.products?.count { it.campus.equals(c.name, true) && it.isActiveListing } ?: 0
                                AdminCard(
                                    title = c.name,
                                    subtitle = "${c.abbreviation ?: "Campus"} • $userCount users • $listingCount active listings",
                                    onClick = { activeDetail = AdminDetailTarget.Campus(c) }
                                ) {}
                            }
                        }
                    }

                    "Site Settings" -> item {
                        val buyNowEnabled = bundle?.buyNowEnabled ?: true
                        val maintenanceMode = bundle?.isMaintenanceMode ?: false
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
                                                    ) { viewModel.setBuyNowEnabled(false, onBuyNowSettingChanged) }
                                                } else {
                                                    viewModel.setBuyNowEnabled(true, onBuyNowSettingChanged)
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
                                                ) { viewModel.setMaintenanceMode(nextEnabled) }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KartWarning)
                                        )
                                    }
                                }
                            }

                            var boostInput by remember(bundle?.promoBoostRate) {
                                mutableStateOf(KartFormatters.formatPrice(bundle?.promoBoostRate ?: 15.0))
                            }
                            var featuredInput by remember(bundle?.promoFeaturedRate) {
                                mutableStateOf(KartFormatters.formatPrice(bundle?.promoFeaturedRate ?: 35.0))
                            }
                            var campusAdInput by remember(bundle?.promoCampusAdRate) {
                                mutableStateOf(KartFormatters.formatPrice(bundle?.promoCampusAdRate ?: 65.0))
                            }
                            val parsedBoost = boostInput.toDoubleOrNull()
                            val parsedFeatured = featuredInput.toDoubleOrNull()
                            val parsedCampusAd = campusAdInput.toDoubleOrNull()
                            val ratesValid = parsedBoost != null && parsedBoost > 0.0 &&
                                parsedFeatured != null && parsedFeatured > 0.0 &&
                                parsedCampusAd != null && parsedCampusAd > 0.0

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
                                        text = "Promotion Rates (GHS)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                    Text(
                                        text = "Configure pricing for seller listing promotions across Kart.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                    KartTextField(
                                        value = boostInput,
                                        onValueChange = { boostInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Boost Rate (GHS)",
                                        placeholder = "15.00"
                                    )
                                    KartTextField(
                                        value = featuredInput,
                                        onValueChange = { featuredInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Featured Rate (GHS)",
                                        placeholder = "35.00"
                                    )
                                    KartTextField(
                                        value = campusAdInput,
                                        onValueChange = { campusAdInput = KartFormatters.sanitizeDecimalInput(it) },
                                        label = "Campus Ad Rate (GHS)",
                                        placeholder = "65.00"
                                    )
                                    KartPrimaryButton(
                                        text = "Save Promotion Rates",
                                        enabled = !state.actionLoading && ratesValid,
                                        onClick = {
                                            val b = parsedBoost ?: return@KartPrimaryButton
                                            val f = parsedFeatured ?: return@KartPrimaryButton
                                            val c = parsedCampusAd ?: return@KartPrimaryButton
                                            pendingConfirm = Triple(
                                                "Update Promotion Rates?",
                                                "Set Boost to ${KartFormatters.formatCedis(b)}, Featured to ${KartFormatters.formatCedis(f)}, and Campus Ad to ${KartFormatters.formatCedis(c)}?"
                                            ) {
                                                viewModel.updatePromotionRates(b, f, c)
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
            onUpdateProductStatus = viewModel::updateProductStatus,
            onDeleteProduct = viewModel::deleteProduct,
            onResolveVerification = viewModel::resolveVerification,
            onRequestRejectKyc = { kycId -> rejectingKycId = kycId },
            onResolveWithdrawal = viewModel::resolveWithdrawal,
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
            Triple("Admins", "${bundle.users.count { it.isAdmin == true }}", KartPrimary),
            Triple("Banned", "${stats.bannedUsers}", KartDanger)
        )
        "Listings" -> {
            val sold = bundle.products.count { it.status.equals("Sold", true) || it.isOutOfStock }
            listOf(
                Triple("Total", "${bundle.products.size}", KartPrimary),
                Triple("Active", "${stats.activeListings}", KartSuccess),
                Triple("Suspended", "${stats.suspendedListings}", KartDanger),
                Triple("Sold", "$sold", KartWarning)
            )
        }
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
            val pending = bundle.deletionRequests.count { AdminConsoleHelpers.isPendingStatus(it.status) }
            val approved = bundle.deletionRequests.count { it.status.equals("approved", true) }
            val rejected = bundle.deletionRequests.count { it.status.equals("rejected", true) }
            listOf(
                Triple("Total", "${bundle.deletionRequests.size}", KartPrimary),
                Triple("Pending", "$pending", KartWarning),
                Triple("Approved", "$approved", KartSuccess),
                Triple("Rejected", "$rejected", KartDanger)
            )
        }
        "Reviews" -> {
            val avg = if (bundle.reviews.isEmpty()) 0.0 else bundle.reviews.map { it.rating }.average()
            val fiveStar = bundle.reviews.count { it.rating == 5 }
            val lowStar = bundle.reviews.count { it.rating <= 2 }
            listOf(
                Triple("Total", "${bundle.reviews.size}", KartPrimary),
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
        "Campuses" -> listOf(
            Triple("Campuses", "${bundle.campuses.size}", KartPrimary),
            Triple("Users", "${stats.totalUsers}", KartSuccess),
            Triple("Listings", "${stats.activeListings}", KartPrimary)
        )
        "Site Settings" -> listOf(
            Triple("Buy Now", if (bundle.buyNowEnabled) "Active" else "Off", if (bundle.buyNowEnabled) KartSuccess else KartWarning),
            Triple("Store", if (bundle.isMaintenanceMode) "Maint." else "Live", if (bundle.isMaintenanceMode) KartWarning else KartSuccess),
            Triple("Boost", KartFormatters.formatCedis(bundle.promoBoostRate), KartPrimary)
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
