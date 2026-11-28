package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartCategories
import com.example.domain.model.KartFormatters
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.Product
import com.example.domain.model.conditionOptionFor
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartConditionSelector
import com.example.ui.components.KartInAppCameraDialog
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPhotoSourceSheet
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
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
import com.example.ui.viewmodel.SellerViewModel

@Composable
fun SellerDashboardScreen(
    userId: String,
    viewModel: SellerViewModel,
    isVerified: Boolean = false,
    verificationStatus: String? = null,
    onBack: () -> Unit,
    onVerifyIdentity: () -> Unit = {},
    onCreateListing: () -> Unit,
    onMyListings: () -> Unit,
    onSellerOrders: () -> Unit,
    onPromotions: () -> Unit,
    onAnalytics: () -> Unit,
    onSubscriptions: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors

    LaunchedEffect(userId) { viewModel.loadSellerHub(userId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Seller Studio", onBack = onBack)

        if (state.loading && state.hub == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(110.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(180.dp))
            }
        } else if (state.error != null && state.hub == null) {
            EmptyStateView(
                asset = EmptyStateAsset.ERROR,
                title = "Could not load Seller Studio",
                subtitle = state.error ?: "Check your connection and try again.",
                actionLabel = "Retry",
                onActionClick = { viewModel.loadSellerHub(userId) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            val hub = state.hub
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (!isVerified) {
                    val isPending = verificationStatus.equals("Pending", ignoreCase = true)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = KartWarningContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onVerifyIdentity)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.ShieldCheck,
                                    contentDescription = null,
                                    tint = KartWarning,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPending) "Verification Under Review" else "Verification Required to Sell",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = if (isPending) {
                                            "You'll be able to post items once your identity is approved."
                                        } else {
                                            "Verify your identity before posting items on Kart. Tap to verify."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // KPI 2x2 Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiCard("Total Earnings", KartFormatters.formatCedis(hub?.totalEarnings ?: 0.0), KartPrimary, Modifier.weight(1f))
                            KpiCard("Active Listings", "${hub?.activeListings ?: 0}", KartSuccess, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiCard("Items Sold", "${hub?.itemsSold ?: 0}", tokens.textPrimary, Modifier.weight(1f))
                            KpiCard("Active Promos", "${hub?.promotions?.count { it.status.equals("Active", true) } ?: 0}", KartPrimary, Modifier.weight(1f))
                        }
                    }
                }

                // 7-Day Earnings Area Chart (Native Compose port of SellerAreaChart.js)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                            Text("7-Day Earnings Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SellerEarningsAreaChart(
                                values = hub?.chartValues ?: List(7) { 0.0 },
                                labels = hub?.chartLabels ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            )
                        }
                    }
                }

                // Quick Actions Grid
                item {
                    Text("Manage Your Shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SellerActionTile("Add New Listing", "Publish an item to campus in seconds", KartLucideIcons.Plus, onCreateListing)
                        SellerActionTile("My Listings (${hub?.listings?.size ?: 0})", "Edit prices, manage stock, or promote items", KartLucideIcons.Package, onMyListings)
                        SellerActionTile("Seller Orders (${hub?.orders?.size ?: 0})", "Ship paid orders and track escrow releases", KartLucideIcons.Truck, onSellerOrders)
                        SellerActionTile("Promotions & Boosts", "Feature listings on the Kart home banner", KartLucideIcons.Megaphone, onPromotions)
                        SellerActionTile("Shop Analytics", "Views, shares, and conversion metrics", KartLucideIcons.BarChart2, onAnalytics)
                        SellerActionTile("Seller Subscription", if (hub?.isSubscriptionFree == true) "Free campus selling active" else "Manage seller membership", KartLucideIcons.Crown, onSubscriptions)
                    }
                }
            }
        }
    }
}

@Composable
fun SellerEarningsAreaChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val tokens = KartThemeTokens.colors
    val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp)
        ) {
            if (values.isEmpty()) return@Canvas
            val count = values.size.coerceAtLeast(2)
            val stepX = size.width / (count - 1).coerceAtLeast(1)
            val points = values.mapIndexed { i, v ->
                val x = i * stepX
                val y = size.height - ((v / maxVal).toFloat() * (size.height * 0.82f)) - 10f
                Offset(x, y)
            }
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val ctrlX = (prev.x + curr.x) / 2f
                    cubicTo(ctrlX, prev.y, ctrlX, curr.y, curr.x, curr.y)
                }
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(KartPrimary.copy(alpha = 0.32f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = KartPrimary,
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
            points.forEach { pt ->
                drawCircle(color = KartPrimary, radius = 8f, center = pt)
                drawCircle(color = Color.White, radius = 4f, center = pt)
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            labels.forEach { day ->
                Text(day, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateEditListingScreen(
    sellerId: String?,
    editProduct: Product? = null,
    editProductId: String? = null,
    campuses: List<String> = KartGhanaCampuses,
    viewModel: SellerViewModel,
    isVerified: Boolean = false,
    verificationStatus: String? = null,
    onBack: () -> Unit,
    onRequireAuth: () -> Unit,
    onVerifyIdentity: () -> Unit = {},
    onViewCreatedListing: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    if (sellerId.isNullOrBlank()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            EmptyStateView(
                asset = EmptyStateAsset.LISTINGS,
                title = "Sign in to sell on Kart",
                subtitle = "Turn textbooks, gadgets, and dorm items into cash across your campus.",
                actionLabel = "Sign In",
                onActionClick = onRequireAuth
            )
        }
        return
    }

    // Only gate new listing creation on verification (DB RLS gates INSERT; sellers can still edit existing listings)
    val isCreatingNew = editProductId.isNullOrBlank() && editProduct == null
    if (!isVerified && isCreatingNew) {
        val isPending = verificationStatus.equals("Pending", ignoreCase = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            KartSubScreenTopBar(
                title = if (editProductId.isNullOrBlank()) "Sell an Item" else "Edit listing",
                onBack = onBack
            )
            EmptyStateView(
                asset = if (isPending) EmptyStateAsset.VERIFICATION_PENDING else EmptyStateAsset.VERIFY_IDENTITY,
                title = if (isPending) "Verification Under Review" else "Verify Your Identity to Sell",
                subtitle = if (isPending) {
                    "Your identity verification is currently being reviewed. You'll be able to sell and post items once your account is verified."
                } else {
                    "To keep Kart campuses safe, you need to verify your identity before selling or posting items."
                },
                actionLabel = if (isPending) "View Verification Status" else "Verify Identity",
                onActionClick = onVerifyIdentity,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors
    val availableCampuses = campuses.ifEmpty { KartGhanaCampuses }

    var loadedEditProduct by remember(editProduct, editProductId) { mutableStateOf(editProduct) }
    var loadingEditProduct by remember(editProductId) {
        mutableStateOf(editProduct == null && !editProductId.isNullOrBlank())
    }

    LaunchedEffect(editProductId) {
        if (loadedEditProduct == null && !editProductId.isNullOrBlank()) {
            loadingEditProduct = true
            viewModel.loadProductForEdit(editProductId) { found ->
                loadedEditProduct = found
                loadingEditProduct = false
            }
        }
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    if (loadingEditProduct) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            KartSubScreenTopBar(title = "Edit listing", onBack = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(120.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(56.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
        return
    }

    if (!editProductId.isNullOrBlank() && loadedEditProduct == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            KartSubScreenTopBar(title = "Edit listing", onBack = onBack)
            EmptyStateView(
                asset = EmptyStateAsset.ERROR,
                title = "Listing not found",
                subtitle = "We couldn't load this listing for editing. Please go back and try again.",
                actionLabel = "Go Back",
                onActionClick = onBack,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        return
    }

    val targetEdit = loadedEditProduct
    var title by remember(targetEdit) { mutableStateOf(targetEdit?.title.orEmpty()) }
    var description by remember(targetEdit) { mutableStateOf(targetEdit?.description.orEmpty()) }
    var price by remember(targetEdit) { mutableStateOf(targetEdit?.price?.takeIf { it > 0 }?.toString().orEmpty()) }
    var stockText by remember(targetEdit) { mutableStateOf((targetEdit?.stockQuantity ?: 1).coerceAtLeast(0).toString()) }
    var category by remember(targetEdit) { mutableStateOf(targetEdit?.category ?: KartCategories.first().name) }
    var condition by remember(targetEdit) {
        mutableStateOf(conditionOptionFor(targetEdit?.condition)?.name ?: "Like New")
    }
    var campus by remember(targetEdit) {
        mutableStateOf(targetEdit?.campus.orEmpty())
    }
    var showCampusSuggestions by remember { mutableStateOf(false) }
    val sellListState = rememberLazyListState()
    val matchingCampuses = remember(campus, availableCampuses) {
        val q = campus.trim()
        if (q.isEmpty()) availableCampuses.take(5)
        else availableCampuses.filter { c ->
            c.contains(q, ignoreCase = true) ||
                KartFormatters.abbreviateCampus(c).contains(q, ignoreCase = true)
        }.take(6)
    }
    LaunchedEffect(showCampusSuggestions, campus) {
        if (showCampusSuggestions) {
            val lastIdx = (sellListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            sellListState.animateScrollToItem(lastIdx)
        }
    }
    var existingUrls by remember(targetEdit) { mutableStateOf(targetEdit?.allImages.orEmpty()) }
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var createdId by remember { mutableStateOf<String?>(null) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var showInAppCamera by remember { mutableStateOf(false) }

    val totalPhotoCount = existingUrls.size + pickedUris.size

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val maxNew = (6 - existingUrls.size).coerceAtLeast(0)
            pickedUris = (pickedUris + uris).distinct().take(maxNew)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showInAppCamera = true
        } else {
            onShowMessage("Camera permission is needed to take photos. You can still upload from your gallery.")
        }
    }

    if (createdId != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Icon(KartLucideIcons.CheckCircle, contentDescription = null, tint = KartSuccess, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (targetEdit != null) "Listing Updated!" else "Listing Live on Campus!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Students across $campus can now discover and buy your item.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )
            Spacer(Modifier.height(24.dp))
            KartPrimaryButton(
                text = "View Listing",
                onClick = { onViewCreatedListing(createdId!!) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            KartOutlineButton(
                text = "Back to Studio",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            KartSubScreenTopBar(
                title = if (targetEdit != null) "Edit listing" else "Sell an item",
                onBack = onBack
            )

        val parsedPrice = price.toDoubleOrNull() ?: 0.0
        val parsedStock = stockText.toIntOrNull() ?: -1
        val minStock = if (targetEdit != null) 0 else 1
        val canPublish = title.trim().length >= 3 &&
            description.trim().length >= 10 &&
            campus.trim().isNotBlank() &&
            parsedPrice in 0.01..1_000_000.0 &&
            parsedStock >= minStock &&
            totalPhotoCount >= 1

        LazyColumn(
            state = sellListState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Photos Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Photos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "First photo is your cover",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = tokens.textSecondary
                            )
                        }
                        Text(
                            text = "$totalPhotoCount / 6",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textSecondary
                        )
                    }

                    if (totalPhotoCount == 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                                .clickable { showPhotoSourceSheet = true }
                                .testTag("create_listing_add_photo")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.Camera,
                                    contentDescription = "Add photos",
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Add photos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "Camera or photo library",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(existingUrls.size) { index ->
                                val url = existingUrls[index]
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Listing photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (index == 0) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.56f))
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text("Cover", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable { existingUrls = existingUrls - url }
                                    ) {
                                        Icon(KartLucideIcons.X, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            items(pickedUris.size) { index ->
                                val uri = pickedUris[index]
                                val isCover = existingUrls.isEmpty() && index == 0
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isCover) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.56f))
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text("Cover", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable { pickedUris = pickedUris - uri }
                                    ) {
                                        Icon(KartLucideIcons.X, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            if (totalPhotoCount < 6) {
                                item {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                                            .clickable { showPhotoSourceSheet = true }
                                            .testTag("create_listing_add_photo")
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(KartLucideIcons.Plus, contentDescription = "Add photos", tint = KartPrimary, modifier = Modifier.size(20.dp))
                                            Text("Add", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Basics: Title, Price, Stock, Description
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    KartTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = "Title",
                        placeholder = "MacBook Air M2 256GB",
                        testTag = "listing_title_input"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        KartTextField(
                            value = price,
                            onValueChange = { price = KartFormatters.sanitizeDecimalInput(it) },
                            label = "Price (₵ GHS)",
                            placeholder = "450",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f),
                            testTag = "listing_price_input"
                        )
                        KartTextField(
                            value = stockText,
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(3)
                                stockText = digits
                            },
                            label = "Quantity (max 999)",
                            placeholder = "1",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.9f),
                            testTag = "listing_stock_input"
                        )
                    }
                    KartTextField(
                        value = description,
                        onValueChange = { description = it.take(2000) },
                        label = "Description (min 10 chars)",
                        placeholder = "Condition, specs, or meetup spot",
                        singleLine = false,
                        maxLines = 5,
                        testTag = "listing_description_input"
                    )
                }
            }

            // Category Section (compact horizontal row instead of tall pill wall)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(KartCategories) { cat ->
                            KartChip(label = cat.name, selected = category == cat.name, onClick = { category = cat.name })
                        }
                    }
                }
            }

            // Condition Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Condition",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    KartConditionSelector(
                        selectedConditions = setOf(condition),
                        onToggleCondition = { selected -> condition = selected }
                    )
                }
            }

            // Campus Section
            item {
                val trimmedQuery = campus.trim()
                val normalizedCustomCampus = trimmedQuery.uppercase(java.util.Locale.US)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
        }

        // Sticky Bottom Publish Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = tokens.cardBorder)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (!canPublish) {
                    Text(
                        text = "Add at least 1 photo, a title (3+ chars), description (10+ chars), valid price, and quantity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
                KartPrimaryButton(
                    text = if (targetEdit != null) "Save changes" else "Publish listing",
                    loading = state.actionLoading,
                    enabled = canPublish,
                    onClick = {
                        val trimmedCampus = campus.trim()
                        val resolvedCampus = availableCampuses.firstOrNull {
                            it.equals(trimmedCampus, ignoreCase = true)
                        } ?: trimmedCampus.uppercase(java.util.Locale.US)
                        viewModel.publishListing(
                            sellerId = sellerId,
                            editProductId = targetEdit?.id,
                            title = title,
                            description = description,
                            price = parsedPrice,
                            category = category,
                            condition = condition,
                            campus = resolvedCampus,
                            stock = parsedStock.coerceAtLeast(minStock),
                            existingUrls = existingUrls,
                            newImageUris = pickedUris,
                            resolver = context.contentResolver,
                            originalPrice = targetEdit?.price,
                            onSuccess = { id -> createdId = id }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "publish_listing_btn"
                )
            }
        }
    }

    if (showPhotoSourceSheet) {
        val remainingSlots = (6 - totalPhotoCount).coerceAtLeast(0)
        KartPhotoSourceSheet(
            remainingSlots = remainingSlots,
            onDismiss = { showPhotoSourceSheet = false },
            onTakePhotoClick = {
                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPermission) {
                    showInAppCamera = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onUploadPhotoClick = {
                multiPhotoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    LaunchedEffect(showInAppCamera, totalPhotoCount) {
        if (showInAppCamera && (6 - totalPhotoCount) <= 0) {
            showInAppCamera = false
        }
    }

    if (showInAppCamera) {
        val remainingSlots = (6 - totalPhotoCount).coerceAtLeast(0)
        if (remainingSlots > 0) {
            KartInAppCameraDialog(
                maxPhotos = remainingSlots,
                existingPhotoCount = totalPhotoCount,
                onDismiss = { showInAppCamera = false },
                onPhotosCaptured = { captured ->
                    val maxNew = (6 - existingUrls.size).coerceAtLeast(0)
                    pickedUris = (pickedUris + captured).distinct().take(maxNew)
                },
                onFallbackToGallery = {
                    multiPhotoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }
    }
}

@Composable
fun MyListingsScreen(
    sellerId: String,
    viewModel: SellerViewModel,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onEditListing: (String) -> Unit,
    onPromoteListing: (String) -> Unit,
    onViewProduct: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteProduct by remember { mutableStateOf<Product?>(null) }
    LaunchedEffect(sellerId) { viewModel.loadSellerHub(sellerId) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(
            title = "My Listings",
            onBack = onBack,
            actions = {
                IconButton(onClick = onCreateNew) {
                    Icon(KartLucideIcons.Plus, contentDescription = "Create listing", tint = KartPrimary)
                }
            }
        )

        val listings = state.hub?.listings.orEmpty()
        if (state.loading && listings.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                repeat(4) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(92.dp)) }
            }
        } else if (listings.isEmpty()) {
            EmptyStateView(
                asset = EmptyStateAsset.ACTIVE_LISTINGS,
                title = "No listings yet",
                subtitle = "Post your first item and reach students on your campus.",
                actionLabel = "Create Listing",
                onActionClick = onCreateNew,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listings, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewProduct(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = item.primaryImage,
                                    contentDescription = item.title,
                                    placeholder = painterResource(R.drawable.img_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(KartFormatters.formatCedis(item.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = KartPrimary)
                                    Text(
                                        "${item.viewsCount ?: 0} views • Stock ${item.stockQuantity ?: 1} • ${item.status ?: "Active"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                }
                            }
                            val canPromoteItem = item.isActiveListing && !item.isOutOfStock
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KartOutlineButton(
                                    text = if (canPromoteItem) "Promote" else "Unavailable",
                                    icon = KartLucideIcons.Megaphone,
                                    enabled = canPromoteItem && !state.actionLoading,
                                    onClick = { onPromoteListing(item.id) },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = !state.actionLoading,
                                    onClick = { onEditListing(item.id) }
                                ) {
                                    Icon(KartLucideIcons.Pencil, contentDescription = "Edit listing", tint = KartPrimary)
                                }
                                IconButton(
                                    enabled = !state.actionLoading,
                                    onClick = { pendingDeleteProduct = item }
                                ) {
                                    Icon(KartLucideIcons.Trash2, contentDescription = "Delete listing", tint = KartDanger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { if (!state.actionLoading) pendingDeleteProduct = null },
            title = { Text("Delete Listing?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete \"${prod.title}\"? Listings with active escrow orders or running promotions cannot be deleted.")
            },
            confirmButton = {
                KartPrimaryButton(
                    text = "Delete",
                    loading = state.actionLoading,
                    enabled = !state.actionLoading,
                    containerColor = KartDanger,
                    onClick = {
                        viewModel.deleteListing(prod.id, sellerId) {
                            pendingDeleteProduct = null
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !state.actionLoading,
                    onClick = { pendingDeleteProduct = null }
                ) { Text("Cancel") }
            }
        )
    }
}

private data class PromoTierOption(
    val tierId: String,
    val title: String,
    val subtitle: String,
    val price: Double
)

@Composable
fun PromoteListingScreen(
    productId: String,
    viewModel: SellerViewModel,
    dailyPrice: Double = 5.0,
    weeklyPrice: Double = 25.0,
    featuredPrice: Double = 50.0,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTierId by remember { mutableStateOf("weekly") }
    val tiers = remember(dailyPrice, weeklyPrice, featuredPrice) {
        listOf(
            PromoTierOption("daily", "Daily Boost (24h)", "Higher ranking in Marketplace for 24 hours", dailyPrice.coerceAtLeast(0.0)),
            PromoTierOption("weekly", "Weekly Boost (7d)", "7-day Marketplace ranking boost", weeklyPrice.coerceAtLeast(0.0)),
            PromoTierOption("featured", "Featured Listing (30d)", "Featured Slider + Home Spotlight placement", featuredPrice.coerceAtLeast(0.0))
        )
    }
    val chosenTier = tiers.firstOrNull { it.tierId == selectedTierId } ?: tiers[1]

    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Promote Listing", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Choose Campaign Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(tiers) { tier ->
                val selected = selectedTierId == tier.tierId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) KartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (selected) 2.dp else 1.dp, if (selected) KartPrimary else KartThemeTokens.colors.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { selectedTierId = tier.tierId }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tier.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(tier.subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                        }
                        Text(
                            text = if (tier.price <= 0.0) "Free" else KartFormatters.formatCedis(tier.price),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = KartPrimary
                        )
                    }
                }
            }
            item {
                Text(
                    text = if (chosenTier.price <= 0.0) {
                        "This promotional tier is currently free and activates immediately."
                    } else {
                        "Total: ${KartFormatters.formatCedis(chosenTier.price)} — confirmed securely at Paystack checkout."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = KartThemeTokens.colors.textSecondary
                )
            }
            item {
                KartPrimaryButton(
                    text = if (chosenTier.price <= 0.0) "Activate Free Promotion" else "Launch Promotion",
                    loading = state.actionLoading,
                    onClick = {
                        viewModel.promoteListing(productId, chosenTier.tierId, chosenTier.price) { url ->
                            if (url.isNotBlank()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    onShowMessage("Could not open payment page in browser.")
                                }
                            } else {
                                onShowMessage("Promotion campaign launched!")
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PromotionsListScreen(
    sellerId: String,
    viewModel: SellerViewModel,
    onBack: () -> Unit,
    onMyListings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sellerId) { viewModel.loadSellerHub(sellerId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Promotions", onBack = onBack)
        val promos = state.hub?.promotions.orEmpty()
        when {
            state.loading && state.hub == null -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    repeat(3) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(84.dp)) }
                }
            }
            state.error != null && state.hub == null -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ERROR,
                    title = "Could not load promotions",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadSellerHub(sellerId) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            promos.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.PROMOTIONS,
                    title = "No active promotions",
                    subtitle = "Boost your listings to reach more buyers on the Kart homepage.",
                    actionLabel = "Choose a Listing to Boost",
                    onActionClick = onMyListings,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(promos, key = { it.id }) { ad ->
                        val (pillBg, pillFg) = when (ad.status?.lowercase()) {
                            "active" -> KartSuccessContainer to KartSuccess
                            "pending" -> KartWarningContainer to KartWarning
                            else -> KartDangerContainer to KartDanger
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(ad.product?.title ?: ad.adType ?: "Campaign", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    StatusPill(ad.status ?: "Pending", pillBg, pillFg)
                                }
                                Text(
                                    "${ad.adType ?: "Boost"} • ${ad.views ?: 0} impressions • ${ad.clicks ?: 0} clicks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KartThemeTokens.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SellerAnalyticsScreen(
    sellerId: String,
    viewModel: SellerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sellerId) { viewModel.loadSellerHub(sellerId) }
    val hub = state.hub

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Seller Analytics", onBack = onBack)
        when {
            state.loading && hub == null -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(92.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(190.dp))
                }
            }
            state.error != null && hub == null -> {
                EmptyStateView(
                    asset = EmptyStateAsset.ERROR,
                    title = "Could not load analytics",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadSellerHub(sellerId) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        val totalViews = hub?.listings?.sumOf { it.viewsCount ?: 0 } ?: 0
                        val totalShares = hub?.listings?.sumOf { it.sharesCount ?: 0 } ?: 0
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard("Total Views", "$totalViews", KartPrimary, Modifier.weight(1f))
                            KpiCard("Total Shares", "$totalShares", KartSuccess, Modifier.weight(1f))
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                                Text("Revenue (Last 7 Days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                SellerEarningsAreaChart(
                                    values = hub?.chartValues ?: List(7) { 0.0 },
                                    labels = hub?.chartLabels ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = valueColor)
        }
    }
}

@Composable
private fun SellerActionTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KartPrimary.copy(alpha = 0.14f))
        ) {
            Icon(icon, contentDescription = null, tint = KartPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
        }
    }
}
