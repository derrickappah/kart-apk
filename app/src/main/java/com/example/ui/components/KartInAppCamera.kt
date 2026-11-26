package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import kotlinx.coroutines.delay
import java.io.File

private val KartLibraryAccent = Color(0xFF9333EA)

/**
 * Replicates Kart web repo PhotoSourceSheet (`2f13dcb2c3438853.js`):
 * - Header with "Add Photo" + available slots subtitle + close button
 * - Two option cards: "Take Photo" (Live in-app camera) and "Photo Library" (Upload from gallery)
 * - Full-width Cancel button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KartPhotoSourceSheet(
    remainingSlots: Int,
    onDismiss: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onUploadPhotoClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Add Photo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Select an option ($remainingSlots photo slot${if (remainingSlots == 1) "" else "s"} available)",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("photo_source_close_btn")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = KartLucideIcons.X,
                            contentDescription = "Close",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = tokens.cardBorder)

            PhotoSourceOptionCard(
                icon = KartLucideIcons.Camera,
                accentColor = KartPrimary,
                title = "Take Photo",
                subtitle = "Live in-app camera",
                testTag = "photo_option_take_picture",
                onClick = {
                    onDismiss()
                    onTakePhotoClick()
                }
            )

            PhotoSourceOptionCard(
                icon = KartLucideIcons.Image,
                accentColor = KartLibraryAccent,
                title = "Photo Library",
                subtitle = "Upload from gallery",
                testTag = "photo_option_upload_photo",
                onClick = {
                    onDismiss()
                    onUploadPhotoClick()
                }
            )

            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("photo_source_cancel_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceOptionCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tokens.cardBorder, RoundedCornerShape(18.dp))
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(23.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            Icon(
                imageVector = KartLucideIcons.ChevronRight,
                contentDescription = null,
                tint = tokens.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Replicates Kart web repo InAppCameraModal (`2016fc08ab8d68e9.js`) using native Android CameraX:
 * - Top status pill with pulsing cyan dot + "X / N Photos" counter & animated "Done (X)" pill
 * - Live captured thumbnail strip with "MAIN" / "#N" badges and red circular delete buttons
 * - Large rounded viewfinder card (34.dp) with live CameraX preview, loading & error fallback states
 * - Bottom floating controls: frosted Cancel (X), tactile Shutter ring, and Flip Camera (back/front)
 * - White shutter flash overlay + haptic feedback on each capture
 */
@Composable
fun KartInAppCameraDialog(
    maxPhotos: Int,
    existingPhotoCount: Int = 0,
    onDismiss: () -> Unit,
    onPhotosCaptured: (List<Uri>) -> Unit,
    onFallbackToGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val tokens = KartThemeTokens.colors

    var capturedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var retryTick by remember { mutableIntStateOf(0) }
    var isCameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var flashTick by remember { mutableIntStateOf(0) }
    var flipCount by remember { mutableIntStateOf(0) }
    val shutterFlashAlpha = remember { Animatable(0f) }
    val uiEaseOut = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // COMPATIBLE (TextureView) ensures clean 34.dp rounded corner clipping across devices
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Interruptible shutter flash scoped to the viewfinder well: instant peak, 180ms ease-out decay
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            shutterFlashAlpha.snapTo(0.85f)
            shutterFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = uiEaseOut)
            )
        }
    }

    // Bind CameraX Preview + ImageCapture to lifecycle whenever lensFacing or retryTick changes
    DisposableEffect(lifecycleOwner, lensFacing, retryTick) {
        isCameraReady = false
        cameraError = null
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        var disposed = false

        val bindListener = Runnable {
            if (disposed) return@Runnable
            runCatching {
                val provider = cameraProviderFuture.get()
                if (disposed) return@runCatching
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val preferredSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                val selector = when {
                    provider.hasCamera(preferredSelector) -> preferredSelector
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("No camera hardware found on this device.")
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
                isCameraReady = true
                cameraError = null
            }.onFailure { err ->
                isCameraReady = false
                cameraError = err.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unable to start camera stream. You can still upload from your gallery."
            }
        }

        cameraProviderFuture.addListener(bindListener, mainExecutor)

        onDispose {
            disposed = true
            runCatching { boundProvider?.unbindAll() }
            imageCapture = null
        }
    }

    fun captureShot() {
        val capture = imageCapture ?: return
        if (!isCameraReady || isCapturing || capturedUris.size >= maxPhotos) return
        isCapturing = true
        flashTick++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        val photoDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(photoDir, "kart_shot_${System.currentTimeMillis()}_${capturedUris.size + 1}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runCatching {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        capturedUris = (capturedUris + contentUri).take(maxPhotos)
                    }
                    isCapturing = false
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                }
            }
        )
    }

    val flipRotation by animateFloatAsState(
        targetValue = flipCount * 180f,
        animationSpec = tween(durationMillis = 260, easing = uiEaseOut),
        label = "cameraFlipRotation"
    )
    val isAtLimit = capturedUris.size >= maxPhotos

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
                // Quiet, unslopped Top Bar: title + subtle count on left, flat primary action on right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("camera_photo_counter")
                    ) {
                        Text(
                            text = "Take photo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "${capturedUris.size} / $maxPhotos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textSecondary
                        )
                    }

                    AnimatedVisibility(
                        visible = capturedUris.isNotEmpty(),
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.94f),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.94f)
                    ) {
                        Surface(
                            onClick = {
                                if (capturedUris.isNotEmpty()) {
                                    onPhotosCaptured(capturedUris)
                                }
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = KartPrimary,
                            contentColor = Color.White,
                            shadowElevation = 0.dp,
                            modifier = Modifier.testTag("camera_done_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Use ${capturedUris.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Captured Shots Thumbnail Strip (slides in after first photo)
                AnimatedVisibility(
                    visible = capturedUris.isNotEmpty(),
                    enter = fadeIn(tween(180)) + expandVertically(),
                    exit = fadeOut(tween(140)) + shrinkVertically()
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(capturedUris, key = { _, uri -> uri.toString() }) { idx, uri ->
                            val isMain = idx == 0 && existingPhotoCount == 0
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Captured shot ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isMain) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.56f))
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Cover",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable {
                                            capturedUris = capturedUris.filterIndexed { i, _ -> i != idx }
                                        }
                                        .testTag("camera_delete_shot_$idx")
                                ) {
                                    Icon(
                                        imageVector = KartLucideIcons.X,
                                        contentDescription = "Delete captured photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Recessed Live Viewfinder Well (inset bezel + inner vignette depth instead of drop shadow)
                val viewfinderShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(viewfinderShape)
                        .background(Color(0xFF0E0E11))
                        .border(1.dp, tokens.cardBorder, viewfinderShape)
                ) {
                    if (cameraError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(KartDanger.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    imageVector = KartLucideIcons.CameraOff,
                                    contentDescription = "Camera unavailable",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Camera Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = cameraError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Surface(
                                onClick = { retryTick++ },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.20f),
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Try Again",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onFallbackToGallery()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = KartPrimary,
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Upload from Gallery",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!isCameraReady) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                CircularProgressIndicator(
                                    color = KartPrimary,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Starting Camera...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Subtle inset depth vignette at top and bottom of the viewfinder well
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f))
                                )
                            )
                    )

                    // Viewfinder-only shutter flash (clipped to the rounded viewfinder well, beneath controls)
                    if (shutterFlashAlpha.value > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(shutterFlashAlpha.value)
                                .background(Color.White)
                        )
                    }

                    // Bottom Floating Controls: Cancel (X), Shutter Ring, Flip Camera
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 28.dp, bottom = 28.dp)
                    ) {
                        CameraFrostedCircleButton(
                            icon = KartLucideIcons.X,
                            contentDescription = "Cancel camera",
                            testTag = "camera_cancel_btn",
                            onClick = onDismiss
                        )

                        CameraShutterButton(
                            enabled = isCameraReady && !isAtLimit && !isCapturing,
                            atLimit = isAtLimit,
                            onClick = { captureShot() }
                        )

                        CameraFrostedCircleButton(
                            icon = KartLucideIcons.RefreshCw,
                            contentDescription = "Flip camera",
                            testTag = "camera_flip_btn",
                            iconRotation = flipRotation,
                            onClick = {
                                flipCount++
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                        )
                    }
                }
            }
        }
}

@Composable
private fun CameraFrostedCircleButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cameraCircleBtnScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun CameraShutterButton(
    enabled: Boolean,
    atLimit: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cameraShutterScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (atLimit) 0.40f else 1f
            }
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(6.dp)
            .testTag("camera_shutter_btn")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (atLimit) Color(0xFF9CA3AF) else Color.White)
        )
    }
}
