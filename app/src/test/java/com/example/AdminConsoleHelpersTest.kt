package com.example

import com.example.data.repository.AdminDashboardBundle
import com.example.domain.model.ActiveAdvertisementRow
import com.example.domain.model.CampusRow
import com.example.domain.model.Order
import com.example.domain.model.OrderProduct
import com.example.domain.model.Product
import com.example.domain.model.Profile
import com.example.domain.model.RefundRequestRow
import com.example.domain.model.ReportRow
import com.example.domain.model.Review
import com.example.domain.model.VerificationRequestRow
import com.example.domain.model.WithdrawalRequestRow
import com.example.ui.screens.AdminConsoleHelpers
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminConsoleHelpersTest {

    private val adminUser = Profile(
        id = "u-admin",
        displayName = "Kwame Admin",
        email = "admin@kart.cx",
        campus = "KNUST (Kumasi)",
        isAdmin = true,
        isVerified = true,
        banned = false
    )
    private val bannedUser = Profile(
        id = "u-banned",
        displayName = "Kojo Suspended",
        email = "kojo@ug.edu.gh",
        campus = "University of Ghana (Legon)",
        isAdmin = false,
        isVerified = false,
        banned = true
    )
    private val activeProduct = Product(
        id = "p-1",
        title = "MacBook Air M2",
        price = 8500.0,
        campus = "KNUST (Kumasi)",
        sellerId = "u-admin",
        status = "Active"
    )
    private val suspendedProduct = Product(
        id = "p-2",
        title = "Counterfeit Watch",
        price = 250.0,
        campus = "University of Ghana (Legon)",
        sellerId = "u-banned",
        status = "Suspended"
    )
    private val sampleOrder = Order(
        id = "ord-12345",
        buyerId = "u-banned",
        sellerId = "u-admin",
        productId = "p-1",
        totalAmount = 8500.0,
        platformFeeTotal = 425.0,
        sellerPayoutAmount = 8075.0,
        status = "In Escrow",
        paymentReference = "PAY-999",
        product = OrderProduct(id = "p-1", title = "MacBook Air M2")
    )

    @Test
    fun computeOverviewStats_aggregatesGmvFeesAndPendingQueues() {
        val bundle = AdminDashboardBundle(
            users = listOf(adminUser, bannedUser),
            products = listOf(activeProduct, suspendedProduct),
            orders = listOf(sampleOrder),
            verifications = listOf(
                VerificationRequestRow(id = "v-1", userId = "u-banned", status = "Pending"),
                VerificationRequestRow(id = "v-2", userId = "u-admin", status = "Approved")
            ),
            withdrawals = listOf(
                WithdrawalRequestRow(id = "w-1", userId = "u-admin", amount = 500.0, status = "Pending")
            ),
            refunds = listOf(
                RefundRequestRow(id = "r-1", orderId = "ord-12345", buyerId = "u-banned", reason = "Damaged", status = "Pending")
            ),
            reports = listOf(
                ReportRow(id = "rep-1", reporterId = "u-admin", targetType = "product", targetId = "p-2", reason = "Fake", status = "Open")
            ),
            reviews = listOf(
                Review(id = "rev-1", productId = "p-1", sellerId = "u-admin", reviewerId = "u-banned", rating = 5, comment = "Great")
            ),
            advertisements = listOf(
                ActiveAdvertisementRow(id = "ad-1", productId = "p-1", adType = "Featured", status = "Active", views = 100, clicks = 10)
            ),
            campuses = listOf(CampusRow(name = "KNUST (Kumasi)", abbreviation = "KNUST")),
            usersById = mapOf(adminUser.id to adminUser, bannedUser.id to bannedUser),
            ordersById = mapOf(sampleOrder.id to sampleOrder),
            productsById = mapOf(activeProduct.id to activeProduct, suspendedProduct.id to suspendedProduct)
        )

        val stats = AdminConsoleHelpers.computeOverviewStats(bundle)
        assertEquals(2, stats.totalUsers)
        assertEquals(1, stats.bannedUsers)
        assertEquals(1, stats.verifiedUsers)
        assertEquals(1, stats.activeListings)
        assertEquals(1, stats.suspendedListings)
        assertEquals(8500.0, stats.totalGmv, 0.01)
        assertEquals(425.0, stats.totalPlatformFees, 0.01)
        assertEquals(1, stats.pendingKyc)
        assertEquals(1, stats.pendingWithdrawals)
        assertEquals(1, stats.pendingRefunds)
        assertEquals(1, stats.openReports)
        assertEquals(1, stats.totalReviews)
        assertEquals(1, stats.activeAds)
    }

    @Test
    fun filters_matchByStatusAndCrossLinkedQuery() {
        val usersById = mapOf(adminUser.id to adminUser, bannedUser.id to bannedUser)
        val ordersById = mapOf(sampleOrder.id to sampleOrder)
        val productsById = mapOf(activeProduct.id to activeProduct, suspendedProduct.id to suspendedProduct)

        assertEquals(listOf(bannedUser), AdminConsoleHelpers.filterUsers(listOf(adminUser, bannedUser), "legon", "Banned"))
        assertEquals(listOf(activeProduct), AdminConsoleHelpers.filterProducts(listOf(activeProduct, suspendedProduct), usersById, "kwame", "Active"))
        assertEquals(listOf(sampleOrder), AdminConsoleHelpers.filterOrders(listOf(sampleOrder), usersById, "PAY-999", "Pending"))

        val refund = RefundRequestRow(id = "r-1", orderId = "ord-12345", buyerId = "u-banned", reason = "Wrong item", status = "Pending")
        assertEquals(listOf(refund), AdminConsoleHelpers.filterRefunds(listOf(refund), usersById, ordersById, "MacBook", "Pending"))

        val report = ReportRow(id = "rep-1", reporterId = "u-admin", targetType = "product", targetId = "p-2", reason = "Fake", status = "Open")
        val resolvedReport = ReportRow(id = "rep-2", reporterId = "u-admin", targetType = "user", targetId = "u-banned", reason = "Spam", status = "Resolved")
        assertEquals(listOf(report), AdminConsoleHelpers.filterReports(listOf(report, resolvedReport), usersById, productsById, "Counterfeit", "Product"))
        assertEquals(listOf(report, resolvedReport), AdminConsoleHelpers.filterReports(listOf(report, resolvedReport), usersById, productsById, "", "All"))
        assertEquals(listOf(report), AdminConsoleHelpers.filterReports(listOf(report, resolvedReport), usersById, productsById, "", "Open"))
        assertEquals(listOf(resolvedReport), AdminConsoleHelpers.filterReports(listOf(report, resolvedReport), usersById, productsById, "", "Resolved"))

        val activeAd = ActiveAdvertisementRow(id = "ad-1", productId = "p-1", adType = "Featured", status = "Active")
        val pausedAd = ActiveAdvertisementRow(id = "ad-2", productId = "p-1", adType = "Boost", status = "Paused")
        val endedAd = ActiveAdvertisementRow(id = "ad-3", productId = "p-1", adType = "Campus Ad", status = "Ended")
        val allAds = listOf(activeAd, pausedAd, endedAd)
        assertEquals(listOf(activeAd), AdminConsoleHelpers.filterAds(allAds, productsById, "", "Active"))
        assertEquals(listOf(pausedAd), AdminConsoleHelpers.filterAds(allAds, productsById, "", "Paused"))
        assertEquals(listOf(endedAd), AdminConsoleHelpers.filterAds(allAds, productsById, "", "Ended"))
        assertEquals(allAds, AdminConsoleHelpers.filterAds(allAds, productsById, "", "All"))
    }

    @Test
    fun resolveLiveTarget_updatesStaleDetailTargetFromFreshBundle() {
        val updatedUser = adminUser.copy(banned = true)
        val bundle = AdminDashboardBundle(
            users = listOf(updatedUser),
            products = emptyList(),
            orders = emptyList(),
            verifications = emptyList(),
            withdrawals = emptyList(),
            refunds = emptyList(),
            reports = emptyList(),
            reviews = emptyList(),
            advertisements = emptyList(),
            campuses = emptyList(),
            usersById = mapOf(updatedUser.id to updatedUser)
        )
        val resolved = AdminConsoleHelpers.resolveLiveTarget(
            com.example.ui.screens.AdminDetailTarget.User(adminUser),
            bundle
        ) as com.example.ui.screens.AdminDetailTarget.User
        assertEquals(true, resolved.profile.banned)
    }
}
