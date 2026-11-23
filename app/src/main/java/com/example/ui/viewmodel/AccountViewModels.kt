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
    val actionLoading: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class ProfileViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(targetUserId: String, currentUserId: String?) {
        val isDifferentUser = _uiState.value.bundle?.profile?.id != targetUserId
        _uiState.update {
            it.copy(
                loading = true,
                bundle = if (isDifferentUser) null else it.bundle,
                error = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.fetchProfileBundle(targetUserId, currentUserId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, bundle = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadFollowList(userId: String, type: String) {
        viewModelScope.launch {
            val users = repository.fetchFollowProfiles(userId, type)
            _uiState.update { it.copy(followList = users) }
        }
    }

    fun toggleFollow(targetUserId: String, currentUserId: String? = null) {
        if (!currentUserId.isNullOrBlank() && targetUserId == currentUserId) {
            _uiState.update { it.copy(feedback = "You cannot follow your own profile.") }
            return
        }
        val current = _uiState.value.bundle ?: return
        val next = !current.isFollowing
        val nextCount = (current.followersCount + if (next) 1 else -1).coerceAtLeast(0)
        _uiState.update { it.copy(bundle = current.copy(isFollowing = next, followersCount = nextCount)) }
        viewModelScope.launch {
            when (val res = repository.toggleFollow(targetUserId)) {
                is ApiResult.Error -> _uiState.update { it.copy(bundle = current, feedback = res.message) }
                is ApiResult.Success -> Unit
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
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { if (it.id == id) it.copy(isRead = true) else it })
        }
        viewModelScope.launch { repository.markNotificationRead(id) }
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
    val error: String? = null,
    val feedback: String? = null
)

class AdminViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchAdminBundle()) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, bundle = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun toggleBan(userId: String, ban: Boolean) {
        runAdminCall("User ${if (ban) "suspended" else "reinstated"}") { repository.adminBanUser(userId, ban) }
    }

    fun updateProductStatus(productId: String, status: String) {
        runAdminCall("Listing updated to $status") { repository.adminUpdateProductStatus(productId, status) }
    }

    fun resolveVerification(requestId: String, approve: Boolean) {
        runAdminCall("Verification ${if (approve) "approved" else "rejected"}") {
            repository.adminResolveVerification(requestId, approve)
        }
    }

    fun resolveWithdrawal(requestId: String, approve: Boolean) {
        runAdminCall("Withdrawal ${if (approve) "approved" else "rejected"}") {
            repository.adminResolveWithdrawal(requestId, approve)
        }
    }

    fun resolveRefund(orderId: String, requestId: String, approve: Boolean) {
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
                    loadDashboard()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
                }
            }
        }
    }

    private fun runAdminCall(successMsg: String, block: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = block()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = successMsg) }
                    loadDashboard()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}
