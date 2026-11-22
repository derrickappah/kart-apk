package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.ShimmerBox
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import kotlinx.coroutines.launch

@Composable
fun CheckoutScreen(
    productId: String,
    repository: KartRepository,
    currentUserId: String?,
    onBack: () -> Unit,
    onOrderCreated: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = KartThemeTokens.colors

    var product by remember { mutableStateOf<Product?>(null) }
    var walletBalance by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var quantity by remember { mutableIntStateOf(1) }
    var paymentMethod by remember { mutableStateOf("paystack") }

    LaunchedEffect(productId, currentUserId) {
        loading = true
        val detailRes = repository.fetchProductDetail(productId, currentUserId)
        if (detailRes is ApiResult.Success) product = detailRes.data.product
        if (!currentUserId.isNullOrBlank()) {
            val walletRes = repository.fetchWallet(currentUserId)
            if (walletRes is ApiResult.Success) walletBalance = walletRes.data.balance
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Secure Checkout", onBack = onBack)

        if (loading) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(92.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(120.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp))
            }
        } else if (product == null) {
            EmptyStateView(
                asset = EmptyStateAsset.ERROR,
                title = "Product unavailable",
                subtitle = "We couldn't load this listing for checkout.",
                actionLabel = "Go Back",
                onActionClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            val item = product!!
            val maxStock = (item.stockQuantity ?: 10).coerceAtLeast(1)
            val subtotal = item.price * quantity
            val canPayWallet = walletBalance >= subtotal

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            AsyncImage(
                                model = item.primaryImage,
                                contentDescription = item.title,
                                placeholder = painterResource(R.drawable.img_placeholder),
                                error = painterResource(R.drawable.img_placeholder),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    KartFormatters.toSentenceCase(item.title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Text(
                                    KartFormatters.formatCedis(item.price),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KartPrimary
                                )
                                Text(
                                    item.campus ?: "On Campus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }
                }

                // Quantity Selector
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Quantity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (quantity > 1) quantity-- }) {
                                Icon(KartLucideIcons.Minus, contentDescription = "Decrease quantity")
                            }
                            Text(
                                text = "$quantity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            IconButton(onClick = { if (quantity < maxStock) quantity++ }) {
                                Icon(KartLucideIcons.Plus, contentDescription = "Increase quantity")
                            }
                        }
                    }
                }

                // Payment Methods
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Payment Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        PaymentMethodOption(
                            title = "Paystack (Mobile Money & Card)",
                            subtitle = "MTN MoMo, Telecel Cash, AT Money, Visa/Mastercard",
                            icon = KartLucideIcons.CreditCard,
                            selected = paymentMethod == "paystack",
                            onClick = { paymentMethod = "paystack" }
                        )

                        PaymentMethodOption(
                            title = "Kart Wallet",
                            subtitle = "Available balance: ${KartFormatters.formatCedis(walletBalance)}" +
                                if (!canPayWallet) " (Insufficient funds)" else "",
                            icon = KartLucideIcons.Wallet,
                            selected = paymentMethod == "wallet",
                            onClick = { paymentMethod = "wallet" }
                        )
                    }
                }

                // Summary
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SummaryRow("Item Subtotal", KartFormatters.formatCedis(subtotal))
                            SummaryRow("Escrow Protection", "Included")
                            HorizontalDivider(color = tokens.subtleDivider, modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Total to Pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    KartFormatters.formatCedis(subtotal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = KartPrimary
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    KartPrimaryButton(
                        text = if (paymentMethod == "wallet") "Pay with Kart Wallet" else "Proceed to Paystack",
                        loading = submitting,
                        enabled = paymentMethod == "paystack" || canPayWallet,
                        onClick = {
                            scope.launch {
                                submitting = true
                                if (paymentMethod == "wallet") {
                                    when (val res = repository.payOrderWithWallet(item.id, quantity)) {
                                        is ApiResult.Success -> {
                                            submitting = false
                                            onShowMessage("Payment confirmed! Order secured in Escrow.")
                                            onOrderCreated(res.data)
                                        }
                                        is ApiResult.Error -> {
                                            submitting = false
                                            onShowMessage(res.message)
                                        }
                                    }
                                } else {
                                    when (val res = repository.createOrderPaystack(item.id, quantity)) {
                                        is ApiResult.Success -> {
                                            submitting = false
                                            val (_, authUrl) = res.data
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                            onShowMessage("Complete payment in browser to return automatically.")
                                        }
                                        is ApiResult.Error -> {
                                            submitting = false
                                            onShowMessage(res.message)
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "checkout_confirm_btn"
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) KartPrimary else tokens.subtleDivider,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) KartPrimary else tokens.textSecondary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = KartPrimary)
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KartThemeTokens.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
