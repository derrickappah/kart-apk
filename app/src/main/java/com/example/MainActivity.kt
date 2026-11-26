package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.KartSplashOverlay
import com.example.ui.navigation.KartAppRoot
import com.example.ui.theme.KartTheme
import com.example.ui.viewmodel.KartViewModelFactory
import com.example.ui.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<Uri?>(null)
    private var showSplash by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ SplashScreen and immediately hand off to Kart's Lottie splash
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        showSplash = savedInstanceState == null
        if (showSplash) {
            applySplashSystemBars(immersive = true)
        }

        if (savedInstanceState == null) {
            pendingDeepLink = extractDeepLink(intent)
        }

        val repository = (application as KartApplication).container.repository

        setContent {
            val sessionViewModel: SessionViewModel = viewModel(
                factory = KartViewModelFactory(repository)
            )
            val themePreference by sessionViewModel.themePreference.collectAsStateWithLifecycle()

            LaunchedEffect(showSplash) {
                applySplashSystemBars(immersive = showSplash)
            }

            KartTheme(themePreference = themePreference) {
                Box(modifier = Modifier.fillMaxSize()) {
                    KartAppRoot(
                        repository = repository,
                        sessionViewModel = sessionViewModel,
                        deepLinkUri = pendingDeepLink,
                        onDeepLinkConsumed = {
                            pendingDeepLink = null
                            intent?.data = null
                            intent?.removeExtra("deep_link")
                            intent?.removeExtra("link")
                        }
                    )

                    if (showSplash) {
                        KartSplashOverlay(
                            onFinished = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (showSplash) {
            applySplashSystemBars(immersive = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?): Uri? {
        intent?.data?.let { return it }
        val extraLink = intent?.getStringExtra("deep_link") ?: intent?.getStringExtra("link")
        if (extraLink.isNullOrBlank()) return null
        val normalized = if (extraLink.startsWith("http://") || extraLink.startsWith("https://") || extraLink.startsWith("kart-app://")) {
            extraLink
        } else {
            "https://www.kart.cx/${extraLink.trimStart('/')}"
        }
        return runCatching { Uri.parse(normalized) }.getOrNull()
    }

    private fun applySplashSystemBars(immersive: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        if (immersive) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
