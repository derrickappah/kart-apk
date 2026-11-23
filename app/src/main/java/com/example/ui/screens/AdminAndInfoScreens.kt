package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StatusPill
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
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf("Overview", "Users", "Listings", "Orders", "KYC", "Withdrawals", "Refunds", "Reports", "Campuses")
    var selectedTab by remember { mutableStateOf("Overview") }
    var pendingConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

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
    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Kart Admin Console", onBack = onBack)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs) { t ->
                KartChip(label = t, selected = selectedTab == t, onClick = { selectedTab = t })
            }
        }

        if (state.loading && state.bundle == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                repeat(4) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(88.dp)) }
            }
        } else {
            val bundle = state.bundle
            val usersById = bundle?.usersById.orEmpty()
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    "Overview" -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AdminMetricRow("Loaded Users", "${bundle?.users?.size ?: 0}")
                            AdminMetricRow("Active Listings", "${bundle?.products?.count { it.isActiveListing } ?: 0}")
                            AdminMetricRow("Recent Orders", "${bundle?.orders?.size ?: 0}")
                            AdminMetricRow("Pending KYC Verifications", "${bundle?.verifications?.count { it.status.equals("Pending", true) } ?: 0}")
                            AdminMetricRow("Pending Withdrawals", "${bundle?.withdrawals?.count { it.status.equals("Pending", true) } ?: 0}")
                            AdminMetricRow("Pending Refund Disputes", "${bundle?.refunds?.count { it.status.equals("Pending", true) } ?: 0}")
                            AdminMetricRow("User Reports", "${bundle?.reports?.size ?: 0}")
                        }
                    }
                    "Users" -> items(bundle?.users.orEmpty(), key = { it.id }) { user ->
                        val isSelf = currentUserId.isNotBlank() && user.id == currentUserId
                        AdminCard(
                            title = user.resolvedName,
                            subtitle = "${user.email ?: ""} • ${user.campus ?: ""}" + if (user.isAdmin == true) " • Admin" else ""
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
                    "Listings" -> items(bundle?.products.orEmpty(), key = { it.id }) { p ->
                        AdminCard(title = p.title, subtitle = "${KartFormatters.formatCedis(p.price)} • ${p.status ?: "Active"}") {
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
                                        ) { viewModel.updateProductStatus(p.id, "Suspended") }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    "Orders" -> items(bundle?.orders.orEmpty(), key = { it.id }) { o ->
                        AdminCard(
                            title = o.product?.title ?: "Order #${o.id.take(8)}",
                            subtitle = "${KartFormatters.formatCedis(o.totalAmount)} • ${o.status ?: "Pending"}"
                        ) {}
                    }
                    "KYC" -> items(bundle?.verifications.orEmpty(), key = { it.id }) { v ->
                        val applicant = v.userId?.let { usersById[it] }
                        val isPending = v.status.isNullOrBlank() || v.status.equals("Pending", true)
                        AdminCard(
                            title = v.fullName?.takeIf { it.isNotBlank() }
                                ?: applicant?.resolvedName
                                ?: "KYC #${v.id.take(8)}",
                            subtitle = listOfNotNull(
                                applicant?.email,
                                v.studentIdNumber?.takeIf { it.isNotBlank() }?.let { "ID: $it" },
                                v.campus?.takeIf { it.isNotBlank() } ?: applicant?.campus,
                                "Status: ${v.status ?: "Pending"}"
                            ).joinToString(" • ")
                        ) {
                            if (!v.idImageUrl.isNullOrBlank() || !v.selfieImageUrl.isNullOrBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (!v.idImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = v.idImageUrl,
                                            contentDescription = "Applicant ID photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                    if (!v.selfieImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = v.selfieImageUrl,
                                            contentDescription = "Applicant selfie photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                }
                            }
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
                                        onClick = {
                                            pendingConfirm = Triple(
                                                "Reject KYC Verification?",
                                                "Reject this verification request so the user must resubmit valid documents?"
                                            ) { viewModel.resolveVerification(v.id, false) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                val approved = v.status.equals("Approved", true) || v.status.equals("Verified", true)
                                StatusPill(
                                    text = v.status ?: "Resolved",
                                    containerColor = if (approved) KartSuccessContainer else KartDangerContainer,
                                    contentColor = if (approved) KartSuccess else KartDanger
                                )
                            }
                        }
                    }
                    "Withdrawals" -> items(bundle?.withdrawals.orEmpty(), key = { it.id }) { w ->
                        val requester = w.userId?.let { usersById[it] }
                        val isPending = w.status.isNullOrBlank() || w.status.equals("Pending", true)
                        AdminCard(
                            title = "${KartFormatters.formatCedis(w.amount)} • ${requester?.resolvedName ?: "Seller"}",
                            subtitle = listOfNotNull(
                                requester?.email,
                                w.payoutMethod ?: "MoMo",
                                w.accountDetails?.takeIf { it.isNotBlank() },
                                "Status: ${w.status ?: "Pending"}"
                            ).joinToString(" • ")
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
                            } else {
                                val approved = w.status.equals("Approved", true) || w.status.equals("Completed", true)
                                StatusPill(
                                    text = w.status ?: "Resolved",
                                    containerColor = if (approved) KartSuccessContainer else KartDangerContainer,
                                    contentColor = if (approved) KartSuccess else KartDanger
                                )
                            }
                        }
                    }
                    "Refunds" -> items(bundle?.refunds.orEmpty(), key = { it.id }) { r ->
                        val isPending = r.status.isNullOrBlank() || r.status.equals("Pending", true)
                        AdminCard(title = "Refund #${r.id.take(8)}", subtitle = "${r.reason ?: "Dispute"} • ${r.status ?: "Pending"}") {
                            if (isPending) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    KartPrimaryButton(
                                        text = "Refund Buyer",
                                        onClick = {
                                            pendingConfirm = Triple(
                                                "Issue Buyer Refund?",
                                                "Approve this refund dispute and return escrow funds to the buyer?"
                                            ) { viewModel.resolveRefund(r.orderId.orEmpty(), r.id, true) }
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
                            } else {
                                val approved = r.status.equals("Approved", true) || r.status.equals("Refunded", true)
                                StatusPill(
                                    text = r.status ?: "Resolved",
                                    containerColor = if (approved) KartSuccessContainer else KartWarningContainer,
                                    contentColor = if (approved) KartSuccess else KartWarning
                                )
                            }
                        }
                    }
                    "Reports" -> items(bundle?.reports.orEmpty(), key = { it.id }) { rep ->
                        AdminCard(title = rep.reason ?: "Report", subtitle = "${rep.targetType ?: ""} • ${rep.details ?: ""}") {}
                    }
                    "Campuses" -> items(bundle?.campuses.orEmpty(), key = { it.name }) { c ->
                        AdminCard(title = c.name, subtitle = c.abbreviation ?: "Active Campus") {}
                    }
                }
            }
        }
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
private fun AdminMetricRow(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = KartPrimary)
        }
    }
}

@Composable
private fun AdminCard(title: String, subtitle: String, actions: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
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
                "How does Kart Escrow work?" to "When you pay via Mobile Money, Card, or Kart Wallet, funds stay safely held by Kart until you meet the seller, inspect your item, and confirm delivery with your 4-digit PIN or OTP.",
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
