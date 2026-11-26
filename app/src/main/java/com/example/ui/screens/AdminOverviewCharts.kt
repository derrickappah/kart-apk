package com.example.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.AdminDashboardBundle
import com.example.domain.model.KartFormatters
import com.example.domain.model.Product
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val AdminChartEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

data class DailyListingsBucket(
    val label: String,
    val listingCount: Int,
    val totalValue: Double
)

fun computeLast7DaysListings(products: List<Product>, nowMillis: Long = System.currentTimeMillis()): List<DailyListingsBucket> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val dayStarts = (6 downTo 0).map { daysAgo ->
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        c.timeInMillis to dayFmt.format(c.time)
    }
    val dayMs = 86_400_000L
    return dayStarts.map { (startMs, label) ->
        val endMs = startMs + dayMs
        val dayProducts = products.filter { p ->
            val ts = KartFormatters.parseIsoTimestamp(p.createdAt)
            ts in startMs until endMs
        }
        DailyListingsBucket(
            label = label,
            listingCount = dayProducts.size,
            totalValue = dayProducts.sumOf { it.price }
        )
    }
}

@Composable
fun AdminOverviewDashboard(
    bundle: AdminDashboardBundle?,
    stats: AdminOverviewStats,
    isLiveConnected: Boolean,
    onNavigateTab: (tab: String, filter: String) -> Unit
) {
    val dailyBuckets = remember(bundle?.products) {
        computeLast7DaysListings(bundle?.products.orEmpty())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Live status header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Live Telemetry",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = KartThemeTokens.colors.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isLiveConnected) KartSuccessContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isLiveConnected) KartSuccess else KartThemeTokens.colors.textSecondary)
                )
                Text(
                    text = if (isLiveConnected) "LIVE" else "SYNCING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLiveConnected) KartSuccess else KartThemeTokens.colors.textSecondary
                )
            }
        }

        // Compact 2x2 KPI Strip
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AdminKpiTile(
                label = "Order GMV",
                value = KartFormatters.formatCedis(stats.totalGmv),
                accent = KartPrimary,
                onClick = { onNavigateTab("Orders", "All") },
                modifier = Modifier.weight(1f)
            )
            AdminKpiTile(
                label = "Platform Fees",
                value = KartFormatters.formatCedis(stats.totalPlatformFees),
                accent = KartSuccess,
                onClick = { onNavigateTab("Orders", "All") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AdminKpiTile(
                label = "Total Users",
                value = "${stats.totalUsers}",
                accent = KartPrimary,
                onClick = { onNavigateTab("Users", "All") },
                modifier = Modifier.weight(1f)
            )
            AdminKpiTile(
                label = "Active Listings",
                value = "${stats.activeListings}",
                accent = KartSuccess,
                onClick = { onNavigateTab("Listings", "Active") },
                modifier = Modifier.weight(1f)
            )
        }

        // 7-Day Listings Volume Area Chart
        AdminListingsVolumeAreaChartCard(
            buckets = dailyBuckets,
            onOpenListings = { onNavigateTab("Listings", "All") }
        )

        // Pending Queues Horizontal Bar Chart
        AdminPendingQueuesBarChartCard(
            stats = stats,
            onNavigateTab = onNavigateTab
        )

        // Side-by-side Users & Listings Donut Charts
        val standardUsers = (stats.totalUsers - stats.verifiedUsers - stats.bannedUsers).coerceAtLeast(0)
        val soldListings = ((bundle?.products?.size ?: 0) - stats.activeListings - stats.suspendedListings).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            AdminDonutChartCard(
                title = "Users",
                total = stats.totalUsers,
                slices = listOf(
                    DonutSlice("Verified", stats.verifiedUsers, KartSuccess),
                    DonutSlice("Standard", standardUsers, KartPrimary),
                    DonutSlice("Banned", stats.bannedUsers, KartDanger)
                ),
                onClick = { onNavigateTab("Users", "All") },
                modifier = Modifier.weight(1f)
            )
            AdminDonutChartCard(
                title = "Listings",
                total = bundle?.products?.size ?: 0,
                slices = listOf(
                    DonutSlice("Active", stats.activeListings, KartSuccess),
                    DonutSlice("Suspended", stats.suspendedListings, KartDanger),
                    DonutSlice("Sold/Other", soldListings, KartWarning)
                ),
                onClick = { onNavigateTab("Listings", "All") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminKpiTile(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = KartThemeTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminListingsVolumeAreaChartCard(
    buckets: List<DailyListingsBucket>,
    onOpenListings: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = AdminChartEaseOut),
        label = "listings_reveal"
    )
    var selectedIdx by remember { mutableIntStateOf((buckets.size - 1).coerceAtLeast(0)) }
    val activeBucket = buckets.getOrNull(selectedIdx)
    val maxVal = (buckets.maxOfOrNull { it.listingCount } ?: 0).coerceAtLeast(1).toFloat()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("7-Day Listings Volume", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (activeBucket != null) {
                        Text(
                            text = "${activeBucket.label}: ${activeBucket.listingCount} listings (${KartFormatters.formatCedis(activeBucket.totalValue)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = KartPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Listings →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = KartPrimary,
                    modifier = Modifier.clickable(onClick = onOpenListings)
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            if (buckets.size > 1 && size.width > 0) {
                                val step = size.width.toFloat() / (buckets.size - 1)
                                selectedIdx = (offset.x / step).toInt().coerceIn(0, buckets.lastIndex)
                            }
                        }
                    }
                    .pointerInput(buckets) {
                        detectHorizontalDragGestures { change, _ ->
                            if (buckets.size > 1 && size.width > 0) {
                                val step = size.width.toFloat() / (buckets.size - 1)
                                selectedIdx = (change.position.x / step).toInt().coerceIn(0, buckets.lastIndex)
                            }
                        }
                    }
            ) {
                if (buckets.isEmpty()) return@Canvas
                val padTop = 12.dp.toPx()
                val padBottom = 10.dp.toPx()
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val stepX = if (buckets.size > 1) size.width / (buckets.size - 1) else size.width

                val pts = buckets.mapIndexed { i, b ->
                    val x = i * stepX
                    val norm = ((b.listingCount.toFloat() / maxVal) * progress).coerceIn(0f, 1f)
                    val y = padTop + chartH * (1f - norm)
                    Offset(x, y)
                }

                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val curr = pts[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(KartPrimary.copy(alpha = 0.28f), KartPrimary.copy(alpha = 0.02f))
                    )
                )
                drawPath(
                    path = linePath,
                    color = KartPrimary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                pts.getOrNull(selectedIdx)?.let { sel ->
                    drawLine(
                        color = KartPrimary.copy(alpha = 0.35f),
                        start = Offset(sel.x, padTop),
                        end = Offset(sel.x, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = sel)
                    drawCircle(color = KartPrimary, radius = 4.dp.toPx(), center = sel)
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                buckets.forEachIndexed { idx, b ->
                    Text(
                        text = b.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (idx == selectedIdx) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx == selectedIdx) KartPrimary else KartThemeTokens.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPendingQueuesBarChartCard(
    stats: AdminOverviewStats,
    onNavigateTab: (tab: String, filter: String) -> Unit
) {
    val rows = listOf(
        Triple("KYC", stats.pendingKyc, KartWarning),
        Triple("Withdrawals", stats.pendingWithdrawals, KartPrimary),
        Triple("Refunds", stats.pendingRefunds, KartDanger),
        Triple("Product Reports", stats.openProductReports, KartDanger),
        Triple("User Reports", stats.openUserReports, KartWarning)
    )
    val maxCount = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Text("Pending Action Queues", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            rows.forEach { (tab, count, color) ->
                val targetFraction = if (count == 0) 0.04f else (count.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
                val animFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 300, easing = AdminChartEaseOut),
                    label = "bar_$tab"
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (tab) {
                                "Product Reports" -> onNavigateTab("Reports", "Product")
                                "User Reports" -> onNavigateTab("Reports", "User")
                                else -> onNavigateTab(tab, "Pending")
                            }
                        }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tab, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) color else KartThemeTokens.colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animFraction)
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (count > 0) color else KartThemeTokens.colors.textSecondary.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

private data class DonutSlice(val label: String, val count: Int, val color: Color)

@Composable
private fun AdminDonutChartCard(
    title: String,
    total: Int,
    slices: List<DonutSlice>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val sweepProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 340, easing = AdminChartEaseOut),
        label = "donut_$title"
    )
    val safeTotal = total.coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 11.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = Color.Gray.copy(alpha = 0.16f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    var startAngle = -90f
                    slices.forEach { slice ->
                        if (slice.count > 0) {
                            val sweep = (slice.count.toFloat() / safeTotal.toFloat()) * 360f * sweepProgress
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                }
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                slices.forEach { slice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(slice.color))
                            Text(slice.label, style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
                        }
                        Text("${slice.count}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
