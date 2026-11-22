package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.ui.theme.KartPrimary
import kotlinx.coroutines.delay

val KartSplashRed = Color(0xFFFF0000)

/**
 * 1:1 native Jetpack Compose recreation of Kart's full-screen Lottie splash screen
 * (`R.raw.splash` on `#FF0000`, `ContentScale.Crop`, tap-to-skip, 350ms alpha fade-out).
 */
@Composable
fun KartSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compositionResult = rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.splash)
    )
    val composition = compositionResult.value

    var isDismissing by remember { mutableStateOf(false) }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = composition != null && !isDismissing,
        restartOnPlay = false
    )

    // Dismiss automatically when the 2-second Lottie animation finishes
    LaunchedEffect(composition, progress) {
        if (composition != null && progress >= 0.999f) {
            isDismissing = true
        }
    }

    // Safety fallback if composition fails or stalls
    LaunchedEffect(compositionResult.isFailure) {
        if (compositionResult.isFailure) {
            isDismissing = true
        }
    }
    LaunchedEffect(Unit) {
        delay(4500L)
        isDismissing = true
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0f else 1f,
        animationSpec = tween(durationMillis = 350),
        finishedListener = { finalAlpha ->
            if (finalAlpha <= 0.01f && isDismissing) {
                onFinished()
            }
        },
        label = "kartSplashAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(KartSplashRed)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isDismissing = true
            }
            .semantics { contentDescription = "Kart splash screen, tap to skip" }
            .testTag("kart_splash_overlay")
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 1:1 recreation of Kart's `LoadingScreen.js` (`R.raw.kartloading` with subtle brand glow).
 */
@Composable
fun KartLottieLoadingView(
    modifier: Modifier = Modifier,
    size: Dp = 144.dp
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.kartloading)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("kart_lottie_loader")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size)
        ) {
            Box(
                modifier = Modifier
                    .size(size - 32.dp)
                    .clip(CircleShape)
                    .background(KartPrimary.copy(alpha = 0.10f))
            )
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
