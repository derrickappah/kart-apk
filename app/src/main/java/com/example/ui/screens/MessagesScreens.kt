package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            KartSearchBar(
                query = search,
                onQueryChange = { search = it },
                onSearch = {},
                placeholder = "Search chats or listings..."
            )
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
    onShowMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messageInput by remember { mutableStateOf("") }

    val conv = state.conversations.firstOrNull { it.id == conversationId }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageMessage(conversationId, currentUserId, context.contentResolver, uri)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.loadThread(conversationId, silent = false)
        while (true) {
            delay(4500L)
            viewModel.loadThread(conversationId, silent = true)
        }
    }

    LaunchedEffect(state.activeMessages.size) {
        if (state.activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.activeMessages.lastIndex)
        }
    }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            onShowMessage(it)
            viewModel.clearFeedback()
        }
    }

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
                            maxLines = 1
                        )
                        if (!conv?.otherUser?.campus.isNullOrBlank()) {
                            Text(
                                text = conv?.otherUser?.campus.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
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

        // Messages List
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.activeMessages, key = { it.id ?: "${it.createdAt}_${it.content.hashCode()}" }) { msg ->
                val isMine = msg.senderId == currentUserId
                val imageUrl = KartFormatters.extractMessageImageUrl(msg.content)

                Row(
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                        modifier = Modifier.widthIn(max = 285.dp)
                    ) {
                        val bubbleShape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp
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
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Shared photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(210.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) Color.White else tokens.textPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = KartFormatters.formatChatTime(msg.createdAt),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = tokens.textMuted
                        )
                    }
                }
            }
        }

        // Message Composer
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(KartLucideIcons.Image, contentDescription = "Send photo", tint = KartPrimary)
                }

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        Text(
                            "Type a message...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textMuted
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = tokens.textPrimary,
                        unfocusedTextColor = tokens.textPrimary,
                        cursorColor = KartPrimary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = KartPrimary,
                        unfocusedBorderColor = tokens.subtleDivider
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    enabled = messageInput.isNotBlank() && !state.sending,
                    onClick = {
                        val toSend = messageInput.trim()
                        messageInput = ""
                        viewModel.sendMessage(conversationId, currentUserId, toSend)
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (messageInput.isNotBlank()) KartPrimary else tokens.subtleDivider)
                        .testTag("chat_send_btn")
                ) {
                    Icon(
                        KartLucideIcons.Send,
                        contentDescription = "Send message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
