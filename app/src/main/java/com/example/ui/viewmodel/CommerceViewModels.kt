package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.data.repository.SellerHubBundle
import com.example.domain.model.EnrichedConversation
import com.example.domain.model.KartErrorFormatter
import com.example.domain.model.MessageRow
import com.example.domain.model.Order
import com.example.domain.model.Product
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.UserSubscription
import com.example.domain.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OrdersUiState(
    val loading: Boolean = true,
    val orders: List<Order> = emptyList(),
    val selectedOrder: Order? = null,
    val hasPendingRefund: Boolean = false,
    val hasApprovedRefund: Boolean = false,
    val hasReviewedOrder: Boolean = false,
    val deliveryQr: com.example.data.repository.DeliveryQrData? = null,
    val qrLoading: Boolean = false,
    val qrError: String? = null,
    val otpSending: Boolean = false,
    val otpSent: Boolean = false,
    val deliveryActionError: String? = null,
    val deliveryActionNotice: String? = null,
    val actionLoading: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class OrdersViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private var lastOrdersAsSeller: Boolean? = null
    private var loadOrdersJob: kotlinx.coroutines.Job? = null
    private var loadOrderDetailJob: kotlinx.coroutines.Job? = null

    fun loadOrders(userId: String, asSeller: Boolean = false) {
        val modeChanged = lastOrdersAsSeller != asSeller
        lastOrdersAsSeller = asSeller
        loadOrdersJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                orders = if (modeChanged) emptyList() else it.orders,
                error = null
            )
        }
        loadOrdersJob = viewModelScope.launch {
            when (val res = repository.fetchOrders(userId, asSeller)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, orders = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadOrderDetail(orderId: String) {
        val isDifferentOrder = _uiState.value.selectedOrder?.id != orderId
        loadOrderDetailJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                selectedOrder = if (isDifferentOrder) null else it.selectedOrder,
                hasPendingRefund = if (isDifferentOrder) false else it.hasPendingRefund,
                hasApprovedRefund = if (isDifferentOrder) false else it.hasApprovedRefund,
                hasReviewedOrder = if (isDifferentOrder) false else it.hasReviewedOrder,
                error = null
            )
        }
        loadOrderDetailJob = viewModelScope.launch {
            when (val res = repository.fetchOrderDetail(orderId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loading = false,
                        selectedOrder = res.data.order,
                        hasPendingRefund = res.data.hasPendingRefund,
                        hasApprovedRefund = res.data.hasApprovedRefund,
                        hasReviewedOrder = res.data.hasReviewed
                    )
                }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun markShipped(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.markOrderShipped(orderId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Order marked as shipped!") }
                    loadOrderDetail(orderId)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun sendDeliveryOtp(orderId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    otpSending = true,
                    deliveryActionError = null,
                    deliveryActionNotice = null
                )
            }
            when (val res = repository.sendDeliveryOtp(orderId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        otpSending = false,
                        otpSent = true,
                        deliveryActionError = null,
                        deliveryActionNotice = "6-digit delivery OTP sent to your email. Check your inbox or spam folder."
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        otpSending = false,
                        deliveryActionError = res.message,
                        deliveryActionNotice = null
                    )
                }
            }
        }
    }

    fun confirmDelivery(orderId: String, code: String, mode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    actionLoading = true,
                    deliveryActionError = null,
                    deliveryActionNotice = null
                )
            }
            when (val res = repository.confirmDelivery(orderId, code, mode)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            actionLoading = false,
                            otpSending = false,
                            otpSent = false,
                            deliveryActionError = null,
                            deliveryActionNotice = null,
                            feedback = "Delivery confirmed! Escrow released."
                        )
                    }
                    loadOrderDetail(orderId)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        actionLoading = false,
                        deliveryActionError = res.message,
                        deliveryActionNotice = null,
                        feedback = if (mode == "qr") res.message else it.feedback
                    )
                }
            }
        }
    }

    fun clearDeliveryMessages() {
        _uiState.update { it.copy(deliveryActionError = null, deliveryActionNotice = null) }
    }

    fun clearDeliveryModalState() {
        _uiState.update {
            it.copy(
                otpSending = false,
                otpSent = false,
                deliveryActionError = null,
                deliveryActionNotice = null
            )
        }
    }

    fun generateDeliveryQr(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(qrLoading = true, qrError = null) }
            when (val res = repository.generateDeliveryQr(orderId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(qrLoading = false, deliveryQr = res.data, qrError = null)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(qrLoading = false, qrError = res.message)
                }
            }
        }
    }

    fun clearDeliveryQr() {
        _uiState.update { it.copy(deliveryQr = null, qrLoading = false, qrError = null) }
    }

    fun cancelOrder(orderId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.cancelOrder(orderId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Order cancelled.") }
                    loadOrderDetail(orderId)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun requestRefund(orderId: String, reason: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.requestRefund(orderId, reason)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Refund request submitted.") }
                    loadOrderDetail(orderId)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun submitOrderReview(order: Order, rating: Int, comment: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.submitReview(order.productId, order.sellerId, order.id, rating, comment)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            actionLoading = false,
                            hasReviewedOrder = true,
                            feedback = "Thanks for reviewing your order!"
                        )
                    }
                    loadOrderDetail(order.id)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}

data class MessagesUiState(
    val loading: Boolean = true,
    val conversations: List<EnrichedConversation> = emptyList(),
    val activeMessages: List<MessageRow> = emptyList(),
    val onlineUserIds: Set<String> = emptySet(),
    val typingUsersByConversation: Map<String, Set<String>> = emptyMap(),
    val pendingProductAttachment: Pair<String, Product>? = null,
    val sending: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class MessagesViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var cachedUserId: String? = null
    private val messagesCache = mutableMapOf<String, List<MessageRow>>()
    private var loadThreadJob: Job? = null
    private val realtimeClient = com.example.data.remote.SupabaseRealtimeClient()
    val isRealtimeConnected: StateFlow<Boolean> = realtimeClient.isConnected
    private var realtimeEventsJob: Job? = null
    private var presenceJob: Job? = null
    private var typingEventsJob: Job? = null
    private var typingStopDebounceJob: Job? = null
    private var stopRealtimeGraceJob: Job? = null
    private val remoteTypingExpiryJobs = mutableMapOf<Pair<String, String>, Job>()
    private var lastTypingSentAtMs = 0L
    private var isLocallyTyping = false
    private var realtimeConnectedUserId: String? = null
    private var activeRealtimeConsumers = 0
    private var realtimeUserId: String? = null
    private var isAppInForeground = true
    private var joinedConversationId: String? = null

    init {
        viewModelScope.launch {
            com.example.notifications.KartPushEventBus.events.collect { eventType ->
                if (eventType == "message" && activeRealtimeConsumers > 0 && isAppInForeground) {
                    activeThreadId?.let { loadThread(it, silent = true) }
                    (cachedUserId ?: realtimeUserId)?.takeIf { it.isNotBlank() }?.let { uid ->
                        loadConversations(uid)
                    }
                }
            }
        }
    }

    fun acquireRealtime(userId: String) {
        if (userId.isBlank()) return
        stopRealtimeGraceJob?.cancel()
        stopRealtimeGraceJob = null
        activeRealtimeConsumers += 1
        realtimeUserId = userId
        if (isAppInForeground) {
            startRealtime(userId)
        }
    }

    fun releaseRealtime() {
        activeRealtimeConsumers = (activeRealtimeConsumers - 1).coerceAtLeast(0)
        if (activeRealtimeConsumers == 0) {
            stopRealtimeGraceJob?.cancel()
            stopRealtimeGraceJob = viewModelScope.launch {
                delay(3_000L)
                if (activeRealtimeConsumers == 0 || !isAppInForeground) {
                    stopRealtimeInternal(clearJoinedChannel = false)
                }
            }
        }
    }

    fun setAppInForeground(foreground: Boolean) {
        if (isAppInForeground == foreground) return
        isAppInForeground = foreground
        if (foreground) {
            stopRealtimeGraceJob?.cancel()
            stopRealtimeGraceJob = null
            val uid = realtimeUserId
            if (activeRealtimeConsumers > 0 && !uid.isNullOrBlank()) {
                startRealtime(uid)
                activeThreadId?.let { loadThread(it, silent = true) }
            }
        } else {
            stopRealtimeGraceJob?.cancel()
            stopRealtimeGraceJob = null
            stopRealtimeInternal(clearJoinedChannel = false)
        }
    }

    fun startRealtime(userId: String) {
        if (userId.isBlank() || realtimeConnectedUserId == userId) return
        realtimeConnectedUserId = userId
        realtimeEventsJob?.cancel()
        presenceJob?.cancel()
        typingEventsJob?.cancel()
        realtimeEventsJob = viewModelScope.launch {
            val token = repository.sessionStore.currentSession().accessToken
            if (token.isBlank()) {
                realtimeConnectedUserId = null
                return@launch
            }
            realtimeClient.connect(token, userId = userId) {
                repository.sessionStore.currentSession().accessToken
            }
            joinedConversationId?.takeIf { it.isNotBlank() }?.let { cid ->
                realtimeClient.joinConversationChannel(cid)
            }
            presenceJob = launch {
                realtimeClient.onlineUserIds.collect { ids ->
                    _uiState.update { it.copy(onlineUserIds = ids) }
                }
            }
            typingEventsJob = launch {
                realtimeClient.typingEvents.collect { event ->
                    if (event.userId == cachedUserId || event.userId == realtimeConnectedUserId) return@collect
                    val key = event.conversationId to event.userId
                    remoteTypingExpiryJobs.remove(key)?.cancel()
                    updateTypingUser(event.conversationId, event.userId, event.isTyping)
                    if (event.isTyping) {
                        remoteTypingExpiryJobs[key] = launch {
                            delay(5_000L)
                            remoteTypingExpiryJobs.remove(key)
                            updateTypingUser(event.conversationId, event.userId, false)
                        }
                    }
                }
            }
            realtimeClient.messageEvents.collect { convId ->
                clearConversationTyping(convId)
                if (activeThreadId == convId) {
                    loadThread(convId, silent = true)
                }
                cachedUserId?.takeIf { it.isNotBlank() }?.let { uid ->
                    loadConversations(uid)
                }
            }
        }
    }

    private fun updateTypingUser(conversationId: String, userId: String, isTyping: Boolean) {
        _uiState.update { state ->
            val currentSet = state.typingUsersByConversation[conversationId].orEmpty()
            val nextSet = if (isTyping) currentSet + userId else currentSet - userId
            val nextMap = if (nextSet.isEmpty()) {
                state.typingUsersByConversation - conversationId
            } else {
                state.typingUsersByConversation + (conversationId to nextSet)
            }
            state.copy(typingUsersByConversation = nextMap)
        }
    }

    private fun clearConversationTyping(conversationId: String) {
        val keysToCancel = remoteTypingExpiryJobs.keys.filter { it.first == conversationId }
        keysToCancel.forEach { remoteTypingExpiryJobs.remove(it)?.cancel() }
        _uiState.update { state ->
            if (conversationId !in state.typingUsersByConversation) state
            else state.copy(typingUsersByConversation = state.typingUsersByConversation - conversationId)
        }
    }

    fun enterConversation(conversationId: String) {
        joinedConversationId = conversationId.takeIf { it.isNotBlank() }
        realtimeClient.joinConversationChannel(conversationId)
    }

    fun leaveConversation(conversationId: String, currentUserId: String) {
        stopTyping(conversationId, currentUserId)
        if (joinedConversationId == conversationId) {
            joinedConversationId = null
        }
        realtimeClient.leaveConversationChannel(conversationId)
    }

    fun onTypingInputChanged(conversationId: String, currentUserId: String, text: String) {
        if (conversationId.isBlank() || currentUserId.isBlank()) return
        if (text.isBlank()) {
            stopTyping(conversationId, currentUserId)
            return
        }
        val now = System.currentTimeMillis()
        if (!isLocallyTyping || (now - lastTypingSentAtMs) >= 2_000L) {
            isLocallyTyping = true
            lastTypingSentAtMs = now
            realtimeClient.sendTyping(conversationId, currentUserId, true)
        }
        typingStopDebounceJob?.cancel()
        typingStopDebounceJob = viewModelScope.launch {
            delay(3_000L)
            stopTyping(conversationId, currentUserId)
        }
    }

    fun stopTyping(conversationId: String, currentUserId: String) {
        typingStopDebounceJob?.cancel()
        typingStopDebounceJob = null
        if (isLocallyTyping) {
            isLocallyTyping = false
            lastTypingSentAtMs = 0L
            if (conversationId.isNotBlank() && currentUserId.isNotBlank()) {
                realtimeClient.sendTyping(conversationId, currentUserId, false)
            }
        }
    }

    private fun stopRealtimeInternal(clearJoinedChannel: Boolean) {
        realtimeConnectedUserId = null
        if (clearJoinedChannel) {
            joinedConversationId = null
        }
        realtimeEventsJob?.cancel()
        realtimeEventsJob = null
        presenceJob?.cancel()
        presenceJob = null
        typingEventsJob?.cancel()
        typingEventsJob = null
        typingStopDebounceJob?.cancel()
        typingStopDebounceJob = null
        remoteTypingExpiryJobs.values.forEach { it.cancel() }
        remoteTypingExpiryJobs.clear()
        isLocallyTyping = false
        lastTypingSentAtMs = 0L
        realtimeClient.disconnect()
        _uiState.update {
            it.copy(
                onlineUserIds = emptySet(),
                typingUsersByConversation = emptyMap()
            )
        }
    }

    fun stopRealtime() {
        activeRealtimeConsumers = 0
        realtimeUserId = null
        stopRealtimeGraceJob?.cancel()
        stopRealtimeGraceJob = null
        stopRealtimeInternal(clearJoinedChannel = true)
    }

    override fun onCleared() {
        stopRealtime()
        super.onCleared()
    }

    fun loadConversations(userId: String) {
        val isSameUser = cachedUserId == userId
        if (!isSameUser) {
            cachedUserId = userId
            messagesCache.clear()
            activeThreadId = null
            _uiState.update {
                it.copy(
                    conversations = emptyList(),
                    activeMessages = emptyList(),
                    pendingProductAttachment = null
                )
            }
        }
        val hasCachedConversations = isSameUser && _uiState.value.conversations.isNotEmpty()
        if (!hasCachedConversations) {
            _uiState.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            var hasAnyCache = hasCachedConversations
            if (!hasAnyCache) {
                val diskCached = withContext(Dispatchers.IO) {
                    repository.chatCacheStore?.loadConversations(userId).orEmpty()
                }
                if (diskCached.isNotEmpty()) {
                    hasAnyCache = true
                    _uiState.update { it.copy(loading = false, conversations = diskCached, error = null) }
                }
            }
            when (val res = repository.fetchConversations(userId)) {
                is ApiResult.Success -> {
                    val sorted = res.data.sortedByDescending { it.lastMessage?.createdAt ?: it.updatedAt.orEmpty() }
                    _uiState.update { it.copy(loading = false, conversations = sorted, error = null) }
                    withContext(Dispatchers.IO) {
                        repository.chatCacheStore?.saveConversations(userId, sorted)
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(loading = false, error = if (hasAnyCache) null else res.message)
                }
            }
        }
    }

    private fun bumpConversationToTop(conversationId: String, latestMessage: MessageRow) {
        _uiState.update { state ->
            val idx = state.conversations.indexOfFirst { it.id == conversationId }
            if (idx < 0) return@update state
            val updatedConv = state.conversations[idx].copy(
                lastMessage = latestMessage,
                updatedAt = latestMessage.createdAt ?: state.conversations[idx].updatedAt
            )
            val reordered = buildList {
                add(updatedConv)
                state.conversations.forEachIndexed { i, c -> if (i != idx) add(c) }
            }
            state.copy(conversations = reordered)
        }
    }

    private var activeThreadId: String? = null

    fun loadThread(conversationId: String, silent: Boolean = false) {
        val isDifferentThread = activeThreadId != conversationId
        activeThreadId = conversationId

        val cached = messagesCache[conversationId].orEmpty()
        val hasCache = cached.isNotEmpty()

        if (isDifferentThread) {
            _uiState.update {
                it.copy(
                    loading = !hasCache,
                    activeMessages = cached,
                    error = null
                )
            }
        } else if (!silent && !hasCache) {
            _uiState.update { it.copy(loading = true, error = null) }
        }

        val lastServerCreatedAt = cached
            .lastOrNull { it.id?.startsWith("local_") != true && !it.createdAt.isNullOrBlank() }
            ?.createdAt

        loadThreadJob?.cancel()
        loadThreadJob = viewModelScope.launch {
            val uid = cachedUserId ?: repository.sessionStore.currentSession().userId
            var hasAnyCache = hasCache
            var effectiveAfterCreatedAt = lastServerCreatedAt
            if (!hasAnyCache && uid.isNotBlank()) {
                val diskCached = withContext(Dispatchers.IO) {
                    repository.chatCacheStore?.loadMessages(uid, conversationId).orEmpty()
                }
                if (diskCached.isNotEmpty()) {
                    messagesCache[conversationId] = diskCached
                    hasAnyCache = true
                    effectiveAfterCreatedAt = diskCached
                        .lastOrNull { it.id?.startsWith("local_") != true && !it.createdAt.isNullOrBlank() }
                        ?.createdAt
                    if (activeThreadId == conversationId) {
                        _uiState.update { state ->
                            val pendingLocal = state.activeMessages.filter { it.id?.startsWith("local_") == true }
                            val merged = if (pendingLocal.isEmpty()) diskCached else diskCached + pendingLocal
                            state.copy(loading = false, activeMessages = merged, error = null)
                        }
                    }
                }
            }

            when (val res = repository.fetchMessagesForConversation(conversationId, afterCreatedAt = effectiveAfterCreatedAt)) {
                is ApiResult.Success -> {
                    val incoming = res.data
                    val base = messagesCache[conversationId].orEmpty()
                    val combinedServer = if (effectiveAfterCreatedAt != null && base.isNotEmpty()) {
                        val existingIds = base.mapNotNull { it.id }.toHashSet()
                        val fresh = incoming.filter { it.id == null || it.id !in existingIds }
                        if (fresh.isEmpty()) base else base + fresh
                    } else {
                        incoming
                    }
                    messagesCache[conversationId] = combinedServer
                    if (incoming.isNotEmpty()) {
                        combinedServer.lastOrNull()?.let { bumpConversationToTop(conversationId, it) }
                    }
                    if (uid.isNotBlank() && (incoming.isNotEmpty() || !hasAnyCache)) {
                        withContext(Dispatchers.IO) {
                            repository.chatCacheStore?.saveMessages(uid, conversationId, combinedServer)
                        }
                    }

                    if (activeThreadId == conversationId) {
                        _uiState.update { state ->
                            val pendingLocal = state.activeMessages.filter { it.id?.startsWith("local_") == true }
                            val merged = if (pendingLocal.isEmpty()) combinedServer else combinedServer + pendingLocal
                            state.copy(loading = false, activeMessages = merged, error = null)
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (!silent && !hasAnyCache && activeThreadId == conversationId) {
                        _uiState.update { it.copy(loading = false, error = res.message) }
                    }
                }
            }
        }
    }

    fun stageProductAttachment(conversationId: String, product: Product) {
        _uiState.update { it.copy(pendingProductAttachment = conversationId to product) }
    }

    fun clearPendingProductAttachment(conversationId: String? = null) {
        _uiState.update { state ->
            if (conversationId == null || state.pendingProductAttachment?.first == conversationId) {
                state.copy(pendingProductAttachment = null)
            } else {
                state
            }
        }
    }

    fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String,
        onFailure: () -> Unit = {}
    ) {
        sendMessageInternal(conversationId, senderId, text, allowMediaMarker = false, onFailure = onFailure)
    }

    fun sendReplyMessage(
        conversationId: String,
        senderId: String,
        replyToId: String,
        replySenderName: String,
        replySnippet: String,
        text: String,
        onFailure: () -> Unit = {}
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val encoded = com.example.domain.model.KartFormatters.encodeMessageReply(
            replyToId = replyToId,
            senderName = replySenderName,
            snippet = replySnippet,
            body = trimmed
        )
        sendMessageInternal(conversationId, senderId, encoded, allowMediaMarker = true, onFailure = onFailure)
    }

    fun sendProductMessage(
        conversationId: String,
        senderId: String,
        product: Product,
        caption: String = "",
        onFailure: () -> Unit = {}
    ) {
        val encoded = com.example.domain.model.KartFormatters.encodeMessageProduct(product, caption)
        clearPendingProductAttachment(conversationId)
        sendMessageInternal(
            conversationId = conversationId,
            senderId = senderId,
            text = encoded,
            allowMediaMarker = true,
            onFailure = {
                stageProductAttachment(conversationId, product)
                onFailure()
            }
        )
    }

    private fun sendMessageInternal(
        conversationId: String,
        senderId: String,
        text: String,
        allowMediaMarker: Boolean,
        onFailure: () -> Unit = {}
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(sending = false) }
            return
        }
        if (trimmed.length > 2000) {
            _uiState.update { it.copy(sending = false, feedback = "Message is too long (maximum 2,000 characters).") }
            onFailure()
            return
        }
        val localId = "local_${java.util.UUID.randomUUID()}"
        val optimistic = MessageRow(
            id = localId,
            conversationId = conversationId,
            senderId = senderId,
            content = trimmed,
            createdAt = java.time.Instant.now().toString(),
            isRead = false
        )
        _uiState.update { state ->
            if (activeThreadId == conversationId) {
                state.copy(activeMessages = state.activeMessages + optimistic)
            } else {
                state
            }
        }
        bumpConversationToTop(conversationId, optimistic)

        viewModelScope.launch {
            try {
                when (val res = repository.sendMessage(conversationId, senderId, trimmed, allowMediaMarker = allowMediaMarker)) {
                    is ApiResult.Success -> {
                        val serverMsg = res.data
                        realtimeClient.sendNewMessageBroadcast(
                            conversationId = conversationId,
                            messageId = serverMsg.id.orEmpty(),
                            senderId = senderId
                        )
                        val cached = messagesCache[conversationId].orEmpty()
                        val updatedServerCache = if (cached.none { it.id == serverMsg.id }) {
                            cached + serverMsg
                        } else {
                            cached
                        }
                        messagesCache[conversationId] = updatedServerCache
                        val uid = cachedUserId ?: repository.sessionStore.currentSession().userId
                        if (uid.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                repository.chatCacheStore?.saveMessages(uid, conversationId, updatedServerCache)
                            }
                        }
                        _uiState.update { state ->
                            if (activeThreadId != conversationId) {
                                state.copy(sending = false)
                            } else {
                                val withoutDuplicate = state.activeMessages.filterNot {
                                    it.id == serverMsg.id && it.id != localId
                                }
                                val updated = withoutDuplicate.map { if (it.id == localId) serverMsg else it }
                                state.copy(sending = false, activeMessages = updated)
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update { state ->
                            val updatedMessages = if (activeThreadId == conversationId) {
                                state.activeMessages.filterNot { it.id == localId }
                            } else {
                                state.activeMessages
                            }
                            state.copy(
                                sending = false,
                                activeMessages = updatedMessages,
                                feedback = res.message
                            )
                        }
                        onFailure()
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updatedMessages = if (activeThreadId == conversationId) {
                        state.activeMessages.filterNot { it.id == localId }
                    } else {
                        state.activeMessages
                    }
                    state.copy(
                        sending = false,
                        activeMessages = updatedMessages,
                        feedback = KartErrorFormatter.fromThrowable(e, "Failed to send message.")
                    )
                }
                onFailure()
            }
        }
    }

    fun sendImageMessage(conversationId: String, senderId: String, resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            try {
                when (val upload = repository.uploadImageUri(
                    contentResolver = resolver,
                    uri = uri,
                    bucket = "chat-media",
                    isPrivate = true,
                    conversationId = conversationId
                )) {
                    is ApiResult.Success -> {
                        val url = upload.data.trim()
                        if (url.isBlank()) {
                            _uiState.update { it.copy(sending = false, feedback = "Image upload returned an empty URL.") }
                        } else {
                            sendMessageInternal(conversationId, senderId, "[IMAGE:$url]", allowMediaMarker = true)
                        }
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(sending = false, feedback = upload.message) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        sending = false,
                        feedback = KartErrorFormatter.fromThrowable(e, "Image upload failed.")
                    )
                }
            }
        }
    }

    fun sendVoiceMessage(conversationId: String, senderId: String, audioFile: java.io.File, durationSeconds: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            try {
                when (val upload = repository.uploadAudioFile(
                    file = audioFile,
                    bucket = "chat-media",
                    isPrivate = true,
                    conversationId = conversationId
                )) {
                    is ApiResult.Success -> {
                        val url = upload.data.trim()
                        if (url.isBlank()) {
                            _uiState.update { it.copy(sending = false, feedback = "Voice upload returned an empty URL.") }
                        } else {
                            val safeDuration = durationSeconds.coerceIn(1, 600)
                            sendMessageInternal(
                                conversationId,
                                senderId,
                                "[VOICE:$url|$safeDuration]",
                                allowMediaMarker = true
                            )
                        }
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(sending = false, feedback = upload.message) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        sending = false,
                        feedback = KartErrorFormatter.fromThrowable(e, "Voice upload failed.")
                    )
                }
            } finally {
                runCatching { audioFile.delete() }
            }
        }
    }

    fun reportUser(targetUserId: String, reason: String, details: String) {
        viewModelScope.launch {
            when (val res = repository.submitReport("user", targetUserId, reason, details)) {
                is ApiResult.Success -> _uiState.update { it.copy(feedback = "User reported to Kart moderation.") }
                is ApiResult.Error -> _uiState.update { it.copy(feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}

data class WalletUiState(
    val loading: Boolean = true,
    val wallet: Wallet = Wallet(),
    val plans: List<SubscriptionPlan> = emptyList(),
    val activeSubscription: UserSubscription? = null,
    val actionLoading: Boolean = false,
    val subscribingPlanId: String? = null,
    val error: String? = null,
    val feedback: String? = null
)

class WalletViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    fun loadWallet(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchWallet(userId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, wallet = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun initiateDeposit(amount: Double, onAuthorizationUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.initiateWalletDeposit(amount)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false) }
                    onAuthorizationUrl(res.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun withdraw(amount: Double, method: String, details: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.requestWithdrawal(amount, method, details)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Withdrawal request submitted!") }
                    loadWallet(userId)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun loadSubscriptions(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchSubscriptions(userId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(loading = false, plans = res.data.first, activeSubscription = res.data.second)
                }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun subscribe(planId: String, onUrlOrDone: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, subscribingPlanId = planId) }
            when (val res = repository.subscribeToPlan(planId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, subscribingPlanId = null) }
                    onUrlOrDone(res.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(actionLoading = false, subscribingPlanId = null, feedback = res.message)
                }
            }
        }
    }

    fun cancelSubscription(subId: String, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.cancelSubscription(subId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Subscription cancelled.") }
                    loadSubscriptions(userId)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}

data class SellerUiState(
    val loading: Boolean = true,
    val hub: SellerHubBundle? = null,
    val actionLoading: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class SellerViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SellerUiState())
    val uiState: StateFlow<SellerUiState> = _uiState.asStateFlow()

    // Caches successfully uploaded local URIs across retries so failed publishes don't duplicate storage files
    private val uploadedUriCache = mutableMapOf<String, String>()

    fun loadSellerHub(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchSellerHub(userId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, hub = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadProductForEdit(productId: String, onLoaded: (Product?) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.fetchProductById(productId)) {
                is ApiResult.Success -> onLoaded(res.data)
                is ApiResult.Error -> {
                    _uiState.update { it.copy(feedback = res.message) }
                    onLoaded(null)
                }
            }
        }
    }

    fun publishListing(
        sellerId: String,
        editProductId: String?,
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: String,
        campus: String,
        stock: Int,
        existingUrls: List<String>,
        newImageUris: List<Uri>,
        resolver: ContentResolver,
        originalPrice: Double? = null,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            val uploadedUrls = existingUrls.toMutableList()
            for (uri in newImageUris) {
                val uriKey = uri.toString()
                val cachedUrl = uploadedUriCache[uriKey]
                if (!cachedUrl.isNullOrBlank()) {
                    uploadedUrls.add(cachedUrl)
                } else {
                    when (val up = repository.uploadImageUri(resolver, uri, bucket = "products")) {
                        is ApiResult.Success -> {
                            uploadedUriCache[uriKey] = up.data
                            uploadedUrls.add(up.data)
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(actionLoading = false, feedback = up.message) }
                            return@launch
                        }
                    }
                }
            }
            val result = if (editProductId.isNullOrBlank()) {
                repository.createListing(sellerId, title, description, price, category, condition, campus, stock, uploadedUrls)
            } else {
                repository.updateListing(
                    sellerId = sellerId,
                    productId = editProductId,
                    title = title,
                    description = description,
                    price = price,
                    category = category,
                    condition = condition,
                    campus = campus,
                    stock = stock,
                    imageUrls = uploadedUrls,
                    originalPrice = originalPrice
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    uploadedUriCache.clear()
                    _uiState.update { it.copy(actionLoading = false) }
                    loadSellerHub(sellerId)
                    onSuccess(result.data.id)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = result.message) }
            }
        }
    }

    fun deleteListing(productId: String, sellerId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.deleteListing(sellerId, productId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Listing deleted.") }
                    loadSellerHub(sellerId)
                    onDeleted()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun promoteListing(productId: String, tierId: String, amount: Double, onUrlOrDone: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.createPromotion(productId, tierId, amount)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false) }
                    onUrlOrDone(res.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }
}
