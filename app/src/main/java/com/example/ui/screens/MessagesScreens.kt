package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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

    LaunchedEffect(userId) {
        viewModel.loadConversations(userId)
        while (true) {
            delay(8000L)
            viewModel.loadConversations(userId)
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
        UserAvatar(
            avatarUrl = conversation.otherUser.avatarUrl,
            displayName = conversation.otherUser.resolvedName,
            size = 50.dp,
            isVerified = conversation.otherUser.isVerified == true
        )
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
                    text = KartFormatters.messageSnippet(conversation.lastMessage?.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (conversation.unreadCount > 0) tokens.textPrimary else tokens.textSecondary,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
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
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageInput by remember { mutableStateOf("") }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val conv = state.conversations.firstOrNull { it.id == conversationId }

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
        runCatching {
            mediaPlayerRef?.stop()
            mediaPlayerRef?.release()
        }
        mediaPlayerRef = null
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
        try {
            val mp = MediaPlayer().apply {
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
            stopPlayback()
            onShowMessage("Could not play voice message.")
        }
    }

    fun startRecording() {
        stopPlayback()
        val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        try {
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
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
        runCatching {
            rec?.stop()
            rec?.release()
        }
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

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
            runCatching {
                recorderRef?.stop()
                recorderRef?.release()
            }
            runCatching { recordingFile?.delete() }
        }
    }

    LaunchedEffect(conversationId, currentUserId) {
        if (conv == null && currentUserId.isNotBlank()) {
            viewModel.loadConversations(currentUserId)
        }
        viewModel.loadThread(conversationId, silent = false)
        while (true) {
            delay(4500L)
            viewModel.loadThread(conversationId, silent = true)
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
                        UserAvatar(
                            avatarUrl = conv?.otherUser?.avatarUrl,
                            displayName = conv?.otherUser?.resolvedName ?: "Campus Chat",
                            size = 38.dp,
                            isVerified = conv?.otherUser?.isVerified == true
                        )
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
                            if (!conv?.otherUser?.campus.isNullOrBlank()) {
                                Text(
                                    text = conv.otherUser.campus.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary
                                )
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
                        }
                    }
                }
                if (conv?.product != null) {
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
                key = { _, it -> it.id ?: "${it.createdAt}_${it.content.hashCode()}" }
            ) { index, msg ->
                val isMine = msg.senderId == currentUserId
                val isPending = msg.id?.startsWith("local_") == true
                val voicePayload = KartFormatters.extractMessageVoice(msg.content)
                val imageUrl = if (voicePayload == null) KartFormatters.extractMessageImageUrl(msg.content) else null
                val formattedTime = KartFormatters.formatChatTime(msg.createdAt)
                val newerMsg = reversedMessages.getOrNull(index - 1)
                val isLastInGroup = newerMsg == null ||
                    newerMsg.senderId != msg.senderId ||
                    KartFormatters.formatChatTime(newerMsg.createdAt) != formattedTime

                val enterProgress = remember(msg.id) { Animatable(if (isPending) 0f else 1f) }
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

                Row(
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLastInGroup) 10.dp else 0.dp)
                        .graphicsLayer {
                            val p = enterProgress.value
                            alpha = p * deliveryAlpha
                            translationY = (1f - p) * 18f
                            val s = 0.94f + (0.06f * p)
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
                                .padding(if (imageUrl != null) 4.dp else 12.dp)
                        ) {
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
                            } else {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) Color.White else tokens.textPrimary
                                )
                            }
                        }
                        if (isLastInGroup && formattedTime.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = tokens.textMuted
                            )
                        }
                    }
                }
            }
        }

        // Floating Message Composer
        val easeOutStrong = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
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
                } else {
                    val hasText = messageInput.isNotBlank()
                    Row(
                        verticalAlignment = Alignment.Bottom,
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
                            .border(1.dp, tokens.cardBorder, capsuleShape)
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
                                    text = "Message...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textMuted
                                )
                            }
                            BasicTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
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
                                if (hasText) {
                                    val toSend = messageInput.trim()
                                    messageInput = ""
                                    viewModel.sendMessage(conversationId, currentUserId, toSend)
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
                                .testTag(if (hasText) "chat_send_btn" else "chat_mic_btn")
                        ) {
                            AnimatedContent(
                                targetState = hasText,
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
