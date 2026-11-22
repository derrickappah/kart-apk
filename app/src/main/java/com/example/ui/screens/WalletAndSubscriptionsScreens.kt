package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSecondaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
import com.example.ui.components.ShimmerBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.KartCharcoal
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartPrimaryDark
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun WalletScreen(
    userId: String,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors

    LaunchedEffect(userId) { viewModel.loadWallet(userId) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Kart Wallet", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Balance Hero Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(listOf(KartCharcoal, KartPrimaryDark, KartPrimary))
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Available Balance", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            text = KartFormatters.formatCedis(state.wallet.balance),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Escrow Pending", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                Text(
                                    KartFormatters.formatCedis(state.wallet.pendingBalance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            StatusPill(
                                text = "GHS • Protected",
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KartPrimaryButton(
                                text = "Deposit",
                                icon = KartLucideIcons.Plus,
                                containerColor = Color.White.copy(alpha = 0.22f),
                                onClick = onDepositClick,
                                modifier = Modifier.weight(1f),
                                testTag = "wallet_deposit_btn"
                            )
                            KartPrimaryButton(
                                text = "Withdraw",
                                icon = KartLucideIcons.ArrowUp,
                                containerColor = Color.Black.copy(alpha = 0.28f),
                                onClick = onWithdrawClick,
                                modifier = Modifier.weight(1f),
                                testTag = "wallet_withdraw_btn"
                            )
                        }
                    }
                }
            }

            // Referral Banner matching WalletClient.js
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(KartLucideIcons.Gift, contentDescription = null, tint = KartPrimary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Invite Campus Friends", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Share Kart with classmates on your campus.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                        KartSecondaryButton(
                            text = "Copy Link",
                            onClick = {
                                val link = "https://www.kart.cx/signup?ref=$userId"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Kart Referral", link))
                                onShowMessage("Referral link copied to clipboard!")
                            }
                        )
                    }
                }
            }

            item {
                Text("Recent Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (state.loading) {
                items(3) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(68.dp)) }
            } else {
                val txns = state.wallet.transactions.orEmpty().sortedByDescending { it.createdAt }
                if (txns.isEmpty()) {
                    item {
                        EmptyStateView(
                            asset = EmptyStateAsset.WALLET,
                            title = "No transactions yet",
                            subtitle = "Deposits, escrow payouts, and withdrawals will appear here.",
                            modifier = Modifier
                                .fillParentMaxHeight(0.55f)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    items(txns, key = { it.id }) { txn ->
                        val isCredit = txn.type.equals("credit", true) || txn.type.equals("deposit", true) || txn.amount > 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background((if (isCredit) KartSuccess else KartDanger).copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = if (isCredit) KartLucideIcons.ArrowDown else KartLucideIcons.ArrowUp,
                                    contentDescription = null,
                                    tint = if (isCredit) KartSuccess else KartDanger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    txn.description ?: txn.order?.product?.title ?: (txn.type ?: "Wallet Activity"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    "${txn.status ?: "Completed"} • ${KartFormatters.timeAgo(txn.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                            Text(
                                text = (if (isCredit) "+" else "-") + KartFormatters.formatCedis(kotlin.math.abs(txn.amount)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCredit) KartSuccess else KartDanger
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepositFundsScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    val presets = listOf("20", "50", "100", "250", "500")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Deposit Funds", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Text("Top up your Kart Wallet via Mobile Money or Card.", style = MaterialTheme.typography.bodyMedium, color = KartThemeTokens.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    KartChip(label = "₵ $preset", selected = amountText == preset, onClick = { amountText = preset })
                }
            }
            KartTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Amount (₵ GHS)",
                placeholder = "100.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            KartPrimaryButton(
                text = "Continue to Paystack",
                loading = state.actionLoading,
                enabled = (amountText.toDoubleOrNull() ?: 0.0) >= 5.0,
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@KartPrimaryButton
                    viewModel.initiateDeposit(amt) { authUrl ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                        onShowMessage("Complete payment in browser to credit your wallet.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawFundsScreen(
    userId: String,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("MTN Mobile Money") }
    var accountDetails by remember { mutableStateOf("") }
    val methods = listOf("MTN Mobile Money", "Telecel Cash", "AT Money", "Bank Transfer")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Withdraw Funds", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Available to withdraw: ${KartFormatters.formatCedis(state.wallet.balance)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KartPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { m -> KartChip(label = m, selected = method == m, onClick = { method = m }) }
            }
            KartTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Withdrawal Amount (₵ GHS)",
                placeholder = "50.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            KartTextField(
                value = accountDetails,
                onValueChange = { accountDetails = it },
                label = "MoMo Number or Bank Account",
                placeholder = "054 000 0000 - Kwame Mensah"
            )
            KartPrimaryButton(
                text = "Request Withdrawal",
                loading = state.actionLoading,
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && accountDetails.isNotBlank(),
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@KartPrimaryButton
                    viewModel.withdraw(amt, method, accountDetails.trim(), userId) {
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SubscriptionsScreen(
    userId: String,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors

    LaunchedEffect(userId) { viewModel.loadSubscriptions(userId) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Seller Subscriptions", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            state.activeSubscription?.let { sub ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KartSuccessContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(KartLucideIcons.Crown, contentDescription = null, tint = KartSuccess)
                                Spacer(Modifier.width(8.dp))
                                Text("Active Plan: ${sub.plan?.name ?: "Kart Seller"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("Status: ${sub.status ?: "Active"}", style = MaterialTheme.typography.bodySmall)
                            if (sub.status.equals("Active", true)) {
                                KartOutlineButton(
                                    text = "Cancel Subscription",
                                    borderColor = KartDanger,
                                    contentColor = KartDanger,
                                    onClick = { viewModel.cancelSubscription(sub.id, userId) }
                                )
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                items(2) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(150.dp)) }
            } else {
                items(state.plans, key = { it.id }) { plan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    if (plan.price <= 0) "Free" else "${KartFormatters.formatCedis(plan.price)} / ${plan.durationMonths}mo",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = KartPrimary
                                )
                            }
                            if (!plan.description.isNullOrBlank()) {
                                Text(plan.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                            plan.features.orEmpty().forEach { feat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(KartLucideIcons.CheckCircle, contentDescription = null, tint = KartPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(feat, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            KartPrimaryButton(
                                text = if (plan.price <= 0) "Activate Plan" else "Subscribe Now",
                                loading = state.actionLoading,
                                onClick = {
                                    viewModel.subscribe(plan.id) { url ->
                                        if (url.isNotBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        } else {
                                            onShowMessage("Plan activated!")
                                            viewModel.loadSubscriptions(userId)
                                        }
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
