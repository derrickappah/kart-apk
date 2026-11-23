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
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
    val isMaintenanceMode: Boolean = false,
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

    private val seenNotificationIds = mutableSetOf<String>()
    private var initializedNotificationBaseline = false
    private var lastKnownUnreadMessages: Int? = null

    val themePreference: StateFlow<ThemePreference> = repository.sessionStore.themePreferenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    val languagePreference: StateFlow<String> = repository.sessionStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English (GH)")

    val pushEnabled: StateFlow<Boolean> = repository.sessionStore.pushEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val emailNotifsEnabled: StateFlow<Boolean> = repository.sessionStore.emailNotifsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _campuses = MutableStateFlow(KartGhanaCampuses)
    val campuses: StateFlow<List<String>> = _campuses.asStateFlow()

    private var unreadPollingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.sessionStore.sessionFlow.collect { session ->
                _uiState.update { it.copy(session = session) }
                if (session.isLoggedIn) {
                    refreshUserMetadata(session.userId)
                    startUnreadPolling(session.userId)
                    if (repository.sessionStore.isPushEnabled()) {
                        syncFcmToken(session.userId)
                    }
                } else {
                    unreadPollingJob?.cancel()
                    unreadPollingJob = null
                    seenNotificationIds.clear()
                    initializedNotificationBaseline = false
                    lastKnownUnreadMessages = null
                    _uiState.update {
                        it.copy(
                            profile = null,
                            wishlistIds = emptySet(),
                            unreadMessages = 0,
                            unreadNotifications = 0,
                            isBanned = false
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            val maint = repository.isMaintenanceMode()
            _uiState.update { it.copy(isMaintenanceMode = maint) }
        }
        viewModelScope.launch {
            val loadedCampuses = repository.fetchCampuses()
            if (loadedCampuses.isNotEmpty()) {
                _campuses.value = loadedCampuses
            }
        }
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
        unreadPollingJob?.cancel()
        unreadPollingJob = viewModelScope.launch {
            var pollCycle = 0
            while (true) {
                delay(15_000L)
                pollCycle += 1
                refreshUnreadCounts(userId)
                if (pollCycle % 2 == 0) {
                    val freshProfile = repository.fetchMyProfile(userId)
                    val maint = repository.isMaintenanceMode()
                    _uiState.update {
                        it.copy(
                            profile = freshProfile ?: it.profile,
                            isBanned = freshProfile?.banned == true || it.isBanned && freshProfile == null,
                            isMaintenanceMode = maint
                        )
                    }
                }
            }
        }
    }

    fun refreshUnreadCounts(userId: String = _uiState.value.session.userId) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val unreadNotifs = repository.fetchUnreadNotificationsCount(userId)
            val unreadMsgs = repository.fetchUnreadMessagesCount()

            if (pushEnabled.value) {
                val prevMsgs = lastKnownUnreadMessages
                if (prevMsgs != null && unreadMsgs > prevMsgs) {
                    val delta = unreadMsgs - prevMsgs
                    _pushAlerts.tryEmit(
                        PushAlert(
                            id = 1001,
                            title = "New message on Kart",
                            body = if (delta == 1) "You have a new campus chat message." else "You have $delta new campus chat messages.",
                            isMessage = true,
                            deepLink = "/messages"
                        )
                    )
                }

                val notifRes = repository.fetchNotifications(userId)
                if (notifRes is ApiResult.Success) {
                    val unreadList = notifRes.data.filter { it.isRead != true }
                    if (!initializedNotificationBaseline) {
                        seenNotificationIds.addAll(unreadList.map { it.id })
                        initializedNotificationBaseline = true
                    } else {
                        unreadList.filter { it.id !in seenNotificationIds }.forEach { notif ->
                            seenNotificationIds.add(notif.id)
                            _pushAlerts.tryEmit(
                                PushAlert(
                                    id = notif.id.hashCode(),
                                    title = notif.title ?: "Kart Update",
                                    body = notif.message ?: "You have a new update on Kart.",
                                    isMessage = notif.type?.contains("message", ignoreCase = true) == true,
                                    deepLink = notif.link ?: "/notifications"
                                )
                            )
                        }
                    }
                }
            }
            lastKnownUnreadMessages = unreadMsgs

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
        val profile = repository.fetchMyProfile(userId)
        val wishlistRes = repository.fetchWishlist(userId)
        val unreadNotifs = repository.fetchUnreadNotificationsCount(userId)
        val unreadMsgs = repository.fetchUnreadMessagesCount()
        val maint = repository.isMaintenanceMode()

        val savedIds = (wishlistRes as? ApiResult.Success)?.data?.map { it.productId }?.toSet()
            ?: _uiState.value.wishlistIds

        _uiState.update {
            it.copy(
                profile = profile ?: it.profile,
                isBanned = profile?.banned == true,
                isMaintenanceMode = maint,
                wishlistIds = savedIds,
                unreadMessages = unreadMsgs,
                unreadNotifications = unreadNotifs
            )
        }
        return profile
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
                    _uiState.update { it.copy(authLoading = false, authError = null) }
                    if (profile?.banned != true) {
                        onSuccess()
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
                    _uiState.update { it.copy(authLoading = false, authError = null) }
                    if (profile?.banned != true) {
                        onSuccess()
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
                    val nowLoggedIn = repository.sessionStore.currentSession().isLoggedIn
                    _uiState.update {
                        it.copy(authLoading = false, authInfo = res.data, authError = null)
                    }
                    onDone(nowLoggedIn)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.recoverPassword(email)) {
                is ApiResult.Success -> _uiState.update { it.copy(authLoading = false, authInfo = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(authLoading = false, authError = res.message) }
            }
        }
    }

    fun handleDeepLinkRefreshToken(refreshToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null, authInfo = null) }
            when (val res = repository.refreshSessionWithToken(refreshToken)) {
                is ApiResult.Success -> {
                    val profile = refreshUserMetadataSync(res.data.userId)
                    _uiState.update { it.copy(authLoading = false, authError = null) }
                    if (profile?.banned != true) {
                        showMessage("Signed in to Kart!")
                        onSuccess()
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                    showMessage(res.message)
                }
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
