package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.Profile
import com.example.domain.model.UserSession
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val session: UserSession = UserSession(),
    val profile: Profile? = null,
    val wishlistIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet(),
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
    val isMaintenanceMode: Boolean = false,
    val buyNowEnabled: Boolean = true,
    val promoDailyPrice: Double = 5.0,
    val promoWeeklyPrice: Double = 25.0,
    val promoFeaturedPrice: Double = 50.0,
    val isBanned: Boolean = false,
    val snackbarMessage: String? = null,
    val authLoading: Boolean = false,
    val authError: String? = null,
    val authInfo: String? = null
) {
    val isLoggedIn: Boolean get() = session.isLoggedIn
    val isAdmin: Boolean get() = profile?.isAdmin == true
}

data class PushAlert(
    val id: Int,
    val title: String,
    val body: String,
    val isMessage: Boolean = false,
    val deepLink: String? = null
)

class SessionViewModel(
    val repository: KartRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _pushAlerts = MutableSharedFlow<PushAlert>(extraBufferCapacity = 16)
    val pushAlerts: SharedFlow<PushAlert> = _pushAlerts.asSharedFlow()

    val themePreference: StateFlow<ThemePreference> = repository.sessionStore.themePreferenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    val languagePreference: StateFlow<String> = repository.sessionStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English (GH)")

    val pushEnabled: StateFlow<Boolean> = repository.sessionStore.pushEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val emailNotifsEnabled: StateFlow<Boolean> = repository.sessionStore.emailNotifsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _campuses = MutableStateFlow(KartGhanaCampuses)
    val campuses: StateFlow<List<String>> = _campuses.asStateFlow()

    private val isAppInForeground = MutableStateFlow(true)
    private var lastForegroundSyncAtMs: Long = 0L
    private var unreadPollingJob: Job? = null
    private var maintenancePollingJob: Job? = null
    private var lastMetadataRefreshUserId: String? = null
    private var lastMetadataRefreshAtMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.sessionStore.sessionFlow.collect { session ->
                _uiState.update { it.copy(session = session) }
                if (session.isLoggedIn) {
                    val now = System.currentTimeMillis()
                    val recentlySynced = lastMetadataRefreshUserId == session.userId &&
                        (now - lastMetadataRefreshAtMs) < 3_000L
                    if (!recentlySynced) {
                        refreshUserMetadata(session.userId)
                    }
                    if (isAppInForeground.value) {
                        startUnreadPolling(session.userId)
                    }
                    if (repository.sessionStore.isPushEnabled()) {
                        syncFcmToken(session.userId)
                    }
                } else {
                    unreadPollingJob?.cancel()
                    unreadPollingJob = null
                    lastMetadataRefreshUserId = null
                    lastMetadataRefreshAtMs = 0L
                    lastForegroundSyncAtMs = 0L
                    _uiState.update {
                        it.copy(
                            profile = null,
                            wishlistIds = emptySet(),
                            blockedUserIds = emptySet(),
                            unreadMessages = 0,
                            unreadNotifications = 0,
                            isBanned = false
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            com.example.notifications.KartPushEventBus.events.collect {
                val uid = _uiState.value.session.userId
                if (uid.isNotBlank() && isAppInForeground.value) {
                    refreshUnreadCounts(uid)
                }
            }
        }
        viewModelScope.launch {
            applyPlatformFlags(repository.fetchPlatformFlags())
        }
        viewModelScope.launch {
            val loadedCampuses = repository.fetchCampuses()
            if (loadedCampuses.isNotEmpty()) {
                _campuses.value = loadedCampuses
            }
        }
    }

    fun setAppInForeground(foreground: Boolean) {
        if (isAppInForeground.value == foreground) return
        isAppInForeground.value = foreground
        if (!foreground) {
            unreadPollingJob?.cancel()
            unreadPollingJob = null
            maintenancePollingJob?.cancel()
            maintenancePollingJob = null
        } else {
            val uid = _uiState.value.session.userId
            if (uid.isNotBlank()) {
                startUnreadPolling(uid)
                val now = System.currentTimeMillis()
                if (now - lastForegroundSyncAtMs >= 30_000L) {
                    refreshUnreadCounts(uid)
                }
            }
            if (_uiState.value.isMaintenanceMode) {
                startMaintenancePollingIfNeeded()
            }
        }
    }

    private fun startMaintenancePollingIfNeeded() {
        if (!_uiState.value.isMaintenanceMode || !isAppInForeground.value) {
            maintenancePollingJob?.cancel()
            maintenancePollingJob = null
            return
        }
        if (maintenancePollingJob?.isActive == true) return
        maintenancePollingJob = viewModelScope.launch {
            while (_uiState.value.isMaintenanceMode && isAppInForeground.value) {
                delay(30_000L)
                if (!isAppInForeground.value) break
                val refreshed = repository.fetchPlatformFlags(forceRefresh = true)
                _uiState.update {
                    it.copy(
                        isMaintenanceMode = refreshed.isMaintenanceMode,
                        buyNowEnabled = refreshed.buyNowEnabled,
                        promoDailyPrice = refreshed.promoDailyPrice,
                        promoWeeklyPrice = refreshed.promoWeeklyPrice,
                        promoFeaturedPrice = refreshed.promoFeaturedPrice
                    )
                }
            }
        }
    }

    private fun applyPlatformFlags(flags: com.example.domain.model.PlatformFlags) {
        _uiState.update {
            it.copy(
                isMaintenanceMode = flags.isMaintenanceMode,
                buyNowEnabled = flags.buyNowEnabled,
                promoDailyPrice = flags.promoDailyPrice,
                promoWeeklyPrice = flags.promoWeeklyPrice,
                promoFeaturedPrice = flags.promoFeaturedPrice
            )
        }
        startMaintenancePollingIfNeeded()
    }

    private fun syncFcmToken(userId: String) {
        if (userId.isBlank()) return
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        viewModelScope.launch {
                            if (repository.sessionStore.isPushEnabled()) {
                                repository.registerFcmToken(userId, token)
                            }
                        }
                    }
                }
        }
    }

    private fun startUnreadPolling(userId: String) {
        if (!isAppInForeground.value || userId.isBlank()) return
        unreadPollingJob?.cancel()
        unreadPollingJob = viewModelScope.launch {
            var pollCycle = 0
            while (isAppInForeground.value) {
                delay(60_000L)
                if (!isAppInForeground.value) break
                pollCycle += 1
                refreshUnreadCounts(userId)
                if (pollCycle % 5 == 0) {
                    val freshProfile = repository.fetchMyProfile(userId)
                    val flags = repository.fetchPlatformFlags(forceRefresh = true)
                    _uiState.update {
                        it.copy(
                            profile = freshProfile ?: it.profile,
                            isBanned = freshProfile?.banned == true || it.isBanned && freshProfile == null
                        )
                    }
                    applyPlatformFlags(flags)
                }
            }
        }
    }

    fun refreshUnreadCounts(userId: String = _uiState.value.session.userId) {
        if (userId.isBlank()) return
        lastForegroundSyncAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            val (unreadNotifs, unreadMsgs) = repository.fetchUnreadSummary(userId)
            _uiState.update {
                it.copy(
                    unreadMessages = unreadMsgs,
                    unreadNotifications = unreadNotifs
                )
            }
        }
    }

    private suspend fun refreshUserMetadataSync(userId: String): Profile? {
        if (userId.isBlank()) return null
        val now = System.currentTimeMillis()
        lastMetadataRefreshUserId = userId
        lastMetadataRefreshAtMs = now
        lastForegroundSyncAtMs = now
        val profile = repository.fetchMyProfile(userId)
        val wishlistRes = repository.fetchWishlist(userId)
        val blockedRes = repository.fetchBlockedUsers()
        val (unreadNotifs, unreadMsgs) = repository.fetchUnreadSummary(userId)
        val flags = repository.fetchPlatformFlags()

        val savedIds = (wishlistRes as? ApiResult.Success)?.data?.map { it.productId }?.toSet()
            ?: _uiState.value.wishlistIds
        val blockedIds = (blockedRes as? ApiResult.Success)?.data?.first
            ?: _uiState.value.blockedUserIds

        _uiState.update {
            it.copy(
                profile = profile ?: it.profile,
                isBanned = profile?.banned == true || (it.isBanned && profile == null),
                wishlistIds = savedIds,
                blockedUserIds = blockedIds,
                unreadMessages = unreadMsgs,
                unreadNotifications = unreadNotifs
            )
        }
        applyPlatformFlags(flags)
        return profile
    }

    fun refreshPlatformFlags() {
        viewModelScope.launch {
            applyPlatformFlags(repository.fetchPlatformFlags())
        }
    }

    fun applyBuyNowEnabled(enabled: Boolean) {
        _uiState.update { it.copy(buyNowEnabled = enabled) }
    }

    fun refreshUserMetadata(userId: String = _uiState.value.session.userId) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            refreshUserMetadataSync(userId)
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.signIn(email, password)) {
                is ApiResult.Success -> {
                    val profile = refreshUserMetadataSync(res.data.userId)
                    if (profile == null) {
                        repository.signOut()
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authError = "Could not verify your account status. Check your connection and try again."
                            )
                        }
                    } else {
                        _uiState.update { it.copy(authLoading = false, authError = null) }
                        if (profile.banned != true) {
                            onSuccess()
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, rawNonce: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.signInWithGoogleIdToken(idToken, rawNonce)) {
                is ApiResult.Success -> {
                    val profile = refreshUserMetadataSync(res.data.userId)
                    if (profile == null) {
                        repository.signOut()
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authError = "Could not verify your account status. Check your connection and try again."
                            )
                        }
                    } else {
                        _uiState.update { it.copy(authLoading = false, authError = null) }
                        if (profile.banned != true) {
                            onSuccess()
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        displayName: String,
        campus: String,
        phone: String,
        onDone: (signedInImmediately: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.signUp(email, password, displayName, campus, phone)) {
                is ApiResult.Success -> {
                    val session = repository.sessionStore.currentSession()
                    val nowLoggedIn = session.isLoggedIn
                    val profile = if (nowLoggedIn) refreshUserMetadataSync(session.userId) else null
                    _uiState.update {
                        it.copy(authLoading = false, authInfo = res.data, authError = null)
                    }
                    if (!nowLoggedIn || profile?.banned != true) {
                        onDone(nowLoggedIn)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun sendPasswordReset(email: String, onCodeSent: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.recoverPassword(email)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(authLoading = false, authInfo = res.data) }
                    onCodeSent()
                }
                is ApiResult.Error -> _uiState.update { it.copy(authLoading = false, authError = res.message) }
            }
        }
    }

    fun verifyPasswordResetCode(
        email: String,
        code: String,
        onVerified: (recoveryAccessToken: String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.verifyPasswordResetOtp(email, code)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = null,
                            authInfo = "Code verified. Choose your new password."
                        )
                    }
                    onVerified(res.data)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun completePasswordReset(
        recoveryAccessToken: String,
        newPassword: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.completePasswordReset(recoveryAccessToken, newPassword)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(authLoading = false, authError = null, authInfo = res.data) }
                    showMessage(res.data)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun savePendingOAuthPkce(verifier: String, state: String) {
        viewModelScope.launch {
            repository.sessionStore.savePendingPkce(verifier, state)
        }
    }

    fun handleDeepLinkAuthCode(code: String, state: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.exchangeMobileAuthCode(code, state)) {
                is ApiResult.Success -> {
                    val profile = refreshUserMetadataSync(res.data.userId)
                    if (profile == null) {
                        repository.signOut()
                        val msg = "Could not verify your account status. Please sign in again."
                        _uiState.update { it.copy(authLoading = false, authError = msg) }
                        showMessage(msg)
                    } else {
                        _uiState.update { it.copy(authLoading = false, authError = null) }
                        if (profile.banned != true) {
                            showMessage("Signed in to Kart!")
                            onSuccess()
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                    showMessage(res.message)
                }
            }
        }
    }

    fun handleDeepLinkRefreshToken(refreshToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.refreshSessionWithToken(refreshToken)) {
                is ApiResult.Success -> {
                    val profile = refreshUserMetadataSync(res.data.userId)
                    if (profile == null) {
                        repository.signOut()
                        val msg = "Could not verify your account status. Please sign in again."
                        _uiState.update { it.copy(authLoading = false, authError = msg) }
                        showMessage(msg)
                    } else {
                        _uiState.update { it.copy(authLoading = false, authError = null) }
                        if (profile.banned != true) {
                            showMessage("Signed in to Kart!")
                            onSuccess()
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                    showMessage(res.message)
                }
            }
        }
    }

    fun blockUser(targetUserId: String, reason: String = "", onDone: () -> Unit = {}) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.blockUser(targetUserId, reason)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(blockedUserIds = it.blockedUserIds + targetUserId) }
                    showMessage("User blocked.")
                    onDone()
                }
                is ApiResult.Error -> showMessage(res.message)
            }
        }
    }

    fun unblockUser(targetUserId: String, onDone: () -> Unit = {}) {
        if (targetUserId.isBlank()) return
        viewModelScope.launch {
            when (val res = repository.unblockUser(targetUserId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(blockedUserIds = it.blockedUserIds - targetUserId) }
                    showMessage("User unblocked.")
                    onDone()
                }
                is ApiResult.Error -> showMessage(res.message)
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit = {}) {
        viewModelScope.launch {
            repository.signOut()
            showMessage("Signed out of Kart")
            onSignedOut()
        }
    }

    fun toggleWishlist(productId: String, onRequireAuth: () -> Unit) {
        if (!_uiState.value.isLoggedIn) {
            onRequireAuth()
            return
        }
        val wasSaved = productId in _uiState.value.wishlistIds
        // Optimistic update
        _uiState.update {
            it.copy(wishlistIds = if (wasSaved) it.wishlistIds - productId else it.wishlistIds + productId)
        }
        viewModelScope.launch {
            when (val res = repository.toggleWishlist(productId, wasSaved)) {
                is ApiResult.Success -> {
                    showMessage(if (wasSaved) "Removed from wishlist" else "Saved to wishlist")
                }
                is ApiResult.Error -> {
                    // Rollback on failure
                    _uiState.update {
                        it.copy(wishlistIds = if (wasSaved) it.wishlistIds + productId else it.wishlistIds - productId)
                    }
                    showMessage(res.message)
                }
            }
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { repository.sessionStore.setThemePreference(pref) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { repository.sessionStore.setLanguage(lang) }
    }

    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.sessionStore.setPushEnabled(enabled)
            val uid = _uiState.value.session.userId
            if (uid.isNotBlank()) {
                repository.syncNotificationPreferences(enabled, emailNotifsEnabled.value)
                if (!enabled) {
                    repository.unregisterFcmToken()
                } else {
                    syncFcmToken(uid)
                }
            }
        }
    }

    fun setEmailNotifsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.sessionStore.setEmailNotifsEnabled(enabled)
            val uid = _uiState.value.session.userId
            if (uid.isNotBlank()) {
                repository.syncNotificationPreferences(pushEnabled.value, enabled)
            }
        }
    }

    fun setNotificationPrefs(push: Boolean, email: Boolean) {
        viewModelScope.launch {
            repository.sessionStore.setNotificationPrefs(push, email)
            val uid = _uiState.value.session.userId
            if (uid.isNotBlank()) {
                repository.syncNotificationPreferences(push, email)
                if (!push) {
                    repository.unregisterFcmToken()
                } else {
                    syncFcmToken(uid)
                }
            }
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearAuthFeedback() {
        _uiState.update { it.copy(authError = null, authInfo = null) }
    }
}

class KartViewModelFactory(private val repository: KartRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SessionViewModel::class.java) -> SessionViewModel(repository) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository) as T
            modelClass.isAssignableFrom(MarketplaceViewModel::class.java) -> MarketplaceViewModel(repository) as T
            modelClass.isAssignableFrom(ProductDetailViewModel::class.java) -> ProductDetailViewModel(repository) as T
            modelClass.isAssignableFrom(OrdersViewModel::class.java) -> OrdersViewModel(repository) as T
            modelClass.isAssignableFrom(MessagesViewModel::class.java) -> MessagesViewModel(repository) as T
            modelClass.isAssignableFrom(WalletViewModel::class.java) -> WalletViewModel(repository) as T
            modelClass.isAssignableFrom(SellerViewModel::class.java) -> SellerViewModel(repository) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(repository) as T
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> AdminViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
