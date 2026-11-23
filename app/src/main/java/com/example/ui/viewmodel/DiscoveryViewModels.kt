package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ApiResult
import com.example.data.repository.HomeFeedData
import com.example.data.repository.KartRepository
import com.example.data.repository.ProductDetailBundle
import com.example.domain.model.KartGhanaCampuses
import com.example.domain.model.MarketplaceFilter
import com.example.domain.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feed: HomeFeedData? = null,
    val error: String? = null
)

class HomeViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repository.fetchCampuses() }
        loadHome()
    }

    fun loadHome(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(refreshing = true, error = null)
                else it.copy(loading = it.feed == null, error = null)
            }
            when (val res = repository.fetchHomeFeed()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(loading = false, refreshing = false, feed = res.data, error = null)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(loading = false, refreshing = false, error = res.message)
                }
            }
        }
    }

    fun trackAdView(adId: String?) {
        viewModelScope.launch { repository.trackAdEvent(adId, "view") }
    }

    fun trackAdClick(adId: String?) {
        viewModelScope.launch { repository.trackAdEvent(adId, "click") }
    }
}

data class MarketplaceUiState(
    val filter: MarketplaceFilter = MarketplaceFilter(),
    val products: List<Product> = emptyList(),
    val campuses: List<String> = KartGhanaCampuses,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val error: String? = null
)

class MarketplaceViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MarketplaceUiState())
    val uiState: StateFlow<MarketplaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val campuses = repository.fetchCampuses()
            _uiState.update { it.copy(campuses = campuses) }
        }
        loadProducts(reset = true)
    }

    fun applyFilter(newFilter: MarketplaceFilter) {
        _uiState.update { it.copy(filter = newFilter) }
        loadProducts(reset = true)
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(filter = it.filter.copy(query = query)) }
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(filter = it.filter.copy(category = category)) }
        loadProducts(reset = true)
    }

    fun resetFilters() {
        _uiState.update { it.copy(filter = MarketplaceFilter()) }
        loadProducts(reset = true)
    }

    fun loadProducts(reset: Boolean = false, isRefresh: Boolean = false) {
        val state = _uiState.value
        val nextPage = if (reset) 1 else state.currentPage + 1
        if (!reset && (state.loading || state.loadingMore || state.refreshing || !state.hasMore)) return

        viewModelScope.launch {
            _uiState.update {
                when {
                    isRefresh -> it.copy(refreshing = true, error = null)
                    reset -> it.copy(loading = true, error = null)
                    else -> it.copy(loadingMore = true)
                }
            }
            when (val res = repository.fetchMarketplaceProducts(state.filter, page = nextPage, limit = 12)) {
                is ApiResult.Success -> {
                    _uiState.update { curr ->
                        val merged = if (reset) res.data.products else (curr.products + res.data.products).distinctBy { it.id }
                        curr.copy(
                            products = merged,
                            hasMore = res.data.hasMore,
                            currentPage = nextPage,
                            loading = false,
                            loadingMore = false,
                            refreshing = false,
                            error = null
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(loading = false, loadingMore = false, refreshing = false, error = res.message)
                    }
                }
            }
        }
    }
}

data class ProductDetailUiState(
    val loading: Boolean = true,
    val bundle: ProductDetailBundle? = null,
    val error: String? = null,
    val actionLoading: Boolean = false,
    val feedbackMessage: String? = null
)

class ProductDetailViewModel(private val repository: KartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    fun loadProduct(productId: String, currentUserId: String?) {
        val isDifferentProduct = _uiState.value.bundle?.product?.id != productId
        _uiState.update {
            it.copy(
                loading = true,
                bundle = if (isDifferentProduct) null else it.bundle,
                error = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.fetchProductDetail(productId, currentUserId)) {
                is ApiResult.Success -> _uiState.update { it.copy(loading = false, bundle = res.data, error = null) }
                is ApiResult.Error -> _uiState.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun toggleFollow(sellerId: String) {
        val currentBundle = _uiState.value.bundle ?: return
        val nextFollow = !currentBundle.isFollowingSeller
        val nextCount = (currentBundle.followerCount + if (nextFollow) 1 else -1).coerceAtLeast(0)
        _uiState.update {
            it.copy(bundle = currentBundle.copy(isFollowingSeller = nextFollow, followerCount = nextCount))
        }
        viewModelScope.launch {
            when (val res = repository.toggleFollow(sellerId)) {
                is ApiResult.Error -> {
                    _uiState.update { it.copy(bundle = currentBundle, feedbackMessage = res.message) }
                }
                is ApiResult.Success -> Unit
            }
        }
    }

    fun submitReview(
        productId: String,
        sellerId: String?,
        rating: Int,
        comment: String,
        currentUserId: String?,
        orderId: String? = null
    ) {
        val currentBundle = _uiState.value.bundle
        val verifiedOrderId = orderId ?: currentBundle?.verifiedBuyerOrderId
        if (currentBundle?.hasUserReviewed == true) {
            _uiState.update { it.copy(feedbackMessage = "You have already reviewed this listing.") }
            return
        }
        if (verifiedOrderId.isNullOrBlank()) {
            _uiState.update {
                it.copy(feedbackMessage = "Only verified buyers with completed orders can review this item.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.submitReview(productId, sellerId, verifiedOrderId, rating, comment)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false, feedbackMessage = "Review published!") }
                    loadProduct(productId, currentUserId)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedbackMessage = res.message) }
            }
        }
    }

    fun submitReport(productId: String, reason: String, details: String) {
        viewModelScope.launch {
            when (val res = repository.submitReport("product", productId, reason, details)) {
                is ApiResult.Success -> _uiState.update { it.copy(feedbackMessage = "Report submitted to Kart moderation.") }
                is ApiResult.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
            }
        }
    }

    fun startChatWithSeller(currentUserId: String, sellerId: String, productId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true) }
            when (val res = repository.getOrCreateConversation(currentUserId, sellerId, productId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(actionLoading = false) }
                    onReady(res.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(actionLoading = false, feedbackMessage = res.message) }
            }
        }
    }

    fun trackShare(productId: String) {
        viewModelScope.launch { repository.trackShare(productId) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
