package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.data.repository.AdminDashboardBundle
import com.example.domain.model.AccountDeletionRequestRow
import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.CampusRow
import com.example.domain.model.KartFormatters
import com.example.domain.model.Order
import com.example.domain.model.Product
import com.example.domain.model.Profile
import com.example.domain.model.RefundRequestRow
import com.example.domain.model.ReportRow
import com.example.domain.model.Review
import com.example.domain.model.VerificationRequestRow
import com.example.domain.model.WithdrawalRequestRow
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
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

sealed interface AdminDetailTarget {
    data class User(val profile: Profile) : AdminDetailTarget
    data class Listing(val product: Product) : AdminDetailTarget
    data class OrderItem(val order: Order) : AdminDetailTarget
    data class Kyc(val request: VerificationRequestRow) : AdminDetailTarget
    data class Withdrawal(val request: WithdrawalRequestRow) : AdminDetailTarget
    data class Refund(val request: RefundRequestRow) : AdminDetailTarget
    data class Report(val report: ReportRow) : AdminDetailTarget
    data class ReviewItem(val review: Review) : AdminDetailTarget
    data class Ad(val ad: ActiveAdvertisementRow) : AdminDetailTarget
    data class Campus(val campus: CampusRow) : AdminDetailTarget
    data class DeletionRequest(val request: AccountDeletionRequestRow) : AdminDetailTarget
}

data class AdminOverviewStats(
    val totalUsers: Int,
    val bannedUsers: Int,
    val verifiedUsers: Int,
    val activeListings: Int,
    val suspendedListings: Int,
    val totalOrders: Int,
    val totalGmv: Double,
    val totalPlatformFees: Double,
    val pendingKyc: Int,
    val pendingWithdrawals: Int,
    val pendingRefunds: Int,
    val openReports: Int,
    val openProductReports: Int,
    val openUserReports: Int,
    val totalReviews: Int,
    val activeAds: Int
)

object AdminConsoleHelpers {
    fun computeOverviewStats(bundle: AdminDashboardBundle?): AdminOverviewStats {
        if (bundle == null) {
            return AdminOverviewStats(0, 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
        val openReportRows = bundle.reports.filter {
            it.status.isNullOrBlank() || it.status.equals("Pending", true) || it.status.equals("Open", true)
        }
        return AdminOverviewStats(
            totalUsers = bundle.users.size,
            bannedUsers = bundle.users.count { it.banned == true },
            verifiedUsers = bundle.users.count { it.isVerified == true },
            activeListings = bundle.products.count { it.isActiveListing },
            suspendedListings = bundle.products.count {
                it.status.equals("Banned", true) || it.status.equals("Suspended", true)
            },
            totalOrders = bundle.orders.size,
            totalGmv = bundle.orders.sumOf { it.totalAmount ?: 0.0 },
            totalPlatformFees = bundle.orders.sumOf { it.platformFeeTotal ?: 0.0 },
            pendingKyc = bundle.verifications.count { isPendingStatus(it.status) },
            pendingWithdrawals = bundle.withdrawals.count { isPendingStatus(it.status) },
            pendingRefunds = bundle.refunds.count { isPendingStatus(it.status) },
            openReports = openReportRows.size,
            openProductReports = openReportRows.count { it.resolvedTargetType.equals("product", true) || it.resolvedTargetType.equals("listing", true) },
            openUserReports = openReportRows.count { it.resolvedTargetType.equals("user", true) || it.resolvedTargetType.equals("profile", true) },
            totalReviews = bundle.reviews.size,
            activeAds = bundle.advertisements.count {
                it.status.isNullOrBlank() || it.status.equals("Active", true)
            }
        )
    }

    fun isPendingStatus(status: String?): Boolean =
        status.isNullOrBlank() || status.equals("Pending", true)

    fun filterUsers(users: List<Profile>, query: String, filter: String): List<Profile> {
        val q = query.trim().lowercase()
        return users.filter { user ->
            val matchesFilter = when (filter) {
                "Active" -> user.banned != true
                "Banned" -> user.banned == true
                "Verified" -> user.isVerified == true
                "Admins" -> user.isAdmin == true
                else -> true
            }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                user.resolvedName,
                user.username,
                user.email,
                user.campus,
                user.phone,
                user.id
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterProducts(
        products: List<Product>,
        usersById: Map<String, Profile>,
        query: String,
        filter: String
    ): List<Product> {
        val q = query.trim().lowercase()
        return products.filter { p ->
            val matchesFilter = when (filter) {
                "Active" -> p.isActiveListing
                "Suspended" -> p.status.equals("Banned", true) || p.status.equals("Suspended", true)
                "Sold" -> p.status.equals("Sold", true) || p.isOutOfStock
                else -> true
            }
            val seller = p.sellerId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                p.title,
                p.category,
                p.campus,
                p.status,
                p.id,
                seller?.resolvedName,
                seller?.email
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterOrders(
        orders: List<Order>,
        usersById: Map<String, Profile>,
        query: String,
        filter: String
    ): List<Order> {
        val q = query.trim().lowercase()
        return orders.filter { o ->
            val status = o.status.orEmpty()
            val matchesFilter = when (filter) {
                "Pending" -> isPendingStatus(status) || status.equals("Paid", true) || status.equals("In Escrow", true)
                "Completed" -> status.equals("Completed", true) || status.equals("Delivered", true)
                "Refunded" -> status.equals("Refunded", true) || status.equals("Cancelled", true)
                else -> true
            }
            val buyer = o.buyerId?.let { usersById[it] }
            val seller = o.sellerId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                o.id,
                o.product?.title,
                o.status,
                o.paymentReference,
                o.paymentMethod,
                buyer?.resolvedName,
                buyer?.email,
                seller?.resolvedName,
                seller?.email
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterVerifications(
        verifications: List<VerificationRequestRow>,
        usersById: Map<String, Profile>,
        query: String,
        filter: String
    ): List<VerificationRequestRow> {
        val q = query.trim().lowercase()
        return verifications.filter { v ->
            val matchesFilter = when (filter) {
                "Pending" -> isPendingStatus(v.status)
                "Approved" -> v.status.equals("Approved", true) || v.status.equals("Verified", true)
                "Rejected" -> v.status.equals("Rejected", true)
                else -> true
            }
            val applicant = v.userId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                v.id,
                v.fullName,
                v.resolvedStudentId,
                v.campus,
                applicant?.resolvedName,
                applicant?.email
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterWithdrawals(
        withdrawals: List<WithdrawalRequestRow>,
        usersById: Map<String, Profile>,
        query: String,
        filter: String
    ): List<WithdrawalRequestRow> {
        val q = query.trim().lowercase()
        return withdrawals.filter { w ->
            val matchesFilter = when (filter) {
                "Pending" -> isPendingStatus(w.status)
                "Approved" -> w.status.equals("Approved", true) || w.status.equals("Completed", true)
                "Rejected" -> w.status.equals("Rejected", true)
                else -> true
            }
            val requester = w.userId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                w.id,
                w.payoutMethod,
                w.accountDetails,
                w.status,
                requester?.resolvedName,
                requester?.email
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterRefunds(
        refunds: List<RefundRequestRow>,
        usersById: Map<String, Profile>,
        ordersById: Map<String, Order>,
        query: String,
        filter: String
    ): List<RefundRequestRow> {
        val q = query.trim().lowercase()
        return refunds.filter { r ->
            val matchesFilter = when (filter) {
                "Pending" -> isPendingStatus(r.status)
                "Approved" -> r.status.equals("Approved", true) || r.status.equals("Refunded", true)
                "Rejected" -> r.status.equals("Rejected", true)
                else -> true
            }
            val buyer = r.buyerId?.let { usersById[it] }
            val order = r.orderId?.let { ordersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                r.id,
                r.orderId,
                r.reason,
                r.status,
                buyer?.resolvedName,
                buyer?.email,
                order?.product?.title
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterReports(
        reports: List<ReportRow>,
        usersById: Map<String, Profile>,
        productsById: Map<String, Product>,
        query: String,
        filter: String
    ): List<ReportRow> {
        val q = query.trim().lowercase()
        return reports.filter { rep ->
            val tType = rep.resolvedTargetType
            val tId = rep.resolvedTargetId
            val isResolved = rep.status.equals("Resolved", true) || rep.status.equals("Dismissed", true)
            val matchesFilter = when (filter) {
                "All" -> true
                "Open" -> !isResolved
                "Resolved" -> isResolved
                "Product" -> !isResolved && (tType.equals("product", true) || tType.equals("listing", true))
                "User" -> !isResolved && (tType.equals("user", true) || tType.equals("profile", true))
                else -> true
            }
            val reporter = rep.reporterId?.let { usersById[it] }
            val targetProduct = tId?.let { productsById[it] }
            val targetUser = tId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                rep.id,
                rep.reason,
                rep.resolvedDetails,
                tType,
                tId,
                reporter?.resolvedName,
                targetProduct?.title,
                targetUser?.resolvedName
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterReviews(
        reviews: List<Review>,
        usersById: Map<String, Profile>,
        productsById: Map<String, Product>,
        query: String
    ): List<Review> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return reviews
        return reviews.filter { rev ->
            val reviewer = (rev.reviewerId ?: rev.buyerId)?.let { usersById[it] }
            val seller = rev.sellerId?.let { usersById[it] }
            val product = rev.productId?.let { productsById[it] }
            listOfNotNull(
                rev.comment,
                rev.reviewer?.displayName,
                reviewer?.resolvedName,
                seller?.resolvedName,
                product?.title
            ).any { it.lowercase().contains(q) }
        }
    }

    fun filterAds(
        ads: List<ActiveAdvertisementRow>,
        productsById: Map<String, Product>,
        query: String,
        filter: String
    ): List<ActiveAdvertisementRow> {
        val q = query.trim().lowercase()
        return ads.filter { ad ->
            val matchesFilter = when (filter) {
                "Active" -> ad.status.isNullOrBlank() || ad.status.equals("Active", true)
                "Paused" -> ad.status.equals("Paused", true)
                "Ended" -> ad.status.equals("Ended", true) || ad.status.equals("Expired", true)
                else -> true
            }
            val product = ad.product ?: ad.productId?.let { productsById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                ad.id,
                ad.adType,
                ad.status,
                product?.title
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }

    fun filterCampuses(campuses: List<CampusRow>, query: String): List<CampusRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return campuses
        return campuses.filter { c ->
            c.name.lowercase().contains(q) || (c.abbreviation?.lowercase()?.contains(q) == true)
        }
    }

    fun resolveLiveTarget(target: AdminDetailTarget, bundle: AdminDashboardBundle): AdminDetailTarget = when (target) {
        is AdminDetailTarget.User -> bundle.usersById[target.profile.id]?.let { AdminDetailTarget.User(it) } ?: target
        is AdminDetailTarget.Listing -> bundle.productsById[target.product.id]?.let { AdminDetailTarget.Listing(it) } ?: target
        is AdminDetailTarget.OrderItem -> bundle.ordersById[target.order.id]?.let { AdminDetailTarget.OrderItem(it) } ?: target
        is AdminDetailTarget.Kyc -> bundle.verifications.firstOrNull { it.id == target.request.id }?.let { AdminDetailTarget.Kyc(it) } ?: target
        is AdminDetailTarget.Withdrawal -> bundle.withdrawals.firstOrNull { it.id == target.request.id }?.let { AdminDetailTarget.Withdrawal(it) } ?: target
        is AdminDetailTarget.Refund -> bundle.refunds.firstOrNull { it.id == target.request.id }?.let { AdminDetailTarget.Refund(it) } ?: target
        is AdminDetailTarget.Report -> bundle.reports.firstOrNull { it.id == target.report.id }?.let { AdminDetailTarget.Report(it) } ?: target
        is AdminDetailTarget.ReviewItem -> bundle.reviews.firstOrNull { it.id == target.review.id }?.let { AdminDetailTarget.ReviewItem(it) } ?: target
        is AdminDetailTarget.Ad -> bundle.advertisements.firstOrNull { it.id == target.ad.id }?.let { AdminDetailTarget.Ad(it) } ?: target
        is AdminDetailTarget.Campus -> bundle.campuses.firstOrNull { it.id == target.campus.id && it.name == target.campus.name }?.let { AdminDetailTarget.Campus(it) } ?: target
        is AdminDetailTarget.DeletionRequest -> bundle.deletionRequests.firstOrNull { it.id == target.request.id }?.let { AdminDetailTarget.DeletionRequest(it) } ?: target
    }

    fun filterDeletionRequests(
        requests: List<AccountDeletionRequestRow>,
        usersById: Map<String, Profile>,
        query: String,
        filter: String
    ): List<AccountDeletionRequestRow> {
        val q = query.trim().lowercase()
        return requests.filter { req ->
            val matchesFilter = when (filter) {
                "Pending" -> isPendingStatus(req.status)
                "Approved" -> req.status.equals("approved", true)
                "Rejected" -> req.status.equals("rejected", true)
                else -> true
            }
            val user = req.userId?.let { usersById[it] }
            val matchesQuery = q.isEmpty() || listOfNotNull(
                req.id,
                req.email,
                req.reason,
                req.status,
                user?.resolvedName,
                user?.email
            ).any { it.lowercase().contains(q) }
            matchesFilter && matchesQuery
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetailBottomSheet(
    target: AdminDetailTarget,
    bundle: AdminDashboardBundle,
    currentUserId: String,
    accessToken: String = "",
    actionLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectTarget: (AdminDetailTarget) -> Unit,
    onInspectImage: (String, String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenSeller: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onFilterCampus: (tab: String, campusName: String) -> Unit,
    onRequestConfirm: (title: String, message: String, action: () -> Unit) -> Unit,
    onToggleBan: (String, Boolean) -> Unit,
    onUpdateProductStatus: (String, String) -> Unit,
    onDeleteProduct: (String) -> Unit = {},
    onResolveVerification: (String, Boolean, String?) -> Unit,
    onRequestRejectKyc: (String) -> Unit = {},
    onResolveWithdrawal: (String, Boolean) -> Unit,
    onResolveRefund: (String, String, Boolean) -> Unit,
    onEditCampus: (CampusRow) -> Unit = {},
    onDeleteCampus: (CampusRow) -> Unit = {},
    onUpdateAdStatus: (String, String) -> Unit = { _, _ -> },
    onDeleteAd: (String) -> Unit = {},
    onUpdateReportStatus: (String, String) -> Unit = { _, _ -> },
    onResolveDeletionRequest: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    onDeleteReview: (String) -> Unit = {},
    onCopied: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            when (target) {
                is AdminDetailTarget.User -> AdminUserDetailContent(
                    user = target.profile,
                    bundle = bundle,
                    isSelf = currentUserId.isNotBlank() && target.profile.id == currentUserId,
                    actionLoading = actionLoading,
                    onOpenSeller = { onDismiss(); onOpenSeller(target.profile.id) },
                    onToggleBan = { ban ->
                        onRequestConfirm(
                            if (ban) "Suspend ${target.profile.resolvedName}?" else "Reinstate ${target.profile.resolvedName}?",
                            if (ban) "This immediately blocks the user from accessing Kart." else "This restores the user's access to Kart."
                        ) {
                            onDismiss()
                            onToggleBan(target.profile.id, ban)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Listing -> AdminListingDetailContent(
                    product = target.product,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onInspectImage = onInspectImage,
                    onOpenSellerProfile = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onOpenProduct = { onDismiss(); onOpenProduct(target.product.id) },
                    onUpdateStatus = { newStatus ->
                        if (newStatus.equals("Banned", true) || newStatus.equals("Suspended", true)) {
                            onRequestConfirm(
                                "Suspend Listing?",
                                "Suspend \"${target.product.title}\" so it can no longer be purchased?"
                            ) {
                                onDismiss()
                                onUpdateProductStatus(target.product.id, "Banned")
                            }
                        } else {
                            onDismiss()
                            onUpdateProductStatus(target.product.id, newStatus)
                        }
                    },
                    onDelete = {
                        onRequestConfirm(
                            "Delete Listing?",
                            "Permanently delete \"${target.product.title}\"? This action cannot be undone."
                        ) {
                            onDismiss()
                            onDeleteProduct(target.product.id)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.OrderItem -> AdminOrderDetailContent(
                    order = target.order,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onOpenProduct = { productId -> onDismiss(); onOpenProduct(productId) },
                    onOpenOrder = { onDismiss(); onOpenOrder(target.order.id) },
                    onRefundOrder = {
                        onRequestConfirm(
                            "Issue Buyer Refund?",
                            "Refund ${KartFormatters.formatCedis(target.order.totalAmount)} for Order #${target.order.id.take(8)} back to the buyer?"
                        ) {
                            onDismiss()
                            onResolveRefund(target.order.id, "", true)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Kyc -> AdminKycDetailContent(
                    request = target.request,
                    bundle = bundle,
                    accessToken = accessToken,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onInspectImage = onInspectImage,
                    onResolve = { approve ->
                        if (approve) {
                            onRequestConfirm(
                                "Approve KYC Verification?",
                                "Grant verified identity status to this account after inspecting the ID and selfie?"
                            ) {
                                onDismiss()
                                onResolveVerification(target.request.id, true, null)
                            }
                        } else {
                            onDismiss()
                            onRequestRejectKyc(target.request.id)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Withdrawal -> AdminWithdrawalDetailContent(
                    request = target.request,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onResolve = { approve ->
                        onRequestConfirm(
                            if (approve) "Approve Withdrawal?" else "Reject Withdrawal?",
                            if (approve) "Release ${KartFormatters.formatCedis(target.request.amount)} via ${target.request.payoutMethod ?: "MoMo"} to ${target.request.accountDetails ?: "the listed account"}?"
                            else "Reject this ${KartFormatters.formatCedis(target.request.amount)} withdrawal request?"
                        ) {
                            onDismiss()
                            onResolveWithdrawal(target.request.id, approve)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Refund -> AdminRefundDetailContent(
                    request = target.request,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onSelectOrder = { order -> onSelectTarget(AdminDetailTarget.OrderItem(order)) },
                    onOpenOrder = { orderId -> onDismiss(); onOpenOrder(orderId) },
                    onResolve = { approve ->
                        onRequestConfirm(
                            if (approve) "Issue Buyer Refund?" else "Reject Refund Dispute?",
                            if (approve) "Approve this refund dispute and return escrow funds to the buyer?"
                            else "Reject this buyer's refund request?"
                        ) {
                            onDismiss()
                            onResolveRefund(target.request.orderId.orEmpty(), target.request.id, approve)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Report -> AdminReportDetailContent(
                    report = target.report,
                    bundle = bundle,
                    currentUserId = currentUserId,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onSelectListing = { product -> onSelectTarget(AdminDetailTarget.Listing(product)) },
                    onOpenProduct = { productId -> onDismiss(); onOpenProduct(productId) },
                    onOpenSeller = { sellerId -> onDismiss(); onOpenSeller(sellerId) },
                    onSuspendUser = { user ->
                        onRequestConfirm(
                            "Suspend Reported User?",
                            "Suspend ${user.resolvedName} immediately based on this report?"
                        ) {
                            onDismiss()
                            onToggleBan(user.id, true)
                        }
                    },
                    onSuspendListing = { product ->
                        onRequestConfirm(
                            "Suspend Reported Listing?",
                            "Suspend \"${product.title}\" based on this report?"
                        ) {
                            onDismiss()
                            onUpdateProductStatus(product.id, "Banned")
                        }
                    },
                    onUpdateReportStatus = { newStatus ->
                        onDismiss()
                        onUpdateReportStatus(target.report.id, newStatus)
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.ReviewItem -> AdminReviewDetailContent(
                    review = target.review,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onOpenProduct = { productId -> onDismiss(); onOpenProduct(productId) },
                    onDelete = {
                        onRequestConfirm(
                            "Delete Review?",
                            "Permanently delete this ${target.review.rating}-star review?"
                        ) {
                            onDismiss()
                            onDeleteReview(target.review.id)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Ad -> AdminAdDetailContent(
                    ad = target.ad,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onOpenProduct = { productId -> onDismiss(); onOpenProduct(productId) },
                    onUpdateStatus = { newStatus ->
                        onDismiss()
                        onUpdateAdStatus(target.ad.id, newStatus)
                    },
                    onDelete = {
                        onRequestConfirm(
                            "Delete Advertisement?",
                            "Permanently delete this ${target.ad.adType ?: "promotion"} campaign?"
                        ) {
                            onDismiss()
                            onDeleteAd(target.ad.id)
                        }
                    },
                    onCopied = onCopied
                )

                is AdminDetailTarget.Campus -> AdminCampusDetailContent(
                    campus = target.campus,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onViewUsers = { onDismiss(); onFilterCampus("Users", target.campus.name) },
                    onViewListings = { onDismiss(); onFilterCampus("Listings", target.campus.name) },
                    onEdit = { onDismiss(); onEditCampus(target.campus) },
                    onDelete = {
                        onRequestConfirm(
                            "Delete ${target.campus.name}?",
                            "Remove this campus from the active campuses directory?"
                        ) {
                            onDismiss()
                            onDeleteCampus(target.campus)
                        }
                    }
                )

                is AdminDetailTarget.DeletionRequest -> AdminDeletionDetailContent(
                    request = target.request,
                    bundle = bundle,
                    actionLoading = actionLoading,
                    onSelectUser = { profile -> onSelectTarget(AdminDetailTarget.User(profile)) },
                    onResolve = { approve ->
                        onRequestConfirm(
                            if (approve) "Approve Account Deletion?" else "Reject Account Deletion?",
                            if (approve) "This will ban the user's account, suspend all their active listings, and mark this deletion request as approved."
                            else "Reject this user's account deletion request?"
                        ) {
                            onDismiss()
                            onResolveDeletionRequest(target.request.id, target.request.userId, approve)
                        }
                    },
                    onCopied = onCopied
                )
            }
        }
    }
}

@Composable
private fun AdminUserDetailContent(
    user: Profile,
    bundle: AdminDashboardBundle,
    isSelf: Boolean,
    actionLoading: Boolean,
    onOpenSeller: () -> Unit,
    onToggleBan: (Boolean) -> Unit,
    onCopied: (String) -> Unit
) {
    val userListings = bundle.products.count { it.sellerId == user.id }
    val userOrders = bundle.orders.count { it.buyerId == user.id || it.sellerId == user.id }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(avatarUrl = user.avatarUrl, displayName = user.resolvedName, size = 56.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.resolvedName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            user.username?.takeIf { it.isNotBlank() }?.let {
                Text("@$it", style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.isAdmin == true) StatusPill("Admin", KartPrimary.copy(alpha = 0.14f), KartPrimary)
                if (user.isVerified == true) StatusPill("Verified", KartSuccessContainer, KartSuccess)
                if (user.banned == true) StatusPill("Suspended", KartDangerContainer, KartDanger)
            }
        }
    }

    AdminDetailSection("Account Info") {
        AdminDetailRow("User ID", user.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Email", user.email ?: "—", copyable = !user.email.isNullOrBlank(), onCopied = onCopied)
        AdminDetailRow("Phone", user.phone ?: "—", copyable = !user.phone.isNullOrBlank(), onCopied = onCopied)
        user.whatsappNumber?.takeIf { it.isNotBlank() }?.let {
            AdminDetailRow("WhatsApp", it, copyable = true, onCopied = onCopied)
        }
        AdminDetailRow("Campus", user.campus ?: "—")
        AdminDetailRow("Joined", KartFormatters.timeAgo(user.createdAt))
        AdminDetailRow("Email / Phone Verified", "${if (user.emailVerified == true) "Yes" else "No"} / ${if (user.phoneVerified == true) "Yes" else "No"}")
    }

    AdminDetailSection("Marketplace Activity") {
        AdminDetailRow("Listings", "$userListings")
        AdminDetailRow("Orders (Buyer/Seller)", "$userOrders")
        AdminDetailRow("Rating", "${String.format("%.1f", user.averageRating ?: 0.0)} (${user.reviewsCount ?: 0} reviews)")
        AdminDetailRow("KYC Status", user.verificationStatus ?: if (user.isVerified == true) "Verified" else "Unverified")
        user.verificationRejectionReason?.takeIf { it.isNotBlank() }?.let {
            AdminDetailRow("KYC Rejection", it)
        }
        user.bio?.takeIf { it.isNotBlank() }?.let {
            AdminDetailRow("Bio", it)
        }
    }

    if (!user.momoNumber.isNullOrBlank() || !user.bankAccountNumber.isNullOrBlank()) {
        AdminDetailSection("Payout Details") {
            user.momoNetwork?.takeIf { it.isNotBlank() }?.let { AdminDetailRow("MoMo Network", it) }
            user.momoNumber?.takeIf { it.isNotBlank() }?.let { AdminDetailRow("MoMo Number", it, copyable = true, onCopied = onCopied) }
            user.bankName?.takeIf { it.isNotBlank() }?.let { AdminDetailRow("Bank", it) }
            user.bankAccountNumber?.takeIf { it.isNotBlank() }?.let { AdminDetailRow("Bank Account", it, copyable = true, onCopied = onCopied) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KartOutlineButton(
            text = "View Store",
            onClick = onOpenSeller,
            modifier = Modifier.weight(1f)
        )
        if (!isSelf) {
            val nextBan = user.banned != true
            KartOutlineButton(
                text = if (nextBan) "Suspend User" else "Reinstate User",
                enabled = !actionLoading,
                borderColor = if (nextBan) KartDanger else KartSuccess,
                contentColor = if (nextBan) KartDanger else KartSuccess,
                onClick = { onToggleBan(nextBan) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminListingDetailContent(
    product: Product,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onInspectImage: (String, String) -> Unit,
    onOpenSellerProfile: (Profile) -> Unit,
    onOpenProduct: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onCopied: (String) -> Unit
) {
    val seller = product.sellerId?.let { bundle.usersById[it] }
    val images = product.allImages

    Text(product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            KartFormatters.formatCedis(product.price),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = KartPrimary
        )
        StatusPill(
            text = product.status ?: "Active",
            containerColor = if (product.isActiveListing) KartSuccessContainer else KartDangerContainer,
            contentColor = if (product.isActiveListing) KartSuccess else KartDanger
        )
        if (product.isBoosted == true) StatusPill("Boosted", KartPrimary.copy(alpha = 0.14f), KartPrimary)
        if (product.isFeatured == true) StatusPill("Featured", KartWarningContainer, KartWarning)
    }

    if (images.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(images) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 160.dp, height = 120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspectImage(url, product.title) }
                )
            }
        }
        Text("Tap any image to inspect full-screen", style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
    }

    AdminDetailSection("Listing Details") {
        AdminDetailRow("Listing ID", product.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Category", product.category ?: "—")
        AdminDetailRow("Condition", product.condition ?: "—")
        AdminDetailRow("Campus", product.campus ?: "—")
        AdminDetailRow("Stock", "${product.stockQuantity ?: 1}")
        AdminDetailRow("Views / Shares", "${product.viewsCount ?: 0} / ${product.sharesCount ?: 0}")
        AdminDetailRow("Created", KartFormatters.timeAgo(product.createdAt))
        product.description?.takeIf { it.isNotBlank() }?.let {
            AdminDetailRow("Description", it)
        }
    }

    if (seller != null) {
        AdminClickablePersonRow(label = "Seller", profile = seller, onClick = { onOpenSellerProfile(seller) })
    }

    KartOutlineButton(text = "Open Live Product Page", onClick = onOpenProduct, modifier = Modifier.fillMaxWidth())

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KartPrimaryButton(
            text = "Activate",
            enabled = !actionLoading && !product.status.equals("Active", true),
            onClick = { onUpdateStatus("Active") },
            modifier = Modifier.weight(1f)
        )
        KartOutlineButton(
            text = "Suspend",
            enabled = !actionLoading && !product.status.equals("Banned", true) && !product.status.equals("Suspended", true),
            borderColor = KartDanger,
            contentColor = KartDanger,
            onClick = { onUpdateStatus("Banned") },
            modifier = Modifier.weight(1f)
        )
    }

    KartOutlineButton(
        text = "Delete Listing",
        enabled = !actionLoading,
        borderColor = KartDanger,
        contentColor = KartDanger,
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminOrderDetailContent(
    order: Order,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenOrder: () -> Unit,
    onRefundOrder: () -> Unit,
    onCopied: (String) -> Unit
) {
    val buyer = order.buyerId?.let { bundle.usersById[it] }
    val seller = order.sellerId?.let { bundle.usersById[it] }
    val isRefunded = order.status.equals("Refunded", true) || order.status.equals("Cancelled", true)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        order.product?.primaryImage?.let { img ->
            AsyncImage(
                model = img,
                contentDescription = order.product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.product?.title ?: "Order #${order.id.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusPill(
                text = order.status ?: "Pending",
                containerColor = if (isRefunded) KartDangerContainer else KartPrimary.copy(alpha = 0.14f),
                contentColor = if (isRefunded) KartDanger else KartPrimary
            )
        }
    }

    AdminDetailSection("Order & Payment") {
        AdminDetailRow("Order ID", order.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Quantity × Unit", "${order.quantity ?: 1} × ${KartFormatters.formatCedis(order.unitPrice)}")
        AdminDetailRow("Total Amount", KartFormatters.formatCedis(order.totalAmount))
        AdminDetailRow("Platform Fee", KartFormatters.formatCedis(order.platformFeeTotal))
        AdminDetailRow("Seller Payout", KartFormatters.formatCedis(order.sellerPayoutAmount))
        AdminDetailRow("Payment Method", order.paymentMethod ?: "—")
        AdminDetailRow("Payment Ref", order.paymentReference ?: "—", copyable = !order.paymentReference.isNullOrBlank(), onCopied = onCopied)
        AdminDetailRow("Delivery Verified", if (order.deliveryVerified == true) "Yes" else "No")
        AdminDetailRow("Created", KartFormatters.timeAgo(order.createdAt))
    }

    buyer?.let { AdminClickablePersonRow("Buyer", it, onClick = { onSelectUser(it) }) }
    seller?.let { AdminClickablePersonRow("Seller", it, onClick = { onSelectUser(it) }) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KartOutlineButton(text = "Open Order Page", onClick = onOpenOrder, modifier = Modifier.weight(1f))
        order.productId?.takeIf { it.isNotBlank() }?.let { pid ->
            KartOutlineButton(text = "Open Listing", onClick = { onOpenProduct(pid) }, modifier = Modifier.weight(1f))
        }
    }

    if (!isRefunded) {
        KartOutlineButton(
            text = "Issue Refund to Buyer",
            enabled = !actionLoading,
            borderColor = KartDanger,
            contentColor = KartDanger,
            onClick = onRefundOrder,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun buildAdminImageRequest(rawUrl: String, accessToken: String, isPrivateVerification: Boolean): Any {
    val context = LocalContext.current
    val trimmed = rawUrl.trim()
    val resolvedUrl = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else if (isPrivateVerification) {
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/authenticated/verifications/${trimmed.trimStart('/')}"
    } else {
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/${trimmed.trimStart('/')}"
    }
    val needsAuth = isPrivateVerification || resolvedUrl.contains("/storage/v1/object/authenticated/", true)
    return if (needsAuth && accessToken.isNotBlank()) {
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY.trim())
            .crossfade(true)
            .build()
    } else {
        resolvedUrl
    }
}

@Composable
private fun AdminKycDetailContent(
    request: VerificationRequestRow,
    bundle: AdminDashboardBundle,
    accessToken: String,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onInspectImage: (String, String) -> Unit,
    onResolve: (Boolean) -> Unit,
    onCopied: (String) -> Unit
) {
    val applicant = request.userId?.let { bundle.usersById[it] }
    val isPending = AdminConsoleHelpers.isPendingStatus(request.status)

    Text(
        request.fullName?.takeIf { it.isNotBlank() } ?: applicant?.resolvedName ?: "KYC #${request.id.take(8)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    AdminDetailSection("Verification Submission") {
        AdminDetailRow("Request ID", request.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Status", request.status ?: "Pending")
        AdminDetailRow("Student ID", request.resolvedStudentId ?: "—", copyable = !request.resolvedStudentId.isNullOrBlank(), onCopied = onCopied)
        AdminDetailRow("Campus", request.campus ?: applicant?.campus ?: "—")
        AdminDetailRow("Submitted", KartFormatters.timeAgo(request.createdAt))
        request.resolvedNotes?.let {
            AdminDetailRow("Admin Notes", it)
        }
    }

    applicant?.let { AdminClickablePersonRow("Applicant Account", it, onClick = { onSelectUser(it) }) }

    val idImg = request.resolvedIdImageUrl
    val selfieImg = request.resolvedSelfieImageUrl
    if (!idImg.isNullOrBlank() || !selfieImg.isNullOrBlank()) {
        Text("Submitted Documents (tap to zoom)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            idImg?.let { url ->
                val req = buildAdminImageRequest(url, accessToken, isPrivateVerification = true)
                val fullUrl = if (url.startsWith("http", true)) url else "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/authenticated/verifications/${url.trimStart('/')}"
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsyncImage(
                        model = req,
                        contentDescription = "ID Document",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onInspectImage(fullUrl, "Student / National ID") }
                    )
                    Text("Student / National ID", style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
                }
            }
            selfieImg?.let { url ->
                val req = buildAdminImageRequest(url, accessToken, isPrivateVerification = true)
                val fullUrl = if (url.startsWith("http", true)) url else "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/authenticated/verifications/${url.trimStart('/')}"
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsyncImage(
                        model = req,
                        contentDescription = "Selfie",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onInspectImage(fullUrl, "Applicant Face / Selfie") }
                    )
                    Text("Face / Selfie Photo", style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
                }
            }
        }
    }

    if (isPending) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartPrimaryButton(text = "Approve", enabled = !actionLoading, onClick = { onResolve(true) }, modifier = Modifier.weight(1f))
            KartOutlineButton(
                text = "Reject",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = { onResolve(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminWithdrawalDetailContent(
    request: WithdrawalRequestRow,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onResolve: (Boolean) -> Unit,
    onCopied: (String) -> Unit
) {
    val requester = request.userId?.let { bundle.usersById[it] }
    val isPending = AdminConsoleHelpers.isPendingStatus(request.status)

    Text(KartFormatters.formatCedis(request.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = KartPrimary)

    AdminDetailSection("Payout Request") {
        AdminDetailRow("Withdrawal ID", request.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Status", request.status ?: "Pending")
        AdminDetailRow("Method", request.payoutMethod ?: "MoMo")
        AdminDetailRow("Account Details", request.accountDetails ?: "—", copyable = !request.accountDetails.isNullOrBlank(), onCopied = onCopied)
        AdminDetailRow("Requested", KartFormatters.timeAgo(request.createdAt))
    }

    requester?.let {
        AdminClickablePersonRow("Seller Account", it, onClick = { onSelectUser(it) })
        if (!it.momoNumber.isNullOrBlank() || !it.bankAccountNumber.isNullOrBlank()) {
            AdminDetailSection("Saved Profile Payout Info") {
                it.momoNetwork?.takeIf { n -> n.isNotBlank() }?.let { n -> AdminDetailRow("Saved MoMo Network", n) }
                it.momoNumber?.takeIf { n -> n.isNotBlank() }?.let { n -> AdminDetailRow("Saved MoMo Number", n, copyable = true, onCopied = onCopied) }
                it.bankName?.takeIf { n -> n.isNotBlank() }?.let { n -> AdminDetailRow("Saved Bank", n) }
                it.bankAccountNumber?.takeIf { n -> n.isNotBlank() }?.let { n -> AdminDetailRow("Saved Bank Account", n, copyable = true, onCopied = onCopied) }
            }
        }
    }

    if (isPending) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartPrimaryButton(text = "Approve Payout", enabled = !actionLoading, onClick = { onResolve(true) }, modifier = Modifier.weight(1f))
            KartOutlineButton(
                text = "Reject",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = { onResolve(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminRefundDetailContent(
    request: RefundRequestRow,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onSelectOrder: (Order) -> Unit,
    onOpenOrder: (String) -> Unit,
    onResolve: (Boolean) -> Unit,
    onCopied: (String) -> Unit
) {
    val order = request.orderId?.let { bundle.ordersById[it] }
    val buyer = (request.buyerId ?: order?.buyerId)?.let { bundle.usersById[it] }
    val seller = order?.sellerId?.let { bundle.usersById[it] }
    val isPending = AdminConsoleHelpers.isPendingStatus(request.status)

    Text(order?.product?.title ?: "Refund #${request.id.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    AdminDetailSection("Dispute Details") {
        AdminDetailRow("Refund ID", request.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Status", request.status ?: "Pending")
        AdminDetailRow("Reason", request.reason ?: "No reason provided")
        AdminDetailRow("Filed", KartFormatters.timeAgo(request.createdAt))
        request.orderId?.let { AdminDetailRow("Order ID", it, copyable = true, onCopied = onCopied) }
    }

    if (order != null) {
        AdminDetailSection("Linked Order") {
            AdminDetailRow("Order Total", KartFormatters.formatCedis(order.totalAmount))
            AdminDetailRow("Order Status", order.status ?: "Pending")
            AdminDetailRow("Payment Method", order.paymentMethod ?: "—")
            AdminDetailRow("Delivery Verified", if (order.deliveryVerified == true) "Yes" else "No")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartOutlineButton(text = "Inspect Order", onClick = { onSelectOrder(order) }, modifier = Modifier.weight(1f))
            KartOutlineButton(text = "Open Order Page", onClick = { onOpenOrder(order.id) }, modifier = Modifier.weight(1f))
        }
    }

    buyer?.let { AdminClickablePersonRow("Buyer", it, onClick = { onSelectUser(it) }) }
    seller?.let { AdminClickablePersonRow("Seller", it, onClick = { onSelectUser(it) }) }

    if (isPending) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartPrimaryButton(text = "Refund Buyer", enabled = !actionLoading, onClick = { onResolve(true) }, modifier = Modifier.weight(1f))
            KartOutlineButton(
                text = "Reject",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = { onResolve(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminReportDetailContent(
    report: ReportRow,
    bundle: AdminDashboardBundle,
    currentUserId: String,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onSelectListing: (Product) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenSeller: (String) -> Unit,
    onSuspendUser: (Profile) -> Unit,
    onSuspendListing: (Product) -> Unit,
    onUpdateReportStatus: (String) -> Unit,
    onCopied: (String) -> Unit
) {
    val reporter = report.reporterId?.let { bundle.usersById[it] }
    val targetId = report.resolvedTargetId
    val targetProduct = targetId?.let { bundle.productsById[it] }
    val targetUser = targetId?.let { bundle.usersById[it] }

    Text(report.reason ?: "User Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    AdminDetailSection("Report Details") {
        AdminDetailRow("Report ID", report.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Status", report.status ?: "Open")
        AdminDetailRow("Target Type", report.resolvedTargetType.replaceFirstChar { it.uppercase() })
        targetId?.let { AdminDetailRow("Target ID", it, copyable = true, onCopied = onCopied) }
        AdminDetailRow("Details", report.resolvedDetails ?: "No additional details provided.")
        AdminDetailRow("Reported", KartFormatters.timeAgo(report.createdAt))
    }

    reporter?.let { AdminClickablePersonRow("Reported By", it, onClick = { onSelectUser(it) }) }

    if (targetProduct != null) {
        AdminDetailSection("Reported Listing") {
            AdminDetailRow("Title", targetProduct.title)
            AdminDetailRow("Price", KartFormatters.formatCedis(targetProduct.price))
            AdminDetailRow("Status", targetProduct.status ?: "Active")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartOutlineButton(text = "Inspect Listing", onClick = { onSelectListing(targetProduct) }, modifier = Modifier.weight(1f))
            KartOutlineButton(text = "Open Page", onClick = { onOpenProduct(targetProduct.id) }, modifier = Modifier.weight(1f))
        }
        if (!targetProduct.status.equals("Banned", true) && !targetProduct.status.equals("Suspended", true)) {
            KartOutlineButton(
                text = "Suspend Reported Listing",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = { onSuspendListing(targetProduct) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else if (targetUser != null) {
        AdminClickablePersonRow("Reported User", targetUser, onClick = { onSelectUser(targetUser) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartOutlineButton(text = "View Store", onClick = { onOpenSeller(targetUser.id) }, modifier = Modifier.weight(1f))
            if (targetUser.id != currentUserId && targetUser.banned != true) {
                KartOutlineButton(
                    text = "Suspend User",
                    enabled = !actionLoading,
                    borderColor = KartDanger,
                    contentColor = KartDanger,
                    onClick = { onSuspendUser(targetUser) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val isResolved = report.status.equals("Resolved", true) || report.status.equals("Dismissed", true)
    if (!isResolved) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartPrimaryButton(
                text = "Mark Resolved",
                enabled = !actionLoading,
                onClick = { onUpdateReportStatus("Resolved") },
                modifier = Modifier.weight(1f)
            )
            KartOutlineButton(
                text = "Dismiss",
                enabled = !actionLoading,
                onClick = { onUpdateReportStatus("Dismissed") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminReviewDetailContent(
    review: Review,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onOpenProduct: (String) -> Unit,
    onDelete: () -> Unit,
    onCopied: (String) -> Unit
) {
    val reviewer = (review.reviewerId ?: review.buyerId)?.let { bundle.usersById[it] }
    val seller = review.sellerId?.let { bundle.usersById[it] }
    val product = review.productId?.let { bundle.productsById[it] }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = KartWarning, modifier = Modifier.size(22.dp))
        Text("${review.rating} / 5 Rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }

    AdminDetailSection("Review Details") {
        AdminDetailRow("Review ID", review.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Comment", review.comment?.takeIf { it.isNotBlank() } ?: "No written comment")
        AdminDetailRow("Posted", KartFormatters.timeAgo(review.createdAt))
        product?.let { AdminDetailRow("Product", it.title) }
    }

    reviewer?.let { AdminClickablePersonRow("Reviewer", it, onClick = { onSelectUser(it) }) }
    seller?.let { AdminClickablePersonRow("Seller", it, onClick = { onSelectUser(it) }) }
    review.productId?.takeIf { it.isNotBlank() }?.let { pid ->
        KartOutlineButton(text = "Open Product Page", onClick = { onOpenProduct(pid) }, modifier = Modifier.fillMaxWidth())
    }
    KartOutlineButton(
        text = "Delete Review",
        enabled = !actionLoading,
        borderColor = KartDanger,
        contentColor = KartDanger,
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminAdDetailContent(
    ad: ActiveAdvertisementRow,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onOpenProduct: (String) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onCopied: (String) -> Unit
) {
    val product = ad.product ?: ad.productId?.let { bundle.productsById[it] }
    val views = ad.views ?: 0
    val clicks = ad.clicks ?: 0
    val ctr = if (views > 0) String.format("%.1f%%", (clicks.toDouble() / views.toDouble()) * 100.0) else "0.0%"
    val isActive = ad.status.isNullOrBlank() || ad.status.equals("Active", true)

    Text(product?.title ?: "${ad.adType ?: "Promotion"} #${ad.id.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    AdminDetailSection("Campaign Metrics") {
        AdminDetailRow("Ad ID", ad.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Ad Type", ad.adType ?: "Boost")
        AdminDetailRow("Status", ad.status ?: "Active")
        AdminDetailRow("Price Paid", KartFormatters.formatCedis(ad.price))
        AdminDetailRow("Duration", "${ad.durationDays ?: 0} days")
        AdminDetailRow("Views / Clicks (CTR)", "$views / $clicks ($ctr)")
        ad.startDate?.let { AdminDetailRow("Start Date", KartFormatters.timeAgo(it)) }
        ad.endDate?.let { AdminDetailRow("End Date", KartFormatters.timeAgo(it)) }
    }

    (ad.productId ?: product?.id)?.takeIf { it.isNotBlank() }?.let { pid ->
        KartOutlineButton(text = "Open Promoted Listing", onClick = { onOpenProduct(pid) }, modifier = Modifier.fillMaxWidth())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KartPrimaryButton(
            text = "Enable",
            enabled = !actionLoading && !isActive,
            onClick = { onUpdateStatus("Active") },
            modifier = Modifier.weight(1f)
        )
        KartOutlineButton(
            text = "Disable",
            enabled = !actionLoading && isActive,
            borderColor = KartWarning,
            contentColor = KartWarning,
            onClick = { onUpdateStatus("Paused") },
            modifier = Modifier.weight(1f)
        )
    }

    KartOutlineButton(
        text = "Delete Ad",
        enabled = !actionLoading,
        borderColor = KartDanger,
        contentColor = KartDanger,
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminCampusDetailContent(
    campus: CampusRow,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onViewUsers: () -> Unit,
    onViewListings: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val campusUsers = bundle.users.filter { it.campus.equals(campus.name, true) }
    val verifiedUsers = campusUsers.count { it.isVerified == true }
    val campusListings = bundle.products.filter { it.campus.equals(campus.name, true) }
    val activeListings = campusListings.count { it.isActiveListing }

    Text(campus.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    AdminDetailSection("Campus Snapshot") {
        AdminDetailRow("Abbreviation", campus.abbreviation ?: "—")
        AdminDetailRow("Loaded Users", "${campusUsers.size}")
        AdminDetailRow("Verified Users", "$verifiedUsers")
        AdminDetailRow("Active Listings", "$activeListings")
        AdminDetailRow("Total Listings", "${campusListings.size}")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KartOutlineButton(text = "View Users", onClick = onViewUsers, modifier = Modifier.weight(1f))
        KartPrimaryButton(text = "View Listings", onClick = onViewListings, modifier = Modifier.weight(1f))
    }
    if (!campus.id.isNullOrBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartOutlineButton(
                text = "Edit Campus",
                enabled = !actionLoading,
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
            KartOutlineButton(
                text = "Delete",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminDeletionDetailContent(
    request: AccountDeletionRequestRow,
    bundle: AdminDashboardBundle,
    actionLoading: Boolean,
    onSelectUser: (Profile) -> Unit,
    onResolve: (Boolean) -> Unit,
    onCopied: (String) -> Unit
) {
    val user = request.userId?.let { bundle.usersById[it] }
    val isPending = AdminConsoleHelpers.isPendingStatus(request.status)

    Text(
        user?.resolvedName ?: request.email ?: "Deletion #${request.id.take(8)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    AdminDetailSection("Deletion Request") {
        AdminDetailRow("Request ID", request.id, copyable = true, onCopied = onCopied)
        AdminDetailRow("Status", (request.status ?: "pending").replaceFirstChar { it.uppercase() })
        AdminDetailRow("Email", request.email ?: user?.email ?: "—", copyable = !(request.email ?: user?.email).isNullOrBlank(), onCopied = onCopied)
        AdminDetailRow("Reason", request.reason?.takeIf { it.isNotBlank() } ?: "No reason provided.")
        AdminDetailRow("Submitted", KartFormatters.timeAgo(request.createdAt))
    }

    user?.let { AdminClickablePersonRow("User Account", it, onClick = { onSelectUser(it) }) }

    if (isPending) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KartPrimaryButton(
                text = "Approve",
                enabled = !actionLoading,
                onClick = { onResolve(true) },
                modifier = Modifier.weight(1f)
            )
            KartOutlineButton(
                text = "Reject",
                enabled = !actionLoading,
                borderColor = KartDanger,
                contentColor = KartDanger,
                onClick = { onResolve(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AdminFullScreenImageDialog(
    imageUrl: String,
    title: String,
    accessToken: String = "",
    onDismiss: () -> Unit
) {
    val imageModel = buildAdminImageRequest(
        rawUrl = imageUrl,
        accessToken = accessToken,
        isPrivateVerification = imageUrl.contains("/verifications/", true)
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close image", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AdminDetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = KartThemeTokens.colors.textSecondary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun AdminDetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    onCopied: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = KartThemeTokens.colors.textSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(0.58f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (copyable && value.isNotBlank() && value != "—") {
                Text(
                    text = "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = KartPrimary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(value))
                            onCopied("$label copied")
                        }
                )
            }
        }
    }
}

@Composable
private fun AdminClickablePersonRow(
    label: String,
    profile: Profile,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            UserAvatar(avatarUrl = profile.avatarUrl, displayName = profile.resolvedName, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = KartThemeTokens.colors.textSecondary)
                Text(profile.resolvedName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                profile.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Inspect $label", tint = KartPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
