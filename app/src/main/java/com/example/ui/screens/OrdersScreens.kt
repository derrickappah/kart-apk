package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.domain.model.Order
import com.example.ui.components.DeliveryVerificationDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.RefundRequestDialog
import com.example.ui.components.ReviewSubmissionDialog
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartDangerContainer
import com.example.ui.theme.KartInfoContainer
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import com.example.ui.theme.KartWarningContainer
import com.example.ui.viewmodel.OrdersViewModel

@Composable
fun OrdersListScreen(
    viewModel: OrdersViewModel,
    userId: String,
    asSeller: Boolean = false,
    onBack: () -> Unit,
    onOrderClick: (String) -> Unit,
    onExploreShop: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var statusFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Pending", "Paid", "Shipped", "Delivered", "Completed", "Disputed", "Refunded", "Cancelled")

    LaunchedEffect(userId, asSeller) {
        viewModel.loadOrders(userId, asSeller)
    }

    val filteredOrders = remember(state.orders, statusFilter) {
        if (statusFilter == "All") state.orders
        else state.orders.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(
            title = if (asSeller) "Seller Orders" else "My Orders",
            onBack = onBack
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { f ->
                KartChip(label = f, selected = statusFilter == f, onClick = { statusFilter = f })
            }
        }

        when {
            state.loading -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    repeat(4) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(98.dp)) }
                }
            }
            state.error != null && state.orders.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ERROR,
                    title = "Could not load orders",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadOrders(userId, asSeller) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            filteredOrders.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ORDERS,
                    title = if (asSeller) "No incoming orders yet" else "No orders found",
                    subtitle = if (asSeller) "Orders from campus buyers will appear here with escrow tracking."
                    else "Explore campus listings and check out safely with Kart Escrow.",
                    actionLabel = if (asSeller) null else "Browse Marketplace",
                    onActionClick = if (asSeller) null else onExploreShop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderSummaryCard(order = order, onClick = { onOrderClick(order.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryCard(order: Order, onClick: () -> Unit) {
    val tokens = KartThemeTokens.colors
    val (bg, fg) = orderStatusColors(order.status)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            AsyncImage(
                model = order.product?.primaryImage,
                contentDescription = order.product?.title ?: "Order item",
                placeholder = painterResource(R.drawable.img_placeholder),
                error = painterResource(R.drawable.img_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = order.product?.title ?: "Order #${order.id.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = KartFormatters.formatCedis(order.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = KartPrimary
                )
                Text(
                    text = "Qty ${order.quantity ?: 1} • ${KartFormatters.timeAgo(order.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            StatusPill(text = order.status ?: "Pending", containerColor = bg, contentColor = fg)
        }
    }
}

@Composable
fun OrderDetailScreen(
    orderId: String,
    currentUserId: String,
    viewModel: OrdersViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    var showDeliveryModal by remember { mutableStateOf(false) }
    var showBuyerQrScanner by remember { mutableStateOf(false) }
    var showRefundModal by remember { mutableStateOf(false) }
    var showCancelModal by remember { mutableStateOf(false) }
    var showSellerQrModal by remember { mutableStateOf(false) }
    var showReviewModal by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        viewModel.loadOrderDetail(orderId)
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
        KartSubScreenTopBar(title = "Order Details", onBack = onBack)

        if (state.loading && state.selectedOrder == null) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(20.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(100.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp))
            }
        } else if (state.selectedOrder == null) {
            EmptyStateView(
                asset = EmptyStateAsset.ERROR,
                title = "Order not found",
                subtitle = state.error ?: "We couldn't locate this order.",
                actionLabel = "Go Back",
                onActionClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            val order = state.selectedOrder!!
            val isBuyer = order.buyerId == currentUserId
            val isSeller = order.sellerId == currentUserId
            val statusLower = order.status?.lowercase().orEmpty()
            val (statusBg, statusFg) = orderStatusColors(order.status)

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (isBuyer && state.hasReviewedOrder) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = KartSuccessContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(KartLucideIcons.CheckCircle, contentDescription = null, tint = KartSuccess)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Thank you! Your review has been published to the seller's profile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KartSuccess
                                )
                            }
                        }
                    }
                }

                // Header & Status
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Order #${order.id.take(8).uppercase()}", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                                StatusPill(order.status ?: "Pending", statusBg, statusFg)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = order.product?.primaryImage,
                                    contentDescription = order.product?.title,
                                    placeholder = painterResource(R.drawable.img_placeholder),
                                    error = painterResource(R.drawable.img_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        order.product?.title ?: "Campus Item",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        KartFormatters.formatCedis(order.totalAmount),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = KartPrimary
                                    )
                                    Text(
                                        "Qty: ${order.quantity ?: 1} • ${order.paymentMethod ?: "Escrow"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Escrow Progress Timeline
                item {
                    val steps = listOf("Paid", "Shipped", "Delivered", "Completed")
                    val currentStepIdx = when (statusLower) {
                        "paid" -> 0
                        "shipped" -> 1
                        "delivered" -> 2
                        "completed" -> 3
                        else -> -1
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Escrow Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            steps.forEachIndexed { idx, step ->
                                val reached = idx <= currentStepIdx
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (reached) KartPrimary else tokens.subtleDivider),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (reached) {
                                            Icon(
                                                KartLucideIcons.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                                        color = if (reached) tokens.textPrimary else tokens.textMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Contextual Buyer & Seller Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hasRefundBlock = state.hasPendingRefund || state.hasApprovedRefund
                        if (state.hasApprovedRefund || statusLower == "refunded") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KartSuccessContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(KartLucideIcons.CheckCircle, contentDescription = null, tint = KartSuccess)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Refund has been approved and processed by Kart Escrow.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KartSuccess
                                    )
                                }
                            }
                        } else if (state.hasPendingRefund || statusLower == "disputed") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KartWarningContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(KartLucideIcons.Flag, contentDescription = null, tint = KartWarning)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "A refund dispute is currently under review by Kart Escrow.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KartWarning
                                    )
                                }
                            }
                        }

                        if (isBuyer && statusLower == "pending") {
                            KartOutlineButton(
                                text = "Cancel Pending Order",
                                enabled = !state.actionLoading,
                                borderColor = KartDanger,
                                contentColor = KartDanger,
                                onClick = { showCancelModal = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isSeller && statusLower == "paid" && !hasRefundBlock) {
                            KartPrimaryButton(
                                text = "Mark as Shipped / Ready for Handoff",
                                icon = KartLucideIcons.Truck,
                                loading = state.actionLoading,
                                onClick = { viewModel.markShipped(order.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isSeller && statusLower == "shipped" && !hasRefundBlock) {
                            KartPrimaryButton(
                                text = "Show Handover QR Code",
                                icon = KartLucideIcons.QrCode,
                                enabled = !state.actionLoading,
                                onClick = {
                                    showSellerQrModal = true
                                    viewModel.generateDeliveryQr(order.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isBuyer && statusLower == "paid" && !hasRefundBlock) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KartInfoContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(KartLucideIcons.Truck, contentDescription = null, tint = KartPrimary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Waiting for the seller to mark this order as shipped / ready for handoff before delivery confirmation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartPrimary
                                    )
                                }
                            }
                        }

                        if (isBuyer && statusLower == "shipped" && !hasRefundBlock) {
                            KartPrimaryButton(
                                text = "Confirm Delivery & Release Escrow",
                                icon = KartLucideIcons.ShieldCheck,
                                loading = state.actionLoading,
                                onClick = {
                                    viewModel.clearDeliveryModalState()
                                    showDeliveryModal = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isBuyer && statusLower in setOf("paid", "shipped") && !hasRefundBlock) {
                            KartOutlineButton(
                                text = "Request Refund",
                                enabled = !state.actionLoading,
                                borderColor = KartDanger,
                                contentColor = KartDanger,
                                onClick = { showRefundModal = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isBuyer && statusLower in setOf("delivered", "completed") && !state.hasReviewedOrder) {
                            KartPrimaryButton(
                                text = "Rate Seller & Write Review",
                                icon = KartLucideIcons.StarFilled,
                                enabled = !state.actionLoading,
                                onClick = { showReviewModal = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Breakdown
                item {
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(Modifier.height(10.dp))
                    Text("Reference: ${order.paymentReference ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                }
            }

            if (showDeliveryModal) {
                DeliveryVerificationDialog(
                    isSubmitting = state.actionLoading,
                    isSendingOtp = state.otpSending,
                    otpSent = state.otpSent,
                    errorMessage = state.deliveryActionError,
                    statusMessage = state.deliveryActionNotice,
                    onClearStatus = { viewModel.clearDeliveryMessages() },
                    onDismiss = {
                        viewModel.clearDeliveryModalState()
                        showDeliveryModal = false
                    },
                    onSendOtp = { viewModel.sendDeliveryOtp(order.id) },
                    onOpenQrScanner = {
                        viewModel.clearDeliveryModalState()
                        showDeliveryModal = false
                        showBuyerQrScanner = true
                    },
                    onConfirm = { code, mode ->
                        viewModel.confirmDelivery(order.id, code, mode) {
                            showDeliveryModal = false
                        }
                    }
                )
            }

            if (showBuyerQrScanner) {
                com.example.ui.components.BuyerQrScannerDialog(
                    isSubmitting = state.actionLoading,
                    onQrScanned = { qrToken ->
                        viewModel.confirmDelivery(order.id, qrToken, "qr") {
                            showBuyerQrScanner = false
                        }
                    },
                    onDismiss = { showBuyerQrScanner = false }
                )
            }

            if (showSellerQrModal) {
                com.example.ui.components.SellerDeliveryQrDialog(
                    qrData = state.deliveryQr,
                    loading = state.qrLoading,
                    error = state.qrError,
                    onRegenerate = { viewModel.generateDeliveryQr(order.id) },
                    onDismiss = {
                        showSellerQrModal = false
                        viewModel.clearDeliveryQr()
                    }
                )
            }

            if (showCancelModal) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { if (!state.actionLoading) showCancelModal = false },
                    title = { Text("Cancel Pending Order?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Are you sure you want to cancel this unpaid order? You can still repurchase the item later if it remains in stock.")
                    },
                    confirmButton = {
                        KartPrimaryButton(
                            text = "Cancel Order",
                            loading = state.actionLoading,
                            enabled = !state.actionLoading,
                            containerColor = KartDanger,
                            onClick = {
                                viewModel.cancelOrder(order.id) {
                                    showCancelModal = false
                                }
                            }
                        )
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !state.actionLoading,
                            onClick = { showCancelModal = false }
                        ) { Text("Keep Order") }
                    }
                )
            }

            if (showRefundModal) {
                RefundRequestDialog(
                    isSubmitting = state.actionLoading,
                    onDismiss = { if (!state.actionLoading) showRefundModal = false },
                    onSubmit = { reason ->
                        viewModel.requestRefund(order.id, reason) {
                            showRefundModal = false
                        }
                    }
                )
            }

            if (showReviewModal) {
                ReviewSubmissionDialog(
                    title = order.seller?.displayName ?: "Seller",
                    isSubmitting = state.actionLoading,
                    onDismiss = { showReviewModal = false },
                    onSubmit = { rating, formatted ->
                        viewModel.submitOrderReview(order, rating, formatted) {
                            showReviewModal = false
                        }
                    }
                )
            }
        }
    }
}

private fun orderStatusColors(status: String?): Pair<Color, Color> = when (status?.lowercase()) {
    "completed", "delivered" -> KartSuccessContainer to KartSuccess
    "shipped", "paid" -> KartInfoContainer to KartPrimary
    "refunded", "cancelled", "disputed" -> KartDangerContainer to KartDanger
    else -> KartWarningContainer to KartWarning
}
