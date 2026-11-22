package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.KartRepository
import com.example.domain.model.EmptyStateAsset
import com.example.ui.components.KartBottomNav
import com.example.ui.components.KartNavigationRail
import com.example.ui.components.KartTopBar
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.CheckoutScreen
import com.example.ui.screens.CreateEditListingScreen
import com.example.ui.screens.DepositFundsScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.ForgotPasswordScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IdentityVerificationScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MarketplaceScreen
import com.example.ui.screens.MessagesInboxScreen
import com.example.ui.screens.MyListingsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OrderDetailScreen
import com.example.ui.screens.OrdersListScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.PromoteListingScreen
import com.example.ui.screens.PromotionsListScreen
import com.example.ui.screens.SellerAnalyticsScreen
import com.example.ui.screens.SellerDashboardScreen
import com.example.ui.screens.SettingsDetailScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.StaticInfoScreen
import com.example.ui.screens.StatusGuardScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.screens.WithdrawFundsScreen
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.KartViewModelFactory
import com.example.ui.viewmodel.MarketplaceViewModel
import com.example.ui.viewmodel.MessagesViewModel
import com.example.ui.viewmodel.OrdersViewModel
import com.example.ui.viewmodel.ProductDetailViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.SellerViewModel
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun KartAppRoot(
    repository: KartRepository,
    sessionViewModel: SessionViewModel,
    deepLinkUri: Uri?,
    onDeepLinkConsumed: () -> Unit
) {
    val factory = remember(repository) { KartViewModelFactory(repository) }
    val homeVm: HomeViewModel = viewModel(factory = factory)
    val marketplaceVm: MarketplaceViewModel = viewModel(factory = factory)
    val productDetailVm: ProductDetailViewModel = viewModel(factory = factory)
    val ordersVm: OrdersViewModel = viewModel(factory = factory)
    val messagesVm: MessagesViewModel = viewModel(factory = factory)
    val walletVm: WalletViewModel = viewModel(factory = factory)
    val sellerVm: SellerViewModel = viewModel(factory = factory)
    val profileVm: ProfileViewModel = viewModel(factory = factory)
    val adminVm: AdminViewModel = viewModel(factory = factory)

    val navController = rememberNavController()
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Global snackbar feedback
    LaunchedEffect(sessionState.snackbarMessage) {
        sessionState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            sessionViewModel.clearMessage()
        }
    }

    // Handle Deep Links (kart-app:// and https://www.kart.cx/...)
    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            val raw = deepLinkUri.toString()
            val refreshToken = deepLinkUri.getQueryParameter("refresh_token")
            val authErrorParam = deepLinkUri.getQueryParameter("error")
            when {
                !refreshToken.isNullOrBlank() -> {
                    sessionViewModel.handleDeepLinkRefreshToken(refreshToken) {
                        navController.navigate("home") { popUpTo(0) }
                    }
                }
                (raw.contains("auth-tokens") || deepLinkUri.path == "/login") && !authErrorParam.isNullOrBlank() -> {
                    sessionViewModel.showMessage(authErrorParam)
                }
                raw.contains("checkout-success") -> {
                    val orderId = deepLinkUri.getQueryParameter("orderId")
                    val subId = deepLinkUri.getQueryParameter("subscriptionId")
                    val ref = deepLinkUri.getQueryParameter("reference") ?: deepLinkUri.getQueryParameter("trxref")
                    if (!ref.isNullOrBlank()) {
                        repository.verifyPaystackReference(ref)
                    }
                    when {
                        !orderId.isNullOrBlank() -> navController.navigate("order_detail/$orderId")
                        !subId.isNullOrBlank() -> navController.navigate("subscriptions")
                        else -> navController.navigate("orders")
                    }
                }
                deepLinkUri.path?.startsWith("/marketplace/") == true -> {
                    val seg = deepLinkUri.lastPathSegment
                    if (!seg.isNullOrBlank() && seg != "categories") {
                        navController.navigate("product/$seg")
                    }
                }
                deepLinkUri.path?.startsWith("/profile/") == true -> {
                    val seg = deepLinkUri.lastPathSegment
                    if (!seg.isNullOrBlank()) navController.navigate("public_profile/$seg")
                }
            }
            onDeepLinkConsumed()
        }
    }

    // Banned & Maintenance Guards
    if (sessionState.isBanned) {
        StatusGuardScreen(
            asset = EmptyStateAsset.AUTH_ERROR,
            title = "Account Suspended",
            subtitle = "This Kart account has been suspended. Please contact support if you believe this is a mistake.",
            actionLabel = "Sign Out",
            onAction = { sessionViewModel.signOut() }
        )
        return
    }
    if (sessionState.isMaintenanceMode && !sessionState.isAdmin) {
        StatusGuardScreen(
            asset = EmptyStateAsset.OFFLINE,
            title = "Kart is Under Maintenance",
            subtitle = "We're upgrading campus servers and will be back shortly."
        )
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val baseRoute = currentRoute.substringBefore("/").substringBefore("?")

    val topLevelTabRoutes = setOf("home", "marketplace", "messages", "profile")
    val showMainChrome = baseRoute in topLevelTabRoutes

    fun navigateTopTab(route: String) {
        if (route == "seller_create") {
            if (!sessionState.isLoggedIn) navController.navigate("login")
            else navController.navigate("seller_create")
            return
        }
        if (route == "profile" && !sessionState.isLoggedIn) {
            navController.navigate("login")
            return
        }
        if (baseRoute == route && route != "marketplace") return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpanded = maxWidth >= 600.dp
        val contentMaxWidth = if (isExpanded && showMainChrome) maxWidth - 80.dp else maxWidth
        val showBottomBar = !isExpanded && showMainChrome

        Row(modifier = Modifier.fillMaxSize()) {
            if (isExpanded && showMainChrome) {
                KartNavigationRail(
                    currentRoute = baseRoute,
                    unreadMessages = sessionState.unreadMessages,
                    onTabSelected = ::navigateTopTab
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                    sizeTransform = { null },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .clipToBounds()
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeVm,
                            wishlistIds = sessionState.wishlistIds,
                            contentMaxWidth = contentMaxWidth,
                            showBottomBar = showBottomBar,
                            topBar = { fillProgress ->
                                KartTopBar(
                                    isTransparent = fillProgress < 0.5f,
                                    fillProgress = fillProgress,
                                    isLoggedIn = sessionState.isLoggedIn,
                                    unreadNotifications = sessionState.unreadNotifications,
                                    onLogoClick = { homeVm.loadHome(isRefresh = true) },
                                    onNotificationsClick = { navController.navigate("notifications") },
                                    onSignInClick = { navController.navigate("login") }
                                )
                            },
                            onCategoryClick = { cat ->
                                navController.navigate("marketplace?category=${Uri.encode(cat)}")
                            },
                            onSeeAllCategories = { navController.navigate("categories") },
                            onExploreMarketplace = { navigateTopTab("marketplace") },
                            onProductClick = { p -> navController.navigate("product/${p.id}") },
                            onWishlistToggle = { pid ->
                                sessionViewModel.toggleWishlist(pid) { navController.navigate("login") }
                            },
                            onInfoPageClick = { slug -> navController.navigate("info/$slug") }
                        )
                    }

                    composable(
                        route = "marketplace?category={category}",
                        arguments = listOf(navArgument("category") { type = NavType.StringType; defaultValue = "" })
                    ) { backStack ->
                        val cat = backStack.arguments?.getString("category").orEmpty()
                        KartTabScreenFrame(
                            showBottomBar = showBottomBar,
                            topBar = {
                                KartTopBar(
                                    isTransparent = false,
                                    isLoggedIn = sessionState.isLoggedIn,
                                    unreadNotifications = sessionState.unreadNotifications,
                                    onLogoClick = { navigateTopTab("home") },
                                    onNotificationsClick = { navController.navigate("notifications") },
                                    onSignInClick = { navController.navigate("login") }
                                )
                            }
                        ) {
                            MarketplaceScreen(
                                viewModel = marketplaceVm,
                                initialCategory = cat.ifBlank { null },
                                wishlistIds = sessionState.wishlistIds,
                                contentMaxWidth = contentMaxWidth,
                                onProductClick = { p -> navController.navigate("product/${p.id}") },
                                onWishlistToggle = { pid ->
                                    sessionViewModel.toggleWishlist(pid) { navController.navigate("login") }
                                }
                            )
                        }
                    }

                    composable("categories") {
                        CategoriesScreen(
                            contentMaxWidth = contentMaxWidth,
                            onBack = { navController.popBackStack() },
                            onCategorySelected = { cat ->
                                marketplaceVm.setCategory(cat)
                                navController.navigate("marketplace?category=${Uri.encode(cat)}") {
                                    popUpTo("categories") { inclusive = true }
                                }
                            }
                        )
                    }

                            composable("product/{id}") { backStack ->
                                val pid = backStack.arguments?.getString("id").orEmpty()
                                ProductDetailScreen(
                                    productId = pid,
                                    viewModel = productDetailVm,
                                    currentUserId = sessionState.session.userId.ifBlank { null },
                                    wishlistIds = sessionState.wishlistIds,
                                    onBack = { navController.popBackStack() },
                                    onWishlistToggle = { id ->
                                        sessionViewModel.toggleWishlist(id) { navController.navigate("login") }
                                    },
                                    onRequireAuth = { navController.navigate("login") },
                                    onOpenChat = { convId -> navController.navigate("chat/$convId") },
                                    onBuyNow = { id -> navController.navigate("checkout/$id") },
                                    onSellerClick = { sid -> navController.navigate("public_profile/$sid") },
                                    onProductClick = { p -> navController.navigate("product/${p.id}") },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("checkout/{id}") { backStack ->
                                val pid = backStack.arguments?.getString("id").orEmpty()
                                CheckoutScreen(
                                    productId = pid,
                                    repository = repository,
                                    currentUserId = sessionState.session.userId.ifBlank { null },
                                    onBack = { navController.popBackStack() },
                                    onOrderCreated = { orderId ->
                                        navController.navigate("order_detail/$orderId") {
                                            popUpTo("checkout/$pid") { inclusive = true }
                                        }
                                    },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Auth Routes ---
                            composable("login") {
                                LoginScreen(
                                    sessionViewModel = sessionViewModel,
                                    onLoginSuccess = {
                                        if (!navController.popBackStack()) navigateTopTab("home")
                                    },
                                    onNavigateSignUp = { navController.navigate("signup") },
                                    onNavigateForgotPassword = { navController.navigate("forgot_password") },
                                    onBack = { if (!navController.popBackStack()) navigateTopTab("home") }
                                )
                            }

                            composable("signup") {
                                SignUpScreen(
                                    sessionViewModel = sessionViewModel,
                                    onSignedUp = { immediate ->
                                        if (immediate) navController.navigate("home") { popUpTo(0) }
                                        else navController.navigate("login") { popUpTo("signup") { inclusive = true } }
                                    },
                                    onNavigateLogin = { navController.popBackStack() }
                                )
                            }

                            composable("forgot_password") {
                                ForgotPasswordScreen(
                                    sessionViewModel = sessionViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // --- Orders ---
                            composable("orders") {
                                OrdersListScreen(
                                    viewModel = ordersVm,
                                    userId = sessionState.session.userId,
                                    asSeller = false,
                                    onBack = { navController.popBackStack() },
                                    onOrderClick = { oid -> navController.navigate("order_detail/$oid") },
                                    onExploreShop = { navigateTopTab("marketplace") }
                                )
                            }

                            composable("seller_orders") {
                                OrdersListScreen(
                                    viewModel = ordersVm,
                                    userId = sessionState.session.userId,
                                    asSeller = true,
                                    onBack = { navController.popBackStack() },
                                    onOrderClick = { oid -> navController.navigate("order_detail/$oid") },
                                    onExploreShop = { navigateTopTab("marketplace") }
                                )
                            }

                            composable("order_detail/{id}") { backStack ->
                                val oid = backStack.arguments?.getString("id").orEmpty()
                                OrderDetailScreen(
                                    orderId = oid,
                                    currentUserId = sessionState.session.userId,
                                    viewModel = ordersVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Messages ---
                            composable("messages") {
                                KartTabScreenFrame(
                                    showBottomBar = showBottomBar,
                                    topBar = {
                                        KartTopBar(
                                            isTransparent = false,
                                            isLoggedIn = sessionState.isLoggedIn,
                                            unreadNotifications = sessionState.unreadNotifications,
                                            onLogoClick = { navigateTopTab("home") },
                                            onNotificationsClick = { navController.navigate("notifications") },
                                            onSignInClick = { navController.navigate("login") }
                                        )
                                    }
                                ) {
                                    MessagesInboxScreen(
                                        userId = sessionState.session.userId.ifBlank { null },
                                        viewModel = messagesVm,
                                        onConversationClick = { cid -> navController.navigate("chat/$cid") },
                                        onRequireAuth = { navController.navigate("login") },
                                        onExploreShop = { navigateTopTab("marketplace") }
                                    )
                                }
                            }

                            composable("chat/{id}") { backStack ->
                                val cid = backStack.arguments?.getString("id").orEmpty()
                                ChatThreadScreen(
                                    conversationId = cid,
                                    currentUserId = sessionState.session.userId,
                                    viewModel = messagesVm,
                                    onBack = { navController.popBackStack() },
                                    onProductClick = { pid -> navController.navigate("product/$pid") },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Wallet & Subscriptions ---
                            composable("wallet") {
                                WalletScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = walletVm,
                                    onBack = { navController.popBackStack() },
                                    onDepositClick = { navController.navigate("wallet_deposit") },
                                    onWithdrawClick = { navController.navigate("wallet_withdraw") },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("wallet_deposit") {
                                DepositFundsScreen(
                                    viewModel = walletVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("wallet_withdraw") {
                                WithdrawFundsScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = walletVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("subscriptions") {
                                SubscriptionsScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = walletVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Seller Studio ---
                            composable("seller_dashboard") {
                                SellerDashboardScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onCreateListing = { navController.navigate("seller_create") },
                                    onMyListings = { navController.navigate("seller_listings") },
                                    onSellerOrders = { navController.navigate("seller_orders") },
                                    onPromotions = { navController.navigate("seller_promotions") },
                                    onAnalytics = { navController.navigate("seller_analytics") },
                                    onSubscriptions = { navController.navigate("subscriptions") }
                                )
                            }

                            composable("seller_create") {
                                CreateEditListingScreen(
                                    sellerId = sessionState.session.userId.ifBlank { null },
                                    editProduct = null,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = { navController.navigate("login") },
                                    onViewCreatedListing = { pid ->
                                        navController.navigate("product/$pid") {
                                            popUpTo("seller_create") { inclusive = true }
                                        }
                                    },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("seller_edit/{id}") { backStack ->
                                val pid = backStack.arguments?.getString("id").orEmpty()
                                val sellerState by sellerVm.uiState.collectAsStateWithLifecycle()
                                val existing = sellerState.hub?.listings?.firstOrNull { it.id == pid }
                                CreateEditListingScreen(
                                    sellerId = sessionState.session.userId.ifBlank { null },
                                    editProduct = existing,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = { navController.navigate("login") },
                                    onViewCreatedListing = { id ->
                                        navController.navigate("product/$id") { popUpTo("seller_edit/$pid") { inclusive = true } }
                                    },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("seller_listings") {
                                MyListingsScreen(
                                    sellerId = sessionState.session.userId,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onCreateNew = { navController.navigate("seller_create") },
                                    onEditListing = { pid -> navController.navigate("seller_edit/$pid") },
                                    onPromoteListing = { pid -> navController.navigate("seller_promote/$pid") },
                                    onViewProduct = { pid -> navController.navigate("product/$pid") },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("seller_promote/{id}") { backStack ->
                                val pid = backStack.arguments?.getString("id").orEmpty()
                                PromoteListingScreen(
                                    productId = pid,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("seller_promotions") {
                                PromotionsListScreen(
                                    sellerId = sessionState.session.userId,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() },
                                    onMyListings = { navController.navigate("seller_listings") }
                                )
                            }

                            composable("seller_analytics") {
                                SellerAnalyticsScreen(
                                    sellerId = sessionState.session.userId,
                                    viewModel = sellerVm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // --- Profile, Wishlist & Notifications ---
                            composable("profile") {
                                KartTabScreenFrame(
                                    showBottomBar = showBottomBar,
                                    topBar = {
                                        KartTopBar(
                                            isTransparent = false,
                                            isLoggedIn = sessionState.isLoggedIn,
                                            unreadNotifications = sessionState.unreadNotifications,
                                            onLogoClick = { navigateTopTab("home") },
                                            onNotificationsClick = { navController.navigate("notifications") },
                                            onSignInClick = { navController.navigate("login") }
                                        )
                                    }
                                ) {
                                    ProfileScreen(
                                        targetUserId = null,
                                        currentUserId = sessionState.session.userId.ifBlank { null },
                                        viewModel = profileVm,
                                        wishlistIds = sessionState.wishlistIds,
                                        isEmbeddedTab = true,
                                        onBack = {},
                                        onRequireAuth = { navController.navigate("login") },
                                        onEditProfile = { navController.navigate("profile_edit") },
                                        onSellerHub = { navController.navigate("seller_dashboard") },
                                        onOrders = { navController.navigate("orders") },
                                        onWallet = { navController.navigate("wallet") },
                                        onWishlist = { navController.navigate("wishlist") },
                                        onNotifications = { navController.navigate("notifications") },
                                        onSettings = { navController.navigate("settings") },
                                        onVerifyIdentity = { navController.navigate("verify_identity") },
                                        onAdminConsole = { navController.navigate("admin") },
                                        onSignOut = { sessionViewModel.signOut { navigateTopTab("home") } },
                                        onProductClick = { p -> navController.navigate("product/${p.id}") },
                                        onWishlistToggle = { pid -> sessionViewModel.toggleWishlist(pid) { navController.navigate("login") } },
                                        onOpenUserProfile = { uid -> navController.navigate("public_profile/$uid") }
                                    )
                                }
                            }

                            composable("public_profile/{id}") { backStack ->
                                val uid = backStack.arguments?.getString("id").orEmpty()
                                ProfileScreen(
                                    targetUserId = uid,
                                    currentUserId = sessionState.session.userId.ifBlank { null },
                                    viewModel = profileVm,
                                    wishlistIds = sessionState.wishlistIds,
                                    isEmbeddedTab = false,
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = { navController.navigate("login") },
                                    onEditProfile = { navController.navigate("profile_edit") },
                                    onSellerHub = { navController.navigate("seller_dashboard") },
                                    onOrders = { navController.navigate("orders") },
                                    onWallet = { navController.navigate("wallet") },
                                    onWishlist = { navController.navigate("wishlist") },
                                    onNotifications = { navController.navigate("notifications") },
                                    onSettings = { navController.navigate("settings") },
                                    onVerifyIdentity = { navController.navigate("verify_identity") },
                                    onAdminConsole = { navController.navigate("admin") },
                                    onSignOut = { sessionViewModel.signOut { navigateTopTab("home") } },
                                    onProductClick = { p -> navController.navigate("product/${p.id}") },
                                    onWishlistToggle = { pid -> sessionViewModel.toggleWishlist(pid) { navController.navigate("login") } },
                                    onOpenUserProfile = { nextUid -> navController.navigate("public_profile/$nextUid") }
                                )
                            }

                            composable("profile_edit") {
                                EditProfileScreen(
                                    userId = sessionState.session.userId,
                                    initialProfile = sessionState.profile,
                                    viewModel = profileVm,
                                    onBack = { navController.popBackStack() },
                                    onSaved = {
                                        sessionViewModel.refreshUserMetadata()
                                        navController.popBackStack()
                                    },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("wishlist") {
                                WishlistScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = profileVm,
                                    wishlistIds = sessionState.wishlistIds,
                                    onBack = { navController.popBackStack() },
                                    onProductClick = { p -> navController.navigate("product/${p.id}") },
                                    onWishlistToggle = { pid -> sessionViewModel.toggleWishlist(pid) { navController.navigate("login") } },
                                    onExploreShop = { navigateTopTab("marketplace") }
                                )
                            }

                            composable("notifications") {
                                NotificationsScreen(
                                    userId = sessionState.session.userId,
                                    viewModel = profileVm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // --- Settings & KYC ---
                            composable("settings") {
                                SettingsHubScreen(
                                    profile = sessionState.profile,
                                    onBack = { navController.popBackStack() },
                                    onNavigateSection = { sec -> navController.navigate("settings_detail/$sec") },
                                    onNavigateVerify = { navController.navigate("verify_identity") },
                                    onNavigateInfo = { slug -> navController.navigate("info/$slug") },
                                    onSignOut = { sessionViewModel.signOut { navigateTopTab("home") } }
                                )
                            }

                            composable("settings_detail/{section}") { backStack ->
                                val section = backStack.arguments?.getString("section").orEmpty()
                                SettingsDetailScreen(
                                    section = section,
                                    profile = sessionState.profile,
                                    sessionViewModel = sessionViewModel,
                                    repository = repository,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("verify_identity") {
                                IdentityVerificationScreen(
                                    profile = sessionState.profile,
                                    repository = repository,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Admin & Static Info ---
                            composable("admin") {
                                AdminDashboardScreen(
                                    isAdmin = sessionState.isAdmin,
                                    viewModel = adminVm,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("info/{slug}") { backStack ->
                                val slug = backStack.arguments?.getString("slug") ?: "about"
                                StaticInfoScreen(slug = slug, onBack = { navController.popBackStack() })
                            }
                        }

                        if (showBottomBar) {
                            KartBottomNav(
                                currentRoute = baseRoute,
                                unreadMessages = sessionState.unreadMessages,
                                onTabSelected = ::navigateTopTab,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = if (showBottomBar) 68.dp else 12.dp)
                        )
                    }
                }
            }
        }

@Composable
private fun KartTabScreenFrame(
    showBottomBar: Boolean,
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (showBottomBar) Modifier.navigationBarsPadding().padding(bottom = 63.dp)
                else Modifier.navigationBarsPadding()
            )
    ) {
        topBar()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
