package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.data.repository.SellerHubBundle
import com.example.domain.model.EnrichedConversation
import com.example.domain.model.MessageRow
import com.example.domain.model.Order
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.UserSubscription
import com.example.domain.model.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val loading: Boolean = true,
    val orders: List<Order> = emptyList(),
    val selectedOrder: Order? = null,
    val actionLoading: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class OrdersViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private var lastOrdersAsSeller: Boolean? = null

    fun loadOrders(userId: String, asSeller: Boolean = false) {
        val modeChanged = lastOrdersAsSeller != asSeller
        lastOrdersAsSeller = asSeller
        _uiState.update {
            it.copy(
                loading = true,
                orders = if (modeChanged) emptyList() else it.orders,
                error = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.fetchOrders(userId, asSeller)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, orders = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadOrderDetail(orderId: String) {
        val isDifferentOrder = _uiState.value.selectedOrder?.id != orderId
        _uiState.update {
            it.copy(
                loading = true,
                selectedOrder = if (isDifferentOrder) null else it.selectedOrder,
                error = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.fetchOrderById(orderId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, selectedOrder = res.data) }
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
            when (val res = repository.sendDeliveryOtp(orderId)) {
                is ApiResult.Success -> _uiState.update { it.copy(feedback = "Delivery OTP sent to your verified contact.") }
                is ApiResult.Error -> _uiState.update { it.copy(feedback = res.message) }
            }
        }
    }

    fun confirmDelivery(orderId: String, code: String, mode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.confirmDelivery(orderId, code, mode)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Delivery confirmed! Escrow released.") }
                    loadOrderDetail(orderId)
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun requestRefund(orderId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.requestRefund(orderId, reason)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Refund request submitted.") }
                    loadOrderDetail(orderId)
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
                    _uiState.update { it.copy(actionLoading = false) }
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
    val sending: Boolean = false,
    val error: String? = null,
    val feedback: String? = null
)

class MessagesViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var cachedUserId: String? = null
    private val messagesCache = mutableMapOf<String, List<MessageRow>>()

    fun loadConversations(userId: String) {
        val isSameUser = cachedUserId == userId
        if (!isSameUser) {
            cachedUserId = userId
            messagesCache.clear()
        }
        val hasCachedConversations = isSameUser && _uiState.value.conversations.isNotEmpty()
        if (!hasCachedConversations) {
            _uiState.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            when (val res = repository.fetchConversations(userId)) {
                is ApiResult.Success -> {
                    val sorted = res.data.sortedByDescending { it.lastMessage?.createdAt ?: it.updatedAt.orEmpty() }
                    _uiState.update { it.copy(loading = false, conversations = sorted, error = null) }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(loading = false, error = if (hasCachedConversations) null else res.message)
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

        viewModelScope.launch {
            when (val res = repository.fetchMessagesForConversation(conversationId, afterCreatedAt = lastServerCreatedAt)) {
                is ApiResult.Success -> {
                    val incoming = res.data
                    val base = messagesCache[conversationId].orEmpty()
                    val combinedServer = if (lastServerCreatedAt != null && base.isNotEmpty()) {
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

                    if (activeThreadId == conversationId) {
                        _uiState.update { state ->
                            val pendingLocal = state.activeMessages.filter { it.id?.startsWith("local_") == true }
                            val merged = if (pendingLocal.isEmpty()) combinedServer else combinedServer + pendingLocal
                            state.copy(loading = false, activeMessages = merged, error = null)
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (!silent && !hasCache && activeThreadId == conversationId) {
                        _uiState.update { it.copy(loading = false, error = res.message) }
                    }
                }
            }
        }
    }

    fun sendMessage(conversationId: String, senderId: String, text: String) {
        if (text.isBlank()) return
        val localId = "local_${java.util.UUID.randomUUID()}"
        val optimistic = MessageRow(
            id = localId,
            conversationId = conversationId,
            senderId = senderId,
            content = text,
            createdAt = java.time.Instant.now().toString(),
            isRead = false
        )
        _uiState.update { it.copy(activeMessages = it.activeMessages + optimistic) }
        bumpConversationToTop(conversationId, optimistic)

        viewModelScope.launch {
            when (val res = repository.sendMessage(conversationId, senderId, text)) {
                is ApiResult.Success -> {
                    val serverMsg = res.data
                    val cached = messagesCache[conversationId].orEmpty()
                    if (cached.none { it.id == serverMsg.id }) {
                        messagesCache[conversationId] = cached + serverMsg
                    }
                    _uiState.update { state ->
                        val withoutDuplicate = state.activeMessages.filterNot {
                            it.id == serverMsg.id && it.id != localId
                        }
                        val updated = withoutDuplicate.map { if (it.id == localId) serverMsg else it }
                        state.copy(sending = false, activeMessages = updated)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            sending = false,
                            activeMessages = state.activeMessages.filterNot { it.id == localId },
                            feedback = res.message
                        )
                    }
                }
            }
        }
    }

    fun sendImageMessage(conversationId: String, senderId: String, resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            when (val upload = repository.uploadImageUri(resolver, uri, bucket = "products")) {
                is ApiResult.Success -> {
                    sendMessage(conversationId, senderId, "[IMAGE:${upload.data}]")
                }
                is ApiResult.Error -> _uiState.update { it.copy(sending = false, feedback = upload.message) }
            }
        }
    }

    fun sendVoiceMessage(conversationId: String, senderId: String, audioFile: java.io.File, durationSeconds: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            try {
                when (val upload = repository.uploadAudioFile(audioFile, bucket = "products")) {
                    is ApiResult.Success -> {
                        sendMessage(conversationId, senderId, "[VOICE:${upload.data}|${durationSeconds.coerceAtLeast(1)}]")
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(sending = false, feedback = upload.message) }
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
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.subscribeToPlan(planId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false) }
                    onUrlOrDone(res.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
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

    fun loadSellerHub(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val res = repository.fetchSellerHub(userId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, hub = res.data) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
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
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            val uploadedUrls = existingUrls.toMutableList()
            for (uri in newImageUris) {
                when (val up = repository.uploadImageUri(resolver, uri, bucket = "products")) {
                    is ApiResult.Success -> uploadedUrls.add(up.data)
                    is ApiResult.Error -> {
                        _uiState.update { it.copy(actionLoading = false, feedback = up.message) }
                        return@launch
                    }
                }
            }
            val result = if (editProductId.isNullOrBlank()) {
                repository.createListing(sellerId, title, description, price, category, condition, campus, stock, uploadedUrls)
            } else {
                repository.updateListing(editProductId, title, description, price, category, condition, campus, stock, uploadedUrls)
            }
            when (result) {
                is ApiResult.Success -> {
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
            when (val res = repository.deleteListing(productId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedback = "Listing deleted.") }
                    loadSellerHub(sellerId)
                    onDeleted()
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedback = res.message) }
            }
        }
    }

    fun promoteListing(productId: String, adType: String, days: Int, onUrlOrDone: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.createPromotion(productId, adType, days)) {
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
