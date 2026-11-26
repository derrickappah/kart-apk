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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val campuses by sessionViewModel.campuses.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pushEnabled by sessionViewModel.pushEnabled.collectAsStateWithLifecycle()

    val networkMonitor = remember(context) { com.example.data.remote.NetworkMonitor(context) }
    androidx.compose.runtime.DisposableEffect(networkMonitor) {
        networkMonitor.start()
        onDispose { networkMonitor.stop() }
    }
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()

    var askedNotifPermThisSession by rememberSaveable { mutableStateOf(false) }
    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            sessionViewModel.setPushEnabled(false)
        }
    }

    LaunchedEffect(sessionState.isLoggedIn, sessionState.session.userId) {
        if (sessionState.isLoggedIn && sessionState.session.userId.isNotBlank()) {
            messagesVm.startRealtime(sessionState.session.userId)
        } else {
            messagesVm.stopRealtime()
        }
    }

    LaunchedEffect(sessionState.isLoggedIn, pushEnabled) {
        com.example.notifications.KartNotificationHelper.ensureChannels(context)
        if (sessionState.isLoggedIn && pushEnabled && !askedNotifPermThisSession &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !com.example.notifications.KartNotificationHelper.hasNotificationPermission(context)
        ) {
            askedNotifPermThisSession = true
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        sessionViewModel.pushAlerts.collect { alert ->
            com.example.notifications.KartNotificationHelper.showNotification(
                context = context,
                notificationId = alert.id,
                channelId = if (alert.isMessage) {
                    com.example.notifications.KartNotificationHelper.CHANNEL_MESSAGES
                } else {
                    com.example.notifications.KartNotificationHelper.CHANNEL_UPDATES
                },
                title = alert.title,
                body = alert.body,
                deepLink = alert.deepLink
            )
        }
    }

    // Global snackbar feedback
    LaunchedEffect(sessionState.snackbarMessage) {
        sessionState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            sessionViewModel.clearMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isNavGraphReady = navBackStackEntry != null

    // Handle Deep Links (kart-app:// and https://www.kart.cx/...)
    LaunchedEffect(
        deepLinkUri,
        isNavGraphReady,
        sessionState.isBanned,
        sessionState.isMaintenanceMode,
        sessionState.isAdmin
    ) {
        val maintenanceBlocked = sessionState.isMaintenanceMode && !sessionState.isAdmin
        if (deepLinkUri != null && !sessionState.isBanned && !maintenanceBlocked) {
            // Wait until NavHost graph is initialized on cold start before navigating
            if (!isNavGraphReady) return@LaunchedEffect
            val graphReady = runCatching { navController.graph; true }.getOrDefault(false)
            if (!graphReady) return@LaunchedEffect

            val raw = deepLinkUri.toString()
            val normalizedPath = normalizeDeepLinkPath(deepLinkUri)
            val normalizedSegments = normalizedPath.trim('/').split('/').filter { it.isNotBlank() }
            val authCode = deepLinkUri.getQueryParameter("code")
            val authState = deepLinkUri.getQueryParameter("state")
            val refreshToken = deepLinkUri.getQueryParameter("refresh_token")
            val authErrorParam = deepLinkUri.getQueryParameter("error")
            val liveSession = repository.sessionStore.currentSession()
            val liveUserId = sessionState.session.userId.ifBlank { liveSession.userId }
            val isLoggedIn = sessionState.isLoggedIn || liveSession.isLoggedIn

            fun navigateAuthRequired(route: String, singleTop: Boolean = false) {
                if (!isLoggedIn) {
                    navController.navigate("login") { launchSingleTop = true }
                } else {
                    navController.navigate(route) { if (singleTop) launchSingleTop = true }
                }
            }

            val isTrustedAuthCallback =
                (deepLinkUri.scheme == "https" && deepLinkUri.host in setOf("www.kart.cx", "kart.cx")) ||
                    (deepLinkUri.scheme == "kart-app" && deepLinkUri.host in setOf("auth-callback", "auth-tokens"))
            when {
                !authCode.isNullOrBlank() && (normalizedPath.contains("auth-callback") || deepLinkUri.host == "auth-callback") -> {
                    if (!isTrustedAuthCallback || isLoggedIn) {
                        sessionViewModel.showMessage("Sign-in link ignored because a session is already active or the link is untrusted.")
                    } else {
                        sessionViewModel.handleDeepLinkAuthCode(authCode, authState) {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                    saveState = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                !refreshToken.isNullOrBlank() -> {
                    if (!isTrustedAuthCallback || isLoggedIn) {
                        sessionViewModel.showMessage("Sign-in link ignored because a session is already active or the link is untrusted.")
                    } else {
                        sessionViewModel.handleDeepLinkRefreshToken(refreshToken) {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                    saveState = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                (raw.contains("auth-callback") || raw.contains("auth-tokens") || normalizedPath == "/login") && !authErrorParam.isNullOrBlank() -> {
                    sessionViewModel.showMessage(authErrorParam)
                }
                raw.contains("checkout-success") -> {
                    val isTrustedPaymentCallback =
                        (deepLinkUri.scheme == "https" && deepLinkUri.host in setOf("www.kart.cx", "kart.cx")) ||
                            deepLinkUri.scheme == "kart-app"
                    if (!isTrustedPaymentCallback) {
                        sessionViewModel.showMessage("Ignored untrusted payment callback link.")
                    } else {
                        val callback = parsePaymentCallback(deepLinkUri)
                        val ref = callback.reference
                        var verificationFailed = false
                        if (!ref.isNullOrBlank()) {
                            val verifyRes = when {
                                callback.isSubscription ->
                                    repository.verifySubscriptionReference(callback.subscriptionId.orEmpty(), ref)
                                callback.isPromotion ->
                                    repository.verifyPromotionReference(callback.adId.orEmpty(), ref)
                                callback.isWallet ->
                                    repository.verifyWalletDeposit(ref)
                                else ->
                                    repository.verifyPaystackReference(callback.orderId.orEmpty(), ref)
                            }
                            if (verifyRes is com.example.data.repository.ApiResult.Error) {
                                verificationFailed = true
                                sessionViewModel.showMessage(verifyRes.message)
                            } else {
                                sessionViewModel.showMessage("Payment verified!")
                            }
                        }
                        if (!verificationFailed) {
                            if (liveUserId.isNotBlank()) {
                                walletVm.loadWallet(liveUserId)
                                ordersVm.loadOrders(liveUserId)
                                sellerVm.loadSellerHub(liveUserId)
                            }
                            when {
                                !callback.orderId.isNullOrBlank() -> {
                                    ordersVm.loadOrderDetail(callback.orderId)
                                    navigateAuthRequired("order_detail/${Uri.encode(callback.orderId)}", singleTop = true)
                                }
                                callback.isSubscription -> navigateAuthRequired("subscriptions", singleTop = true)
                                callback.isPromotion -> navigateAuthRequired("seller_promotions", singleTop = true)
                                callback.isWallet -> navigateAuthRequired("wallet", singleTop = true)
                                else -> navigateAuthRequired("orders", singleTop = true)
                            }
                        }
                    }
                }
                normalizedPath.startsWith("/marketplace/") -> {
                    val seg = normalizedSegments.lastOrNull()
                    if (!seg.isNullOrBlank() && seg != "categories") {
                        navController.navigate("product/${Uri.encode(seg)}")
                    }
                }
                normalizedPath.startsWith("/profile/") -> {
                    val seg = normalizedSegments.lastOrNull()
                    if (!seg.isNullOrBlank()) navController.navigate("public_profile/${Uri.encode(seg)}")
                }
                normalizedPath.startsWith("/messages/") -> {
                    val seg = normalizedSegments.lastOrNull()
                    if (!seg.isNullOrBlank()) navigateAuthRequired("chat/${Uri.encode(seg)}")
                }
                normalizedPath == "/messages" -> {
                    navigateAuthRequired("messages", singleTop = true)
                }
                normalizedPath == "/notifications" -> {
                    navigateAuthRequired("notifications", singleTop = true)
                }
                normalizedPath.contains("/orders/") -> {
                    val seg = normalizedSegments.lastOrNull()
                    if (!seg.isNullOrBlank()) {
                        val isTrustedOrderCallback =
                            (deepLinkUri.scheme == "https" && deepLinkUri.host in setOf("www.kart.cx", "kart.cx")) ||
                                deepLinkUri.scheme == "kart-app"
                        val ref = parsePaymentCallback(deepLinkUri).reference
                        if (!ref.isNullOrBlank() && isTrustedOrderCallback) {
                            when (val verifyRes = repository.verifyPaystackReference(seg, ref)) {
                                is com.example.data.repository.ApiResult.Success -> {
                                    sessionViewModel.showMessage("Payment verified!")
                                    if (liveUserId.isNotBlank()) {
                                        ordersVm.loadOrders(liveUserId)
                                    }
                                    ordersVm.loadOrderDetail(seg)
                                }
                                is com.example.data.repository.ApiResult.Error -> {
                                    sessionViewModel.showMessage(verifyRes.message)
                                }
                            }
                        }
                        navigateAuthRequired("order_detail/${Uri.encode(seg)}", singleTop = true)
                    }
                }
                normalizedPath == "/orders" -> {
                    navigateAuthRequired("orders", singleTop = true)
                }
                normalizedPath.contains("/wallet") -> {
                    val isTrustedWalletCallback =
                        (deepLinkUri.scheme == "https" && deepLinkUri.host in setOf("www.kart.cx", "kart.cx")) ||
                            deepLinkUri.scheme == "kart-app"
                    val ref = parsePaymentCallback(deepLinkUri).reference
                    if (!ref.isNullOrBlank() && isTrustedWalletCallback) {
                        when (val verifyRes = repository.verifyWalletDeposit(ref)) {
                            is com.example.data.repository.ApiResult.Success -> {
                                sessionViewModel.showMessage("Wallet deposit verified!")
                            }
                            is com.example.data.repository.ApiResult.Error -> {
                                sessionViewModel.showMessage(verifyRes.message)
                            }
                        }
                    }
                    if (liveUserId.isNotBlank()) {
                        walletVm.loadWallet(liveUserId)
                    }
                    navigateAuthRequired("wallet", singleTop = true)
                }
                normalizedPath.contains("/verify") -> {
                    navigateAuthRequired("verify_identity", singleTop = true)
                }
            }
            onDeepLinkConsumed()
        }
    }

    // Banned & Maintenance Guards
    var showMaintenanceAdminLogin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionState.isMaintenanceMode, sessionState.isAdmin) {
        if (!sessionState.isMaintenanceMode || sessionState.isAdmin) {
            showMaintenanceAdminLogin = false
        }
    }
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
        if (showMaintenanceAdminLogin && !sessionState.isLoggedIn) {
            LoginScreen(
                sessionViewModel = sessionViewModel,
                onLoginSuccess = { showMaintenanceAdminLogin = false },
                onNavigateSignUp = { showMaintenanceAdminLogin = false },
                onNavigateForgotPassword = { showMaintenanceAdminLogin = false },
                onBack = { showMaintenanceAdminLogin = false }
            )
        } else {
            StatusGuardScreen(
                asset = EmptyStateAsset.OFFLINE,
                title = "Kart is Under Maintenance",
                subtitle = "We're upgrading campus servers and will be back shortly.",
                actionLabel = "Check Again",
                onAction = { sessionViewModel.refreshPlatformFlags() },
                secondaryActionLabel = if (!sessionState.isLoggedIn) "Admin Sign In" else "Sign Out",
                onSecondaryAction = {
                    if (!sessionState.isLoggedIn) showMaintenanceAdminLogin = true
                    else sessionViewModel.signOut()
                }
            )
        }
        return
    }

    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val baseRoute = currentRoute.substringBefore("/").substringBefore("?")

    val topLevelTabRoutes = setOf("home", "marketplace", "messages", "profile")
    val showMainChrome = baseRoute in topLevelTabRoutes

    fun navigateTopTab(route: String) {
        if (route == "seller_create") {
            if (!sessionState.isLoggedIn) navController.navigate("login") { launchSingleTop = true }
            else navController.navigate("seller_create") { launchSingleTop = true }
            return
        }
        if (route == "profile" && !sessionState.isLoggedIn) {
            navController.navigate("login") { launchSingleTop = true }
            return
        }
        val liveBaseRoute = navController.currentDestination?.route
            ?.substringBefore("/")
            ?.substringBefore("?")
        if (liveBaseRoute == route && route != "marketplace") return
        if (route == "home") {
            val popped = navController.popBackStack("home", inclusive = false)
            if (!popped) {
                navController.navigate("home") {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                        saveState = false
                    }
                    launchSingleTop = true
                }
            }
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
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
                            onInfoPageClick = { slug -> navController.navigate("info/$slug") },
                            onShowMessage = sessionViewModel::showMessage
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
                                    buyNowEnabled = sessionState.buyNowEnabled,
                                    onRefreshPlatformFlags = sessionViewModel::refreshPlatformFlags,
                                    onBack = { navController.popBackStack() },
                                    onWishlistToggle = { id ->
                                        sessionViewModel.toggleWishlist(id) { navController.navigate("login") }
                                    },
                                    onRequireAuth = { navController.navigate("login") },
                                    onOpenChat = { convId, product ->
                                        messagesVm.stageProductAttachment(convId, product)
                                        navController.navigate("chat/$convId")
                                    },
                                    onBuyNow = { id ->
                                        if (sessionState.buyNowEnabled) {
                                            navController.navigate("checkout/$id")
                                        } else {
                                            sessionViewModel.showMessage("Buy Now is currently turned off. Please chat with the seller instead.")
                                        }
                                    },
                                    onSellerClick = { sid -> navController.navigate("public_profile/$sid") },
                                    onProductClick = { p -> navController.navigate("product/${p.id}") },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("checkout/{id}") { backStack ->
                                val pid = backStack.arguments?.getString("id").orEmpty()
                                LaunchedEffect(sessionState.isLoggedIn) {
                                    if (!sessionState.isLoggedIn) {
                                        sessionViewModel.showMessage("Please sign in to complete checkout.")
                                        navController.navigate("login") {
                                            popUpTo("checkout/{id}") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                if (sessionState.isLoggedIn) {
                                    CheckoutScreen(
                                        productId = pid,
                                        repository = repository,
                                        currentUserId = sessionState.session.userId.ifBlank { null },
                                        buyNowEnabled = sessionState.buyNowEnabled,
                                        onBack = { navController.popBackStack() },
                                        onOrderCreated = { orderId ->
                                            val uid = sessionState.session.userId
                                            if (uid.isNotBlank()) {
                                                walletVm.loadWallet(uid)
                                                ordersVm.loadOrders(uid)
                                            }
                                            navController.navigate("order_detail/$orderId") {
                                                popUpTo("checkout/{id}") { inclusive = true }
                                            }
                                        },
                                        onShowMessage = sessionViewModel::showMessage
                                    )
                                }
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
                                    campuses = campuses,
                                    onSignedUp = { immediate ->
                                        if (immediate) {
                                            navController.navigate("home") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = true
                                                    saveState = false
                                                }
                                                launchSingleTop = true
                                            }
                                        } else {
                                            navController.navigate("login") {
                                                popUpTo("signup") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
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
                                    blockedUserIds = sessionState.blockedUserIds,
                                    onBlockUser = { uid, done -> sessionViewModel.blockUser(uid, onDone = done) },
                                    onUnblockUser = { uid, done -> sessionViewModel.unblockUser(uid, onDone = done) },
                                    onBack = {
                                        sessionViewModel.refreshUnreadCounts()
                                        navController.popBackStack()
                                    },
                                    onProductClick = { pid -> navController.navigate("product/$pid") },
                                    onShowMessage = sessionViewModel::showMessage,
                                    onUserClick = { uid -> navController.navigate("public_profile/$uid") }
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
                                    profile = sessionState.profile,
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
                                    isVerified = sessionState.profile?.isVerified == true,
                                    verificationStatus = sessionState.profile?.verificationStatus,
                                    onBack = { navController.popBackStack() },
                                    onVerifyIdentity = { navController.navigate("verify_identity") },
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
                                    editProductId = null,
                                    campuses = campuses,
                                    viewModel = sellerVm,
                                    isVerified = sessionState.profile?.isVerified == true,
                                    verificationStatus = sessionState.profile?.verificationStatus,
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = { navController.navigate("login") },
                                    onVerifyIdentity = { navController.navigate("verify_identity") },
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
                                    editProductId = pid,
                                    campuses = campuses,
                                    viewModel = sellerVm,
                                    isVerified = sessionState.profile?.isVerified == true,
                                    verificationStatus = sessionState.profile?.verificationStatus,
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = { navController.navigate("login") },
                                    onVerifyIdentity = { navController.navigate("verify_identity") },
                                    onViewCreatedListing = { id ->
                                        navController.navigate("product/$id") { popUpTo("seller_edit/{id}") { inclusive = true } }
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
                                    boostRate = sessionState.promoBoostRate,
                                    featuredRate = sessionState.promoFeaturedRate,
                                    campusAdRate = sessionState.promoCampusAdRate,
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
                                        onOpenUserProfile = { uid -> navController.navigate("public_profile/$uid") },
                                        onShowMessage = sessionViewModel::showMessage
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
                                    blockedUserIds = sessionState.blockedUserIds,
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
                                    onOpenUserProfile = { nextUid -> navController.navigate("public_profile/$nextUid") },
                                    onBlockUser = { uid, done -> sessionViewModel.blockUser(uid, onDone = done) },
                                    onUnblockUser = { uid, done -> sessionViewModel.unblockUser(uid, onDone = done) },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            composable("profile_edit") {
                                EditProfileScreen(
                                    userId = sessionState.session.userId,
                                    initialProfile = sessionState.profile,
                                    campuses = campuses,
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
                                    onBack = {
                                        sessionViewModel.refreshUnreadCounts()
                                        navController.popBackStack()
                                    },
                                    onShowMessage = sessionViewModel::showMessage,
                                    onMarkedAllRead = { sessionViewModel.refreshUnreadCounts() },
                                    onNotificationClick = { notif ->
                                        sessionViewModel.refreshUnreadCounts()
                                        val rawLink = notif.link?.trim().orEmpty()
                                        val parsedUri = runCatching {
                                            if (rawLink.startsWith("http")) Uri.parse(rawLink)
                                            else Uri.parse("https://www.kart.cx/${rawLink.trimStart('/')}")
                                        }.getOrNull()
                                        val path = parsedUri?.path?.trimEnd('/').orEmpty()
                                        val segments = parsedUri?.pathSegments.orEmpty()
                                        val type = notif.type.orEmpty().lowercase()

                                        when {
                                            (path.startsWith("/marketplace/") || path.startsWith("/product/")) &&
                                                segments.size >= 2 && segments[1] != "categories" -> {
                                                navController.navigate("product/${Uri.encode(segments[1])}")
                                            }
                                            path.contains("/orders/") && segments.lastOrNull()?.isNotBlank() == true -> {
                                                navController.navigate("order_detail/${Uri.encode(segments.last())}")
                                            }
                                            path.contains("/seller/orders") -> navController.navigate("seller_orders")
                                            path.endsWith("/orders") -> navController.navigate("orders")
                                            (path.startsWith("/messages/") || path.startsWith("/chat/")) && segments.size >= 2 -> {
                                                navController.navigate("chat/${Uri.encode(segments[1])}")
                                            }
                                            path.startsWith("/messages") -> {
                                                val convId = parsedUri?.getQueryParameter("conversation")
                                                    ?: parsedUri?.getQueryParameter("id")
                                                if (!convId.isNullOrBlank()) navController.navigate("chat/${Uri.encode(convId)}")
                                                else navigateTopTab("messages")
                                            }
                                            path.startsWith("/profile/") && segments.size >= 2 -> {
                                                navController.navigate("public_profile/${Uri.encode(segments[1])}")
                                            }
                                            path == "/profile" -> navigateTopTab("profile")
                                            path.contains("/wallet") -> navController.navigate("wallet")
                                            path.contains("/subscription") -> navController.navigate("subscriptions")
                                            path.contains("/verify") -> navController.navigate("verify_identity")
                                            path.contains("/wishlist") -> navController.navigate("wishlist")
                                            path.startsWith("/seller") || path.startsWith("/dashboard") -> {
                                                navController.navigate("seller_dashboard")
                                            }
                                            type.contains("order") -> navController.navigate("orders")
                                            type.contains("message") || type.contains("chat") -> navigateTopTab("messages")
                                            type.contains("wallet") || type.contains("payout") || type.contains("withdraw") || type.contains("deposit") -> {
                                                navController.navigate("wallet")
                                            }
                                            type.contains("verify") || type.contains("kyc") -> navController.navigate("verify_identity")
                                            type.contains("follow") || type.contains("review") -> navigateTopTab("profile")
                                        }
                                    }
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
                                    sessionViewModel = sessionViewModel,
                                    repository = repository,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = sessionViewModel::showMessage
                                )
                            }

                            // --- Admin & Static Info ---
                            composable("admin") {
                                AdminDashboardScreen(
                                    isAdmin = sessionState.isAdmin,
                                    currentUserId = sessionState.session.userId,
                                    viewModel = adminVm,
                                    onBuyNowSettingChanged = sessionViewModel::applyBuyNowEnabled,
                                    onOpenProduct = { productId -> navController.navigate("product/$productId") },
                                    onOpenSeller = { sellerId -> navController.navigate("public_profile/$sellerId") },
                                    onOpenOrder = { orderId -> navController.navigate("order_detail/$orderId") },
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

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isOnline,
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    text = "You're offline. Check your connection.",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
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

internal data class PaymentCallbackParams(
    val orderId: String?,
    val subscriptionId: String?,
    val adId: String?,
    val payType: String?,
    val reference: String?
) {
    val isSubscription: Boolean
        get() = !subscriptionId.isNullOrBlank() || payType == "subscription"
    val isPromotion: Boolean
        get() = !adId.isNullOrBlank() || payType == "promotion"
    val isWallet: Boolean
        get() = payType == "wallet" ||
            reference?.startsWith("wdp_", ignoreCase = true) == true ||
            reference?.startsWith("wallet_dep_", ignoreCase = true) == true
}

private val ORDER_REF_REGEX = Regex("^order_(.+)_\\d+(?:_retry)?$", RegexOption.IGNORE_CASE)
private val SUB_REF_REGEX = Regex("^sub_(.+)_\\d+(?:_retry)?$", RegexOption.IGNORE_CASE)
private val AD_REF_REGEX = Regex("^ad_(.+)_\\d+(?:_retry)?$", RegexOption.IGNORE_CASE)

internal fun parsePaymentCallback(uri: Uri): PaymentCallbackParams {
    fun queryParam(vararg names: String): String? {
        for (name in names) {
            val value = uri.getQueryParameter(name)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    val reference = queryParam("reference", "trxref", "ref")
    val payType = queryParam("type")?.lowercase()
    val orderId = queryParam("orderId", "order_id")
        ?: reference?.let { ORDER_REF_REGEX.matchEntire(it)?.groupValues?.getOrNull(1) }
    val subscriptionId = queryParam("subscriptionId", "subscription_id")
        ?: reference?.let { SUB_REF_REGEX.matchEntire(it)?.groupValues?.getOrNull(1) }
    val adId = queryParam("adId", "promotionId", "ad_id")
        ?: reference?.let { AD_REF_REGEX.matchEntire(it)?.groupValues?.getOrNull(1) }

    return PaymentCallbackParams(
        orderId = orderId,
        subscriptionId = subscriptionId,
        adId = adId,
        payType = payType,
        reference = reference
    )
}

internal fun normalizeDeepLinkPath(uri: Uri): String {
    return if (uri.scheme.equals("kart-app", ignoreCase = true)) {
        val host = uri.host.orEmpty().trim('/')
        val rawPath = uri.path.orEmpty().trimStart('/')
        when {
            host.isBlank() && rawPath.isBlank() -> "/"
            host.isBlank() -> "/$rawPath"
            rawPath.isBlank() -> "/$host"
            else -> "/$host/$rawPath"
        }
    } else {
        uri.path.orEmpty().ifBlank { "/" }
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
