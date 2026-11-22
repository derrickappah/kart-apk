package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.KartReviewTags
import com.example.domain.model.Profile
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.KartWarning

@Composable
fun DeliveryVerificationDialog(
    onDismiss: () -> Unit,
    onSendOtp: () -> Unit,
    onConfirm: (code: String, mode: String) -> Unit,
    isSubmitting: Boolean
) {
    var mode by remember { mutableStateOf("pin") }
    var code by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Confirm Delivery",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Confirming delivery releases funds from Kart Escrow to the seller. Only confirm once you have inspected the item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KartThemeTokens.colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KartChip(
                        label = "Delivery PIN",
                        selected = mode == "pin",
                        onClick = { mode = "pin"; code = "" }
                    )
                    KartChip(
                        label = "One-Time OTP",
                        selected = mode == "otp",
                        onClick = { mode = "otp"; code = "" }
                    )
                }
                if (mode == "otp") {
                    KartOutlineButton(
                        text = if (otpSent) "Resend Delivery OTP" else "Send Delivery OTP",
                        onClick = {
                            otpSent = true
                            onSendOtp()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                KartTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = if (mode == "pin") "4-Digit Delivery PIN" else "6-Digit Verification OTP",
                    placeholder = if (mode == "pin") "••••" else "123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            KartPrimaryButton(
                text = "Release Escrow",
                loading = isSubmitting,
                enabled = code.length >= 4,
                onClick = { onConfirm(code, mode) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RefundRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isSubmitting: Boolean
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Request Refund", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Describe the issue with your order. Kart's escrow team will review your dispute and protect your funds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KartThemeTokens.colors.textSecondary
                )
                KartTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason for refund",
                    placeholder = "Item not as described, seller unresponsive...",
                    singleLine = false,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            KartPrimaryButton(
                text = "Submit Request",
                loading = isSubmitting,
                enabled = reason.trim().length >= 8,
                onClick = { onSubmit(reason.trim()) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf("Scam / Fraud", "Counterfeit Item", "Inappropriate Content", "Harassment", "Wrong Category")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Report $targetLabel", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reasons.forEach { reason ->
                        KartChip(
                            label = reason,
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                    }
                }
                KartTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = "Additional details",
                    placeholder = "Share helpful context for our moderation team...",
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            KartPrimaryButton(
                text = "Submit Report",
                onClick = { onSubmit(selectedReason, details.trim()) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewSubmissionDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, formattedComment: String) -> Unit,
    isSubmitting: Boolean
) {
    var rating by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var comment by remember { mutableStateOf("") }
    val ratingLabels = listOf("Poor", "Fair", "Good", "Very Good", "Great")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Rate $title", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) KartLucideIcons.StarFilled else KartLucideIcons.StarOutline,
                                contentDescription = "$star stars",
                                tint = if (star <= rating) KartWarning else KartThemeTokens.colors.textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Text(
                    text = ratingLabels[(rating - 1).coerceIn(0, 4)],
                    style = MaterialTheme.typography.titleSmall,
                    color = KartPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KartReviewTags.forEach { tag ->
                        val active = tag in selectedTags
                        KartChip(
                            label = tag,
                            selected = active,
                            onClick = {
                                selectedTags = if (active) selectedTags - tag else selectedTags + tag
                            }
                        )
                    }
                }
                KartTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    label = "Your review (${comment.length}/500)",
                    placeholder = "Share how the campus handoff and item quality went...",
                    singleLine = false,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            KartPrimaryButton(
                text = "Publish Review",
                loading = isSubmitting,
                onClick = {
                    val tagPrefix = if (selectedTags.isNotEmpty()) "[Tags: ${selectedTags.joinToString(", ")}] " else ""
                    onSubmit(rating, (tagPrefix + comment.trim()).trim())
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersListSheet(
    title: String,
    users: List<Profile>,
    onUserClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            if (users.isEmpty()) {
                Text(
                    "No users to show yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KartThemeTokens.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(users, key = { it.id }) { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onUserClick(user.id)
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            UserAvatar(
                                avatarUrl = user.avatarUrl,
                                displayName = user.resolvedName,
                                size = 44.dp,
                                isVerified = user.isVerified == true
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.resolvedName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    user.campus ?: "@${user.username ?: "student"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KartThemeTokens.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AvatarPickerSheet(
    onSelectPresetUrl: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val presetSeeds = listOf("Kwame", "Ama", "Kofi", "Abena", "Yaw", "Efua", "Nana", "Akua")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Choose Avatar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            KartOutlineButton(
                text = "Upload Photo from Device",
                icon = KartLucideIcons.Image,
                onClick = {
                    onDismiss()
                    onPickFromGallery()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Or pick a campus avatar", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presetSeeds.forEach { seed ->
                    val url = "https://api.dicebear.com/7.x/avataaars/png?seed=$seed"
                    UserAvatar(
                        avatarUrl = url,
                        displayName = seed,
                        size = 64.dp,
                        modifier = Modifier.clickable {
                            onSelectPresetUrl(url)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
