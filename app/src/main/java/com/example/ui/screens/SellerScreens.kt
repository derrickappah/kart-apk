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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.SellerViewModel

@Composable
fun SellerDashboardScreen(
    userId: String,
    viewModel: SellerViewModel,
    onBack: () -> Unit,
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
        } else {
            val hub = state.hub
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // KPI 2x2 Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiCard("Total Earnings", KartFormatters.formatCedis(hub?.totalEarnings ?: 0.0), KartPrimary, Modifier.weight(1f))
                            KpiCard("Active Listings", "${hub?.activeListings ?: 0}", KartSuccess, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiCard("Items Sold", "${hub?.itemsSold ?: 0}", tokens.textPrimary, Modifier.weight(1f))
                            KpiCard("Active Promos", "${hub?.promotions?.count { it.status == "Active" } ?: 0}", KartPrimary, Modifier.weight(1f))
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
    viewModel: SellerViewModel,
    onBack: () -> Unit,
    onRequireAuth: () -> Unit,
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

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors

    var title by remember(editProduct) { mutableStateOf(editProduct?.title.orEmpty()) }
    var description by remember(editProduct) { mutableStateOf(editProduct?.description.orEmpty()) }
    var price by remember(editProduct) { mutableStateOf(editProduct?.price?.takeIf { it > 0 }?.toString().orEmpty()) }
    var stock by remember(editProduct) { mutableStateOf((editProduct?.stockQuantity ?: 1).toString()) }
    var category by remember(editProduct) { mutableStateOf(editProduct?.category ?: KartCategories.first().name) }
    var condition by remember(editProduct) {
        mutableStateOf(conditionOptionFor(editProduct?.condition)?.name ?: "Like New")
    }
    var campus by remember(editProduct) { mutableStateOf(editProduct?.campus ?: KartGhanaCampuses.first()) }
    var existingUrls by remember(editProduct) { mutableStateOf(editProduct?.allImages.orEmpty()) }
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

    LaunchedEffect(state.feedback) {
        state.feedback?.let { onShowMessage(it); viewModel.clearFeedback() }
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
                text = if (editProduct != null) "Listing Updated!" else "Listing Live on Campus!",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(
            title = if (editProduct != null) "Edit Listing" else "Sell an Item",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Multi-Photo Picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Photos (up to 6)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, KartPrimary, RoundedCornerShape(14.dp))
                                    .background(KartPrimary.copy(alpha = 0.08f))
                                    .clickable {
                                        if (totalPhotoCount >= 6) {
                                            onShowMessage("You can add up to 6 photos per listing.")
                                        } else {
                                            showPhotoSourceSheet = true
                                        }
                                    }
                                    .testTag("create_listing_add_photo")
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(KartLucideIcons.ImagePlus, contentDescription = "Add photos", tint = KartPrimary)
                                    Text("Add Photos", style = MaterialTheme.typography.labelSmall, color = KartPrimary)
                                }
                            }
                        }
                        items(existingUrls) { url ->
                            Box(modifier = Modifier.size(96.dp)) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Listing photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                )
                                IconButton(
                                    onClick = { existingUrls = existingUrls - url },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(KartLucideIcons.X, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        items(pickedUris) { uri ->
                            Box(modifier = Modifier.size(96.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                )
                                IconButton(
                                    onClick = { pickedUris = pickedUris - uri },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(KartLucideIcons.X, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                KartTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    placeholder = "e.g. MacBook Air M2 256GB (Barely Used)",
                    testTag = "listing_title_input"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KartTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "Price (₵ GHS)",
                        placeholder = "450",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.4f),
                        testTag = "listing_price_input"
                    )
                    KartTextField(
                        value = stock,
                        onValueChange = { stock = it.filter(Char::isDigit).take(3) },
                        label = "Stock",
                        placeholder = "1",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KartCategories.forEach { cat ->
                            KartChip(label = cat.name, selected = category == cat.name, onClick = { category = cat.name })
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Item Condition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    KartConditionSelector(
                        selectedConditions = setOf(condition),
                        onToggleCondition = { selected -> condition = selected }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Campus Location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KartGhanaCampuses.take(8).forEach { c ->
                            KartChip(label = c, selected = campus == c, onClick = { campus = c })
                        }
                    }
                }
            }

            item {
                KartTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    placeholder = "Mention specs, flaws, meetup spot on campus...",
                    singleLine = false,
                    maxLines = 5,
                    testTag = "listing_description_input"
                )
            }

            item {
                KartPrimaryButton(
                    text = if (editProduct != null) "Save Changes" else "Publish Listing",
                    loading = state.actionLoading,
                    enabled = title.trim().length >= 3 && (price.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        viewModel.publishListing(
                            sellerId = sellerId,
                            editProductId = editProduct?.id,
                            title = title,
                            description = description,
                            price = price.toDoubleOrNull() ?: 0.0,
                            category = category,
                            condition = condition,
                            campus = campus,
                            stock = stock.toIntOrNull() ?: 1,
                            existingUrls = existingUrls,
                            newImageUris = pickedUris,
                            resolver = context.contentResolver,
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
        } else {
            showInAppCamera = false
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
                                    Text("${item.viewsCount ?: 0} views • ${item.status ?: "Active"}", style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KartOutlineButton(
                                    text = "Promote",
                                    icon = KartLucideIcons.Megaphone,
                                    onClick = { onPromoteListing(item.id) },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onEditListing(item.id) }) {
                                    Icon(KartLucideIcons.Pencil, contentDescription = "Edit listing", tint = KartPrimary)
                                }
                                IconButton(onClick = { viewModel.deleteListing(item.id, sellerId) {} }) {
                                    Icon(KartLucideIcons.Trash2, contentDescription = "Delete listing", tint = KartDanger)
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
fun PromoteListingScreen(
    productId: String,
    viewModel: SellerViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adType by remember { mutableStateOf("Featured") }
    var days by remember { mutableIntStateOf(7) }
    val tiers = listOf(
        Triple("Boost", "Higher ranking in Marketplace search", 15.0),
        Triple("Featured", "Featured Slider + Home Spotlight", 35.0),
        Triple("Campus Ad", "Top Hero Banner across your campus", 65.0)
    )

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
                Text("Choose Campaign Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(tiers) { (name, desc, baseCost) ->
                val selected = adType == name
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) KartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (selected) 2.dp else 1.dp, if (selected) KartPrimary else KartThemeTokens.colors.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { adType = name }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                        }
                        Text(KartFormatters.formatCedis(baseCost * (days / 7.0)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = KartPrimary)
                    }
                }
            }
            item {
                Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 14, 30).forEach { d ->
                        KartChip(label = "$d days", selected = days == d, onClick = { days = d })
                    }
                }
            }
            item {
                KartPrimaryButton(
                    text = "Launch Promotion",
                    loading = state.actionLoading,
                    onClick = {
                        viewModel.promoteListing(productId, adType, days) { url ->
                            if (url.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        if (promos.isEmpty()) {
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
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(promos, key = { it.id }) { ad ->
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
                                StatusPill(ad.status ?: "Active", KartSuccessContainer, KartSuccess)
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
