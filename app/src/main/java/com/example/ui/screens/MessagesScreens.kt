package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.ui.components.ReportDialog
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import com.example.ui.theme.KartDanger
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.EnrichedConversation
import com.example.domain.model.KartFormatters
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartSearchBar
import com.example.ui.components.ShimmerBox
import com.example.ui.components.UserAvatar
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.MessagesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MessagesInboxScreen(
    userId: String?,
    viewModel: MessagesViewModel,
    onConversationClick: (String) -> Unit,
    onRequireAuth: () -> Unit,
    onExploreShop: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRealtimeConnected by viewModel.isRealtimeConnected.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    var search by remember { mutableStateOf("") }

    if (userId.isNullOrBlank()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            EmptyStateView(
                asset = EmptyStateAsset.MESSAGES,
                title = "Sign in to view campus chats",
                subtitle = "Message buyers and sellers directly to negotiate and arrange campus meetups.",
                actionLabel = "Sign In",
                onActionClick = onRequireAuth
            )
        }
        return
    }

    DisposableEffect(userId) {
        viewModel.acquireRealtime(userId)
        onDispose { viewModel.releaseRealtime() }
    }

    LaunchedEffect(userId) {
        viewModel.loadConversations(userId)
    }

    LaunchedEffect(userId, isRealtimeConnected) {
        if (!isRealtimeConnected) {
            while (true) {
                delay(60_000L)
                viewModel.loadConversations(userId)
            }
        }
    }

    val filtered = remember(state.conversations, search) {
        if (search.isBlank()) state.conversations
        else state.conversations.filter {
            it.otherUser.resolvedName.contains(search, ignoreCase = true) ||
                it.product?.title?.contains(search, ignoreCase = true) == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
            var isSearchFocused by remember { mutableStateOf(false) }
            val borderColor by animateColorAsState(
                targetValue = if (isSearchFocused) KartPrimary else tokens.cardBorder,
                animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                label = "chatSearchBorder"
            )
            val searchIconColor by animateColorAsState(
                targetValue = if (isSearchFocused) KartPrimary else tokens.textMuted,
                animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
                label = "chatSearchIcon"
            )
            val pillShape = RoundedCornerShape(26.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        elevation = if (isSearchFocused) 5.dp else 2.dp,
                        shape = pillShape,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSearchFocused) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = pillShape
                    )
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = KartLucideIcons.Search,
                    contentDescription = "Search chats",
                    tint = searchIconColor,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(10.dp))

                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f)
                ) {
                    if (search.isEmpty()) {
                        Text(
                            text = "Search chats or listings...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textMuted,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = tokens.textPrimary
                        ),
                        cursorBrush = SolidColor(KartPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused }
                    )
                }

                AnimatedVisibility(
                    visible = search.isNotEmpty(),
                    enter = fadeIn(tween(140, easing = easeOutStrong)) +
                        scaleIn(initialScale = 0.82f, animationSpec = tween(140, easing = easeOutStrong)),
                    exit = fadeOut(tween(110, easing = easeOutStrong)) +
                        scaleOut(targetScale = 0.82f, animationSpec = tween(110, easing = easeOutStrong))
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { search = "" }
                    ) {
                        Icon(
                            imageVector = KartLucideIcons.X,
                            contentDescription = "Clear search",
                            tint = tokens.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        when {
            state.loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    repeat(5) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp)) }
                }
            }
            state.error != null && state.conversations.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.MESSAGES,
                    title = "Unable to load messages",
                    subtitle = state.error ?: "Check your connection and try again.",
                    actionLabel = "Retry",
                    onActionClick = { viewModel.loadConversations(userId) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            filtered.isEmpty() && search.isNotBlank() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.SEARCH,
                    title = "No matching chats",
                    subtitle = "No conversations match \"$search\". Try a different name or item title.",
                    actionLabel = "Clear Search",
                    onActionClick = { search = "" },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            filtered.isEmpty() -> {
                EmptyStateView(
                    asset = EmptyStateAsset.MESSAGES,
                    title = "No messages yet",
                    subtitle = "Tap \"Chat\" on any campus listing to ask questions or negotiate a deal.",
                    actionLabel = "Explore Listings",
                    onActionClick = onExploreShop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        Column(
                            modifier = Modifier.animateItem(
                                placementSpec = tween(
                                    durationMillis = 260,
                                    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                                )
                            )
                        ) {
                            ConversationRowItem(
                                conversation = conv,
                                isOnline = conv.otherUser.id in state.onlineUserIds,
                                isTyping = conv.otherUser.id in state.typingUsersByConversation[conv.id].orEmpty(),
                                onClick = { onConversationClick(conv.id) }
                            )
                            HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(start = 82.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRowItem(
    conversation: EnrichedConversation,
    isOnline: Boolean = false,
    isTyping: Boolean = false,
    onClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Box {
            UserAvatar(
                avatarUrl = conversation.otherUser.avatarUrl,
                displayName = conversation.otherUser.resolvedName,
                size = 50.dp,
                isVerified = conversation.otherUser.isVerified == true
            )
            OnlineDotBadge(
                visible = isOnline,
                borderColor = MaterialTheme.colorScheme.background,
                dotSize = 14.dp,
                modifier = Modifier.align(
                    if (conversation.otherUser.isVerified == true) Alignment.TopEnd else Alignment.BottomEnd
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = conversation.otherUser.resolvedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = KartFormatters.formatChatTime(conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) KartPrimary else tokens.textMuted
                )
            }
            if (conversation.product != null) {
                Text(
                    text = conversation.product.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = KartPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTyping) "Typing..." else KartFormatters.messageSnippet(conversation.lastMessage?.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isTyping -> KartPrimary
                        conversation.unreadCount > 0 -> tokens.textPrimary
                        else -> tokens.textSecondary
                    },
                    fontWeight = if (isTyping || conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(KartPrimary)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${conversation.unreadCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatThreadScreen(
    conversationId: String,
    currentUserId: String,
    viewModel: MessagesViewModel,
    blockedUserIds: Set<String> = emptySet(),
    onBlockUser: (String, () -> Unit) -> Unit = { _, _ -> },
    onUnblockUser: (String, () -> Unit) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRealtimeConnected by viewModel.isRealtimeConnected.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threadScope = androidx.compose.runtime.rememberCoroutineScope()
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val listState = rememberLazyListState()
    var messageInput by remember { mutableStateOf("") }
    var replyingTo by remember(conversationId) { mutableStateOf<ReplyDraft?>(null) }
    var highlightedMessageId by remember(conversationId) { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val conv = state.conversations.firstOrNull { it.id == conversationId }
    val otherUserId = conv?.otherUser?.id.orEmpty()
    val isOtherBlocked = otherUserId.isNotBlank() && otherUserId in blockedUserIds
    val isOtherOnline = !isOtherBlocked && otherUserId.isNotBlank() && otherUserId in state.onlineUserIds
    val isOtherTyping = !isOtherBlocked && otherUserId.isNotBlank() &&
        otherUserId in state.typingUsersByConversation[conversationId].orEmpty()
    val attachedProduct = state.pendingProductAttachment?.takeIf { it.first == conversationId }?.second

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageMessage(conversationId, currentUserId, context.contentResolver, uri)
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var recorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    var playingUrl by remember { mutableStateOf<String?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackRemainingSec by remember { mutableStateOf<Int?>(null) }

    fun stopPlayback() {
        val mp = mediaPlayerRef
        mediaPlayerRef = null
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        playingUrl = null
        playbackProgress = 0f
        playbackRemainingSec = null
    }

    fun toggleVoicePlayback(url: String) {
        if (playingUrl == url) {
            stopPlayback()
            return
        }
        stopPlayback()
        var mp: MediaPlayer? = null
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onShowMessage("Could not play voice message.")
                    true
                }
                prepareAsync()
            }
            mediaPlayerRef = mp
            playingUrl = url
        } catch (_: Exception) {
            if (mediaPlayerRef !== mp) {
                runCatching { mp?.release() }
            }
            stopPlayback()
            onShowMessage("Could not play voice message.")
        }
    }

    fun startRecording() {
        stopPlayback()
        val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        var rec: MediaRecorder? = null
        try {
            @Suppress("DEPRECATION")
            rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorderRef = rec
            recordingFile = outFile
            recordingSeconds = 0
            isRecording = true
        } catch (_: Exception) {
            if (recorderRef !== rec) {
                runCatching { rec?.release() }
            }
            runCatching { outFile.delete() }
            isRecording = false
            onShowMessage("Could not start voice recording.")
        }
    }

    fun stopRecording(send: Boolean) {
        val rec = recorderRef
        val file = recordingFile
        val dur = recordingSeconds.coerceAtLeast(1)
        recorderRef = null
        recordingFile = null
        isRecording = false
        recordingSeconds = 0
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        if (send && file != null && file.exists() && file.length() > 0L) {
            viewModel.sendVoiceMessage(conversationId, currentUserId, file, dur)
        } else {
            runCatching { file?.delete() }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else onShowMessage("Microphone permission is required to send voice messages.")
    }

    LaunchedEffect(playingUrl) {
        while (playingUrl != null) {
            val mp = mediaPlayerRef
            if (mp != null) {
                runCatching {
                    if (mp.isPlaying) {
                        val total = mp.duration.coerceAtLeast(1)
                        val pos = mp.currentPosition.coerceIn(0, total)
                        playbackProgress = (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        playbackRemainingSec = ((total - pos + 999) / 1000).coerceAtLeast(0)
                    }
                }
            }
            delay(80L)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            recordingSeconds += 1
            if (recordingSeconds >= 120) {
                stopRecording(send = true)
            }
        }
    }

    DisposableEffect(conversationId) {
        onDispose {
            viewModel.leaveConversation(conversationId, currentUserId)
            viewModel.clearPendingProductAttachment(conversationId)
            stopPlayback()
            val rec = recorderRef
            recorderRef = null
            runCatching { rec?.stop() }
            runCatching { rec?.release() }
            runCatching { recordingFile?.delete() }
        }
    }

    DisposableEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            viewModel.acquireRealtime(currentUserId)
        }
        onDispose {
            if (currentUserId.isNotBlank()) {
                viewModel.releaseRealtime()
            }
        }
    }

    LaunchedEffect(conversationId, currentUserId) {
        viewModel.enterConversation(conversationId)
        if (conv == null && currentUserId.isNotBlank()) {
            viewModel.loadConversations(currentUserId)
        }
        viewModel.loadThread(conversationId, silent = false)
    }

    LaunchedEffect(conversationId, isRealtimeConnected) {
        if (!isRealtimeConnected) {
            while (true) {
                delay(45_000L)
                viewModel.loadThread(conversationId, silent = true)
            }
        }
    }

    var prevCount by remember(conversationId) { mutableStateOf(0) }
    LaunchedEffect(state.activeMessages.size, conversationId) {
        val count = state.activeMessages.size
        if (count > prevCount && prevCount > 0) {
            listState.animateScrollToItem(0)
        }
        prevCount = count
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            onShowMessage(it)
            viewModel.clearFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.chatBackground)
            .imePadding()
    ) {
        // Chat Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = tokens.textPrimary,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            KartLucideIcons.ArrowLeft,
                            contentDescription = "Back",
                            tint = tokens.textPrimary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !conv?.otherUser?.id.isNullOrBlank()) {
                                conv?.otherUser?.id?.let(onUserClick)
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Box {
                            UserAvatar(
                                avatarUrl = conv?.otherUser?.avatarUrl,
                                displayName = conv?.otherUser?.resolvedName ?: "Campus Chat",
                                size = 38.dp,
                                isVerified = conv?.otherUser?.isVerified == true
                            )
                            OnlineDotBadge(
                                visible = isOtherOnline,
                                borderColor = MaterialTheme.colorScheme.background,
                                dotSize = 12.dp,
                                reduceMotion = reduceMotion,
                                modifier = Modifier.align(
                                    if (conv?.otherUser?.isVerified == true) Alignment.TopEnd else Alignment.BottomEnd
                                )
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv?.otherUser?.resolvedName ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val campusLabel = conv?.otherUser?.campus?.takeIf { it.isNotBlank() }
                            val statusMode = when {
                                isOtherTyping -> "typing"
                                isOtherOnline -> "online"
                                campusLabel != null -> "campus"
                                else -> "none"
                            }
                            val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
                            AnimatedContent(
                                targetState = statusMode to campusLabel,
                                transitionSpec = {
                                    fadeIn(tween(160, easing = easeOutStrong)) togetherWith
                                        fadeOut(tween(120, easing = easeOutStrong))
                                },
                                label = "chatHeaderStatus"
                            ) { (mode, campus) ->
                                when (mode) {
                                    "typing" -> Text(
                                        text = "Typing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KartPrimary
                                    )
                                    "online" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Online",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = KartSuccess
                                        )
                                        if (campus != null) {
                                            Text(
                                                text = " · $campus",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    "campus" -> Text(
                                        text = campus.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    else -> Spacer(Modifier.height(0.dp))
                                }
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showHeaderMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = tokens.textPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "View Profile",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        KartLucideIcons.User,
                                        contentDescription = null,
                                        tint = tokens.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    conv?.otherUser?.id?.let(onUserClick)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Report User",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = KartDanger
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        KartLucideIcons.Flag,
                                        contentDescription = null,
                                        tint = KartDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    if (!conv?.otherUser?.id.isNullOrBlank()) {
                                        showReportDialog = true
                                    }
                                }
                            )
                            if (otherUserId.isNotBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isOtherBlocked) "Unblock User" else "Block User",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isOtherBlocked) tokens.textPrimary else KartDanger
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            KartLucideIcons.Ban,
                                            contentDescription = null,
                                            tint = if (isOtherBlocked) tokens.textPrimary else KartDanger,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        if (isOtherBlocked) {
                                            onUnblockUser(otherUserId) {}
                                        } else {
                                            showBlockDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (conv?.product != null && attachedProduct == null) {
                    HorizontalDivider(color = tokens.cardBorder)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProductClick(conv.product.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = conv.product.primaryImage,
                            contentDescription = conv.product.title,
                            placeholder = painterResource(R.drawable.img_placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                conv.product.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                KartFormatters.formatCedis(conv.product.price),
                                style = MaterialTheme.typography.labelMedium,
                                color = KartPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Messages List (anchored to newest message at index 0 from frame 1)
        val reversedMessages = remember(state.activeMessages) { state.activeMessages.asReversed() }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(
                reversedMessages,
                key = { index, it -> it.id ?: "anon_${index}_${it.createdAt}_${it.content.hashCode()}" }
            ) { index, msg ->
                val isMine = msg.senderId == currentUserId
                val isPending = msg.id?.startsWith("local_") == true
                val replyPayload = KartFormatters.extractMessageReply(msg.content)
                val bodyContent = replyPayload?.body ?: msg.content
                val voicePayload = KartFormatters.extractMessageVoice(bodyContent)
                val imageUrl = if (voicePayload == null) KartFormatters.extractMessageImageUrl(bodyContent) else null
                val productPayload = if (voicePayload == null && imageUrl == null) {
                    KartFormatters.extractMessageProduct(bodyContent)
                } else null
                val formattedTime = KartFormatters.formatChatTime(msg.createdAt)
                val newerMsg = reversedMessages.getOrNull(index - 1)
                val isLastInGroup = newerMsg == null ||
                    newerMsg.senderId != msg.senderId ||
                    KartFormatters.formatChatTime(newerMsg.createdAt) != formattedTime

                val enterProgress = remember(msg.id) { Animatable(if (isPending && !reduceMotion) 0f else 1f) }
                LaunchedEffect(msg.id) {
                    if (enterProgress.value < 1f) {
                        enterProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 190,
                                easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                            )
                        )
                    }
                }
                val deliveryAlpha by animateFloatAsState(
                    targetValue = if (isPending) 0.72f else 1f,
                    animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                    label = "msgDeliveryAlpha"
                )

                val dragOffsetX = remember(msg.id) { Animatable(0f) }
                var thresholdHapticFired by remember(msg.id) { mutableStateOf(false) }
                val swipeThresholdPx = with(density) { 46.dp.toPx() }
                val maxSwipePx = with(density) { 68.dp.toPx() }
                val swipeProgress = (dragOffsetX.value / swipeThresholdPx).coerceIn(0f, 1f)

                val isHighlighted = !msg.id.isNullOrBlank() && msg.id == highlightedMessageId
                val highlightAlpha by animateFloatAsState(
                    targetValue = if (isHighlighted) 0.16f else 0f,
                    animationSpec = tween(
                        durationMillis = if (isHighlighted) 160 else 280,
                        easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                    ),
                    label = "replyJumpHighlight"
                )

                fun triggerReplyForCurrentMessage() {
                    val targetId = msg.id ?: return
                    if (targetId.startsWith("local_")) return
                    val senderLabel = if (isMine) "You" else (conv?.otherUser?.resolvedName ?: "Seller")
                    val snippet = KartFormatters.messageSnippet(msg.content)
                    replyingTo = ReplyDraft(
                        messageId = targetId,
                        senderName = senderLabel,
                        snippet = snippet
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLastInGroup) 10.dp else 0.dp)
                        .background(KartPrimary.copy(alpha = highlightAlpha), RoundedCornerShape(14.dp))
                        .pointerInput(msg.id, reduceMotion) {
                            detectHorizontalDragGestures(
                                onDragStart = { thresholdHapticFired = false },
                                onDragEnd = {
                                    val triggered = dragOffsetX.value >= swipeThresholdPx
                                    if (triggered) {
                                        triggerReplyForCurrentMessage()
                                    }
                                    threadScope.launch {
                                        if (reduceMotion) {
                                            dragOffsetX.snapTo(0f)
                                        } else {
                                            dragOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    threadScope.launch {
                                        if (reduceMotion) {
                                            dragOffsetX.snapTo(0f)
                                        } else {
                                            dragOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val current = dragOffsetX.value
                                    val resistance = if (current > swipeThresholdPx) 0.35f else 0.85f
                                    val next = (current + dragAmount * resistance).coerceIn(0f, maxSwipePx)
                                    if (!thresholdHapticFired && next >= swipeThresholdPx) {
                                        thresholdHapticFired = true
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    threadScope.launch { dragOffsetX.snapTo(next) }
                                }
                            )
                        }
                ) {
                    if (swipeProgress > 0.02f) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(30.dp)
                                .graphicsLayer {
                                    alpha = swipeProgress
                                    val iconScale = if (reduceMotion) 1f else (0.85f + 0.15f * swipeProgress)
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .clip(CircleShape)
                                .background(
                                    if (swipeProgress >= 1f) KartPrimary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = ReplyArrowIcon,
                                contentDescription = "Reply",
                                tint = if (swipeProgress >= 1f) Color.White else tokens.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                Row(
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = enterProgress.value
                            alpha = p * deliveryAlpha
                            translationX = dragOffsetX.value
                            translationY = if (reduceMotion) 0f else (1f - p) * 18f
                            val s = if (reduceMotion) 1f else (0.94f + (0.06f * p))
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    Column(
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                        modifier = Modifier.widthIn(max = 285.dp)
                    ) {
                        val bubbleShape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine || !isLastInGroup) 18.dp else 4.dp,
                            bottomEnd = if (!isMine || !isLastInGroup) 18.dp else 4.dp
                        )
                        Box(
                            modifier = Modifier
                                .clip(bubbleShape)
                                .background(if (isMine) KartPrimary else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = if (isMine) 0.dp else 1.dp,
                                    color = if (isMine) Color.Transparent else tokens.cardBorder,
                                    shape = bubbleShape
                                )
                                .padding(if (imageUrl != null && replyPayload == null) 4.dp else 12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (replyPayload != null) {
                                    QuotedReplyBubbleBlock(
                                        senderName = replyPayload.senderName,
                                        snippet = replyPayload.snippet,
                                        isMine = isMine,
                                        onClick = {
                                            val targetIdx = reversedMessages.indexOfFirst { it.id == replyPayload.replyToId }
                                            if (targetIdx >= 0) {
                                                threadScope.launch {
                                                    if (reduceMotion) {
                                                        listState.scrollToItem(targetIdx)
                                                    } else {
                                                        listState.animateScrollToItem(targetIdx)
                                                    }
                                                    highlightedMessageId = replyPayload.replyToId
                                                    delay(650L)
                                                    if (highlightedMessageId == replyPayload.replyToId) {
                                                        highlightedMessageId = null
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            if (voicePayload != null) {
                                val isPlaying = playingUrl == voicePayload.url
                                VoiceMessageBubbleContent(
                                    isMine = isMine,
                                    isPlaying = isPlaying,
                                    progress = if (isPlaying) playbackProgress else 0f,
                                    displaySeconds = if (isPlaying) {
                                        playbackRemainingSec ?: voicePayload.durationSeconds
                                    } else {
                                        voicePayload.durationSeconds
                                    },
                                    onTogglePlay = { toggleVoicePlayback(voicePayload.url) }
                                )
                            } else if (imageUrl != null) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Shared photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .widthIn(min = 120.dp, max = 250.dp)
                                        .heightIn(max = 320.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { previewImageUrl = imageUrl }
                                )
                            } else if (productPayload != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isMine) Color.White.copy(alpha = 0.16f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                            )
                                            .clickable { onProductClick(productPayload.productId) }
                                            .padding(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = productPayload.imageUrl,
                                            contentDescription = productPayload.title,
                                            placeholder = painterResource(R.drawable.img_placeholder),
                                            error = painterResource(R.drawable.img_placeholder),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = productPayload.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isMine) Color.White else tokens.textPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = KartFormatters.formatCedis(productPayload.price),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMine) Color.White.copy(alpha = 0.92f) else KartPrimary
                                            )
                                        }
                                    }
                                    if (productPayload.caption.isNotBlank()) {
                                        Text(
                                            text = productPayload.caption,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isMine) Color.White else tokens.textPrimary
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = bodyContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) Color.White else tokens.textPrimary
                                )
                            }
                            }
                        }
                        if (isLastInGroup && formattedTime.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = tokens.textMuted,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                }
            }
        }

        // Floating Message Composer
        val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }

        AnimatedVisibility(
            visible = isOtherTyping,
            enter = if (reduceMotion) {
                fadeIn(tween(150, easing = easeOutStrong))
            } else {
                fadeIn(tween(180, easing = easeOutStrong)) +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(180, easing = easeOutStrong)
                    )
            },
            exit = if (reduceMotion) {
                fadeOut(tween(110, easing = easeOutStrong))
            } else {
                fadeOut(tween(130, easing = easeOutStrong)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(130, easing = easeOutStrong)
                    )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            TypingIndicatorBubble(
                senderName = conv?.otherUser?.resolvedName,
                reduceMotion = reduceMotion
            )
        }

        val actionInteraction = remember { MutableInteractionSource() }
        val actionPressed by actionInteraction.collectIsPressedAsState()
        val actionScale by animateFloatAsState(
            targetValue = if (actionPressed) 0.93f else 1f,
            animationSpec = tween(durationMillis = 120, easing = easeOutStrong),
            label = "composerActionScale"
        )
        val capsuleShape = RoundedCornerShape(28.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    (fadeIn(tween(180, easing = easeOutStrong)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(180, easing = easeOutStrong)))
                        .togetherWith(
                            fadeOut(tween(130, easing = easeOutStrong)) +
                                scaleOut(targetScale = 0.97f, animationSpec = tween(130, easing = easeOutStrong))
                        )
                },
                label = "composerModeTransition"
            ) { recording ->
                if (recording) {
                    val recPulseTransition = rememberInfiniteTransition(label = "recDotPulse")
                    val recDotAlpha by recPulseTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "recDotAlpha"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 4.dp,
                                shape = capsuleShape,
                                ambientColor = Color.Black.copy(alpha = 0.10f),
                                spotColor = Color.Black.copy(alpha = 0.14f)
                            )
                            .clip(capsuleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, KartDanger.copy(alpha = 0.35f), capsuleShape)
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        IconButton(
                            onClick = { stopRecording(send = false) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KartDanger.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                KartLucideIcons.Trash2,
                                contentDescription = "Cancel recording",
                                tint = KartDanger,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer { alpha = recDotAlpha }
                                .clip(CircleShape)
                                .background(KartDanger)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Recording",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = KartFormatters.formatDuration(recordingSeconds),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = KartDanger,
                            modifier = Modifier.padding(end = 10.dp)
                        )

                        IconButton(
                            onClick = { stopRecording(send = true) },
                            interactionSource = actionInteraction,
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = actionScale
                                    scaleY = actionScale
                                }
                                .clip(CircleShape)
                                .background(KartPrimary)
                                .testTag("chat_voice_send_btn")
                        ) {
                            Icon(
                                KartLucideIcons.Send,
                                contentDescription = "Send voice message",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (isOtherBlocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, tokens.cardBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "You blocked this user. Unblock to send messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Unblock",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = KartPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUnblockUser(otherUserId) {} }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    val canSend = messageInput.isNotBlank() || attachedProduct != null
                    val hasComposerBanner = attachedProduct != null || replyingTo != null
                    val composerShape = RoundedCornerShape(if (hasComposerBanner) 22.dp else 28.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 4.dp,
                                shape = composerShape,
                                ambientColor = Color.Black.copy(alpha = 0.10f),
                                spotColor = Color.Black.copy(alpha = 0.14f)
                            )
                            .clip(composerShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, tokens.cardBorder, composerShape)
                    ) {
                        AnimatedVisibility(
                            visible = replyingTo != null,
                            enter = if (reduceMotion) {
                                fadeIn(tween(150, easing = easeOutStrong))
                            } else {
                                fadeIn(tween(180, easing = easeOutStrong)) +
                                    expandVertically(tween(180, easing = easeOutStrong))
                            },
                            exit = if (reduceMotion) {
                                fadeOut(tween(110, easing = easeOutStrong))
                            } else {
                                fadeOut(tween(130, easing = easeOutStrong)) +
                                    shrinkVertically(tween(130, easing = easeOutStrong))
                            }
                        ) {
                            replyingTo?.let { draft ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(KartPrimary)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Replying to ${draft.senderName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = KartPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = draft.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { replyingTo = null },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("chat_cancel_reply_btn")
                                        ) {
                                            Icon(
                                                imageVector = KartLucideIcons.X,
                                                contentDescription = "Cancel reply",
                                                tint = tokens.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = tokens.cardBorder)
                                }
                            }
                        }

                        AnimatedVisibility(visible = attachedProduct != null) {
                            attachedProduct?.let { product ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                                    ) {
                                        AsyncImage(
                                            model = product.primaryImage,
                                            contentDescription = product.title,
                                            placeholder = painterResource(R.drawable.img_placeholder),
                                            error = painterResource(R.drawable.img_placeholder),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onProductClick(product.id) }
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onProductClick(product.id) }
                                        ) {
                                            Text(
                                                text = product.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = tokens.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = KartFormatters.formatCedis(product.price),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = KartPrimary
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.clearPendingProductAttachment(conversationId) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("chat_remove_product_btn")
                                        ) {
                                            Icon(
                                                imageVector = KartLucideIcons.X,
                                                contentDescription = "Remove attached item",
                                                tint = tokens.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = tokens.cardBorder)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    KartLucideIcons.Image,
                                    contentDescription = "Send photo",
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp)
                            ) {
                                if (messageInput.isEmpty()) {
                                    Text(
                                        text = if (attachedProduct != null) "Add a message about this item..." else "Message...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = messageInput,
                                    onValueChange = {
                                        messageInput = it
                                        viewModel.onTypingInputChanged(conversationId, currentUserId, it)
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = tokens.textPrimary
                                    ),
                                    cursorBrush = SolidColor(KartPrimary),
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_message_input")
                                )
                            }

                            Spacer(Modifier.width(6.dp))

                            IconButton(
                                enabled = !state.sending,
                                interactionSource = actionInteraction,
                                onClick = {
                                    if (attachedProduct != null) {
                                        val caption = messageInput.trim()
                                        val prevReply = replyingTo
                                        messageInput = ""
                                        replyingTo = null
                                        viewModel.stopTyping(conversationId, currentUserId)
                                        viewModel.sendProductMessage(
                                            conversationId = conversationId,
                                            senderId = currentUserId,
                                            product = attachedProduct,
                                            caption = caption,
                                            onFailure = {
                                                if (messageInput.isBlank() && caption.isNotBlank()) {
                                                    messageInput = caption
                                                }
                                                if (replyingTo == null && prevReply != null) {
                                                    replyingTo = prevReply
                                                }
                                            }
                                        )
                                    } else if (messageInput.isNotBlank()) {
                                        val toSend = messageInput.trim()
                                        val activeReply = replyingTo
                                        messageInput = ""
                                        replyingTo = null
                                        viewModel.stopTyping(conversationId, currentUserId)
                                        val restoreDraft: () -> Unit = {
                                            if (messageInput.isBlank()) messageInput = toSend
                                            if (replyingTo == null && activeReply != null) replyingTo = activeReply
                                        }
                                        if (activeReply != null) {
                                            viewModel.sendReplyMessage(
                                                conversationId = conversationId,
                                                senderId = currentUserId,
                                                replyToId = activeReply.messageId,
                                                replySenderName = activeReply.senderName,
                                                replySnippet = activeReply.snippet,
                                                text = toSend,
                                                onFailure = restoreDraft
                                            )
                                        } else {
                                            viewModel.sendMessage(
                                                conversationId = conversationId,
                                                senderId = currentUserId,
                                                text = toSend,
                                                onFailure = restoreDraft
                                            )
                                        }
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) startRecording()
                                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = actionScale
                                        scaleY = actionScale
                                    }
                                    .clip(CircleShape)
                                    .background(KartPrimary)
                                    .testTag(if (canSend) "chat_send_btn" else "chat_mic_btn")
                            ) {
                                AnimatedContent(
                                    targetState = canSend,
                                    transitionSpec = {
                                        (fadeIn(tween(150, easing = easeOutStrong)) +
                                            scaleIn(initialScale = 0.84f, animationSpec = tween(150, easing = easeOutStrong)))
                                            .togetherWith(
                                                fadeOut(tween(110, easing = easeOutStrong)) +
                                                    scaleOut(targetScale = 0.84f, animationSpec = tween(110, easing = easeOutStrong))
                                            )
                                    },
                                    label = "composerActionIcon"
                                ) { showSend ->
                                    if (showSend) {
                                        Icon(
                                            KartLucideIcons.Send,
                                            contentDescription = "Send message",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            VoiceMicIcon,
                                            contentDescription = "Record voice message",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBlockDialog && otherUserId.isNotBlank()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${conv?.otherUser?.resolvedName ?: "User"}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Blocked users can no longer message you, follow you, or send you chat push notifications. You can unblock them anytime in Settings.")
            },
            confirmButton = {
                com.example.ui.components.KartPrimaryButton(
                    text = "Block User",
                    containerColor = KartDanger,
                    onClick = {
                        showBlockDialog = false
                        onBlockUser(otherUserId) {}
                    }
                )
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog && !conv?.otherUser?.id.isNullOrBlank()) {
        ReportDialog(
            targetLabel = "User",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                showReportDialog = false
                viewModel.reportUser(conv.otherUser.id, reason, details)
            }
        )
    }

    if (previewImageUrl != null) {
        ChatImageLightbox(
            imageUrl = previewImageUrl!!,
            senderName = conv?.otherUser?.resolvedName ?: "Photo",
            onDismiss = { previewImageUrl = null }
        )
    }
    }
}

@Composable
private fun ChatImageLightbox(
    imageUrl: String,
    senderName: String,
    onDismiss: () -> Unit
) {
    val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val enterAnim = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var showChrome by remember { mutableStateOf(true) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 180, easing = easeOutStrong),
        label = "lightboxZoomScale"
    )
    val chromeAlpha by animateFloatAsState(
        targetValue = if (showChrome && dragY.value == 0f && !isClosing) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = easeOutStrong),
        label = "lightboxChromeAlpha"
    )

    fun animateClose(fromSwipeDown: Boolean = false) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            if (fromSwipeDown) {
                launch {
                    dragY.animateTo(
                        targetValue = 900f,
                        animationSpec = tween(durationMillis = 180, easing = easeOutStrong)
                    )
                }
            }
            enterAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOutStrong)
            )
            onDismiss()
        }
    }

    BackHandler { animateClose() }

    LaunchedEffect(Unit) {
        enterAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = easeOutStrong)
        )
    }

    val dragFraction = (kotlin.math.abs(dragY.value) / 480f).coerceIn(0f, 1f)
    val backdropAlpha = enterAnim.value * (1f - dragFraction * 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showChrome = !showChrome },
                    onDoubleTap = {
                        scale = if (scale > 1.2f) 1f else 2.5f
                    }
                )
            }
            .pointerInput(scale) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY.value > 140f) {
                            animateClose(fromSwipeDown = true)
                        } else {
                            scope.launch {
                                dragY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 200, easing = easeOutStrong)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragY.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 200, easing = easeOutStrong)
                            )
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (scale <= 1.05f && !isClosing) {
                            val next = (dragY.value + dragAmount).coerceAtLeast(-80f)
                            scope.launch { dragY.snapTo(next) }
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Photo preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = enterAnim.value
                    val baseScale = 0.92f + (0.08f * p)
                    val dragScale = 1f - (dragFraction * 0.14f)
                    val finalScale = baseScale * animatedScale * dragScale
                    scaleX = finalScale
                    scaleY = finalScale
                    translationY = dragY.value
                    alpha = p
                }
        )

        // Minimal top bar over subtle gradient scrim
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha * enterAnim.value }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            IconButton(onClick = { animateClose() }) {
                Icon(
                    KartLucideIcons.ArrowLeft,
                    contentDescription = "Close preview",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = senderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceMessageBubbleContent(
    isMine: Boolean,
    isPlaying: Boolean,
    progress: Float,
    displaySeconds: Int,
    onTogglePlay: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    val bars = remember { listOf(9, 15, 21, 13, 19, 24, 16, 11, 20, 14, 18, 10, 15, 9) }

    val wavePhase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "voiceWave")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voiceWavePhase"
        ).value
    } else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clickable(onClick = onTogglePlay)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (isMine) Color.White.copy(alpha = 0.22f)
                    else KartPrimary.copy(alpha = 0.14f)
                )
        ) {
            Icon(
                imageVector = if (isPlaying) VoicePauseIcon else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                tint = if (isMine) Color.White else KartPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            modifier = Modifier.height(26.dp)
        ) {
            val activeCount = (progress * bars.size).toInt()
            bars.forEachIndexed { idx, h ->
                val isPassed = isPlaying && idx <= activeCount
                val scaleYAnim = if (isPlaying) {
                    val phaseShift = ((idx % 4) * 0.25f)
                    val wave = kotlin.math.sin(((wavePhase + phaseShift) * kotlin.math.PI)).toFloat()
                    0.68f + (0.42f * wave)
                } else 1f
                val barColor = when {
                    isMine && isPassed -> Color.White
                    isMine -> Color.White.copy(alpha = 0.45f)
                    isPassed -> KartPrimary
                    else -> KartPrimary.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .graphicsLayer { scaleY = scaleYAnim }
                        .clip(RoundedCornerShape(999.dp))
                        .background(barColor)
                )
            }
        }

        Text(
            text = if (displaySeconds > 0 || isPlaying) {
                KartFormatters.formatDuration(displaySeconds)
            } else "Voice",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.SemiBold,
            color = if (isMine) Color.White.copy(alpha = 0.94f) else tokens.textSecondary
        )
    }
}

private val VoiceMicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VoiceMic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            verticalLineTo(12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            verticalLineTo(5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            close()
            moveTo(19f, 10f)
            verticalLineTo(12f)
            curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
            curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
            verticalLineTo(10f)
            moveTo(12f, 19f)
            verticalLineTo(22f)
            moveTo(8f, 22f)
            horizontalLineTo(16f)
        }
    }.build()
}

private val VoicePauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VoicePause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 5f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            horizontalLineTo(18f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
        }
    }.build()
}

private data class ReplyDraft(
    val messageId: String,
    val senderName: String,
    val snippet: String
)

@Composable
private fun QuotedReplyBubbleBlock(
    senderName: String,
    snippet: String,
    isMine: Boolean,
    onClick: () -> Unit
) {
    val tokens = KartThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isMine) Color.White.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (isMine) Color.White.copy(alpha = 0.88f) else KartPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMine) Color.White else KartPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = if (isMine) Color.White.copy(alpha = 0.86f) else tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val ReplyArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReplyArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(9f, 17f)
            lineTo(4f, 12f)
            lineTo(9f, 7f)
            moveTo(20f, 18f)
            verticalLineTo(16f)
            curveTo(20f, 13.79f, 18.21f, 12f, 16f, 12f)
            horizontalLineTo(4f)
        }
    }.build()
}

@Composable
private fun OnlineDotBadge(
    visible: Boolean,
    borderColor: Color,
    dotSize: Dp = 13.dp,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180, easing = easeOutStrong)) +
            if (reduceMotion) EnterTransition.None
            else scaleIn(initialScale = 0.90f, animationSpec = tween(180, easing = easeOutStrong)),
        exit = fadeOut(tween(140, easing = easeOutStrong)) +
            if (reduceMotion) ExitTransition.None
            else scaleOut(targetScale = 0.90f, animationSpec = tween(140, easing = easeOutStrong)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(borderColor)
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(KartSuccess)
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble(
    senderName: String? = null,
    reduceMotion: Boolean
) {
    val tokens = KartThemeTokens.colors
    val density = LocalDensity.current
    val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val easeInOutStrong = remember { CubicBezierEasing(0.77f, 0f, 0.175f, 1f) }
    val enterProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enterProgress.value < 1f) {
            enterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = easeOutStrong)
            )
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
    val dotPhases = List(3) { idx ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = easeInOutStrong),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(idx * 120)
            ),
            label = "typingDot_$idx"
        )
    }
    val liftPx = with(density) { 3.5.dp.toPx() }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 4.dp
    )
    val firstName = senderName?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                val p = enterProgress.value
                alpha = p
                if (!reduceMotion) {
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                }
            }
    ) {
        Surface(
            shape = bubbleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dotPhases.forEach { phaseState ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .graphicsLayer {
                                    val phase = phaseState.value
                                    alpha = if (reduceMotion) {
                                        0.55f + 0.4f * phase
                                    } else {
                                        0.45f + 0.55f * phase
                                    }
                                    if (!reduceMotion) {
                                        translationY = -liftPx * phase
                                    }
                                }
                                .clip(CircleShape)
                                .background(tokens.textSecondary)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (firstName != null) "$firstName is typing" else "Typing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
