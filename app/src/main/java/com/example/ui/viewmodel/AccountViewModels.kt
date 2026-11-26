package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AdminDashboardBundle
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.data.repository.ProfileBundle
import com.example.domain.model.NotificationItem
import com.example.domain.model.Profile
import com.example.domain.model.WishlistRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val bundle: ProfileBundle? = null,
    val wishlist: List<WishlistRow> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val followList: List<Profile> = emptyList(),
    val followListLoading: Boolean = false,
    val actionLoading: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class ProfileViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loadProfileJob: kotlinx.coroutines.Job? = null
    private var followInFlight = false

    fun loadProfile(targetUserId: String, currentUserId: String?) {
        val isDifferentUser = _uiState.value.bundle?.profile?.id != targetUserId
        loadProfileJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                bundle = if (isDifferentUser) null else it.bundle,
                error = null
            )
        }
        loadProfileJob = viewModelScope.launch {
            when (val res = repository.fetchProfileBundle(targetUserId, currentUserId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, bundle = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadFollowList(userId: String, type: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(followList = emptyList(), followListLoading = true) }
            val users = repository.fetchFollowProfiles(userId, type)
            _uiState.update { it.copy(followList = users, followListLoading = false) }
        }
    }

    fun toggleFollow(targetUserId: String, currentUserId: String? = null) {
        if (!currentUserId.isNullOrBlank() && targetUserId == currentUserId) {
            _uiState.update { it.copy(feedback = "You cannot follow your own profile.") }
            return
        }
        if (followInFlight) return
        val current = _uiState.value.bundle ?: return
        followInFlight = true
        val next = !current.isFollowing
        val nextCount = (current.followersCount + if (next) 1 else -1).coerceAtLeast(0)
        _uiState.update { it.copy(bundle = current.copy(isFollowing = next, followersCount = nextCount)) }
        viewModelScope.launch {
            try {
                when (val res = repository.toggleFollow(targetUserId)) {
                    is ApiResult.Error -> _uiState.update { state ->
                        if (state.bundle?.profile?.id == current.profile.id) {
                            state.copy(bundle = current, feedback = res.message)
                        } else {
                            state.copy(feedback = res.message)
                        }
                    }
                    is ApiResult.Success -> {
                        val serverFollowing = res.data
                        if (serverFollowing != next) {
                            val correctedCount = (current.followersCount +
                                if (serverFollowing == current.isFollowing) 0
                                else if (serverFollowing) 1 else -1).coerceAtLeast(0)
                            _uiState.update { state ->
                                if (state.bundle?.profile?.id == current.profile.id) {
                                    state.copy(
                                        bundle = state.bundle.copy(
                                            isFollowing = serverFollowing,
                                            followersCount = correctedCount
                                        )
                                    )
                                } else state
                            }
                        }
                    }
                }
            } finally {
                followInFlight = false
            }
        }
    }

    fun saveProfile(
        userId: String,
        displayName: String,
        username: String,
        bio: String,
        campus: String,
        phone: String,
        instagram: String,
        snapchat: String,
        whatsapp: String,
        avatarUrl: String?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            val fields = buildMap<String, Any?> {
                put("display_name", displayName.trim())
                if (username.isNotBlank()) put("username", username.trim())
                put("bio", bio.trim())
                put("campus", campus.trim())
                put("phone", phone.trim().ifBlank { null })
                put("whatsapp_number", whatsapp.trim().ifBlank { null })
                put("instagram", instagram.trim().ifBlank { null })
                put("snapchat", snapchat.trim().ifBlank { null })
                if (!avatarUrl.isNullOrBlank()) put("avatar_url", avatarUrl)
            }
            when (val res = repository.updateProfile(userId, fields)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Profile updated!") }
                    onSaved()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun uploadAvatar(resolver: ContentResolver, uri: Uri, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.uploadImageUri(resolver, uri, bucket = "profiles")) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false) }
                    onUrl(res.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun loadWishlist(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchWishlist(userId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, wishlist = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadNotifications(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchNotifications(userId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, notifications = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun markNotificationRead(id: String) {
        val prev = _uiState.value.notifications
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { if (it.id == id) it.copy(isRead = true) else it })
        }
        viewModelScope.launch {
            if (repository.markNotificationRead(id) is ApiResult.Error) {
                _uiState.update { it.copy(notifications = prev) }
            }
        }
    }

    fun markAllNotificationsRead(userId: String, onDone: () -> Unit = {}) {
        if (userId.isBlank()) return
        val prev = _uiState.value.notifications
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
        viewModelScope.launch {
            when (val res = repository.markAllNotificationsRead(userId)) {
                is ApiResult.Success -> onDone()
                is ApiResult.Error -> _uiState.update {
                    it.copy(notifications = prev, feedback = res.message)
                }
            }
        }
    }

    fun deleteNotification(id: String) {
        val prev = _uiState.value.notifications
        _uiState.update { it.copy(notifications = prev.filterNot { n -> n.id == id }) }
        viewModelScope.launch {
            if (repository.deleteNotification(id) is ApiResult.Error) {
                _uiState.update { it.copy(notifications = prev, feedback = "Could not delete notification.") }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}

data class AdminUiState(
    val loading: Boolean = true,
    val bundle: AdminDashboardBundle? = null,
    val actionLoading: Boolean = false,
    val isLiveConnected: Boolean = false,
    val accessToken: String = "",
    val error: String? = null,
    val feedback: String? = null
)

class AdminViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val realtimeClient = com.example.data.remote.SupabaseAdminRealtimeClient()
    private var realtimeJob: kotlinx.coroutines.Job? = null
    private var connectionJob: kotlinx.coroutines.Job? = null
    private var pendingRefreshJob: kotlinx.coroutines.Job? = null

    fun startLiveUpdates() {
        pendingRefreshJob?.cancel()
        connectionJob?.cancel()
        realtimeJob?.cancel()
        connectionJob = viewModelScope.launch {
            val token = repository.sessionStore.currentSession().accessToken
            if (token.isBlank()) return@launch
            realtimeClient.connect(token) {
                repository.sessionStore.currentSession().accessToken
            }
            realtimeJob = launch {
                realtimeClient.tableEvents.collect {
                    pendingRefreshJob?.cancel()
                    pendingRefreshJob = launch {
                        kotlinx.coroutines.delay(1_000L)
                        loadDashboard(forceRefresh = true, silent = true)
                    }
                }
            }
            realtimeClient.connected.collect { connected ->
                _uiState.update { it.copy(isLiveConnected = connected) }
            }
        }
    }

    fun stopLiveUpdates() {
        pendingRefreshJob?.cancel()
        realtimeJob?.cancel()
        connectionJob?.cancel()
        realtimeClient.disconnect()
        _uiState.update { it.copy(isLiveConnected = false) }
    }

    override fun onCleared() {
        stopLiveUpdates()
        super.onCleared()
    }

    fun loadDashboard(forceRefresh: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            val token = repository.sessionStore.currentSession().accessToken
            val cached = repository.getCachedAdminBundle()
            if (cached != null && _uiState.value.bundle == null) {
                _uiState.update { it.copy(loading = false, bundle = cached, accessToken = token) }
            }
            if (!forceRefresh && repository.isAdminCacheFresh()) {
                _uiState.update { it.copy(loading = false, bundle = cached, accessToken = token) }
                return@launch
            }
            val showSpinner = !silent && cached == null && _uiState.value.bundle == null
            if (showSpinner || (forceRefresh && !silent)) {
                _uiState.update { it.copy(loading = true, error = null, accessToken = token) }
            }
            when (val res = repository.fetchAdminBundle()) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, bundle = res.data, accessToken = token) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = if (silent || cached != null) it.error else res.message) }
            }
        }
    }

    fun toggleBan(userId: String, ban: Boolean) {
        runAdminCall("User ${if (ban) "suspended" else "reinstated"}") { repository.adminBanUser(userId, ban) }
    }

    fun updateProductStatus(productId: String, status: String) {
        runAdminCall("Listing updated to $status") { repository.adminUpdateProductStatus(productId, status) }
    }

    fun deleteProduct(productId: String) {
        runAdminCall("Listing deleted") { repository.adminDeleteProduct(productId) }
    }

    fun resolveVerification(requestId: String, approve: Boolean, adminNotes: String? = null) {
        runAdminCall("Verification ${if (approve) "approved" else "rejected"}") {
            repository.adminResolveVerification(requestId, approve, adminNotes)
        }
    }

    fun resolveWithdrawal(requestId: String, approve: Boolean) {
        runAdminCall("Withdrawal ${if (approve) "approved" else "rejected"}") {
            repository.adminResolveWithdrawal(requestId, approve)
        }
    }

    fun resolveDeletionRequest(requestId: String, userId: String?, approve: Boolean) {
        runAdminCall(
            if (approve) "Account banned, listings suspended, and deletion approved"
            else "Deletion request rejected"
        ) {
            repository.adminResolveDeletionRequest(requestId, userId, approve)
        }
    }

    fun deleteReview(reviewId: String) {
        runAdminCall("Review deleted") { repository.adminDeleteReview(reviewId) }
    }

    fun updateReportStatus(reportId: String, status: String) {
        runAdminCall("Report marked $status") { repository.adminUpdateReportStatus(reportId, status) }
    }

    fun updateAdStatus(adId: String, status: String) {
        runAdminCall("Ad updated to $status") { repository.adminUpdateAdStatus(adId, status) }
    }

    fun deleteAd(adId: String) {
        runAdminCall("Ad deleted") { repository.adminDeleteAd(adId) }
    }

    fun addCampus(name: String, abbreviation: String?) {
        runAdminCall("Campus added") { repository.adminAddCampus(name, abbreviation) }
    }

    fun updateCampus(id: String, name: String, abbreviation: String?) {
        runAdminCall("Campus updated") { repository.adminUpdateCampus(id, name, abbreviation) }
    }

    fun deleteCampus(id: String) {
        runAdminCall("Campus deleted") { repository.adminDeleteCampus(id) }
    }

    fun resolveRefund(orderId: String, requestId: String, approve: Boolean) {
        if (approve && orderId.isBlank()) {
            _uiState.update { it.copy(feedback = "Cannot approve refund: Order ID is missing.") }
            return
        }
        runAdminCall("Refund ${if (approve) "processed" else "rejected"}") {
            repository.adminResolveRefund(orderId, requestId, approve)
        }
    }

    fun setBuyNowEnabled(enabled: Boolean, onUpdated: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.adminSetPlatformSetting(KartRepository.KEY_BUY_NOW_ENABLED, enabled)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            actionLoading = false,
                            bundle = state.bundle?.copy(buyNowEnabled = enabled),
                            feedback = if (enabled) {
                                "Buy Now turned on for product pages"
                            } else {
                                "Buy Now turned off on product pages"
                            }
                        )
                    }
                    onUpdated(enabled)
                    loadDashboard(forceRefresh = true, silent = true)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
                }
            }
        }
    }

    fun setMaintenanceMode(enabled: Boolean, onUpdated: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.adminSetPlatformSetting(KartRepository.KEY_MAINTENANCE_MODE, enabled)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            actionLoading = false,
                            bundle = state.bundle?.copy(isMaintenanceMode = enabled),
                            feedback = if (enabled) {
                                "Maintenance mode enabled for non-admin users"
                            } else {
                                "Maintenance mode disabled"
                            }
                        )
                    }
                    onUpdated(enabled)
                    loadDashboard(forceRefresh = true, silent = true)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
                }
            }
        }
    }

    fun updatePromotionRates(boostRate: Double, featuredRate: Double, campusAdRate: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.adminUpdatePromotionRates(boostRate, featuredRate, campusAdRate)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            actionLoading = false,
                            bundle = state.bundle?.copy(
                                promoBoostRate = boostRate,
                                promoFeaturedRate = featuredRate,
                                promoCampusAdRate = campusAdRate
                            ),
                            feedback = "Promotion rates updated"
                        )
                    }
                    loadDashboard(forceRefresh = true, silent = true)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun runAdminCall(successMsg: String, block: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = block()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = successMsg) }
                    loadDashboard(forceRefresh = true, silent = true)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}
