package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.repository.ApiResult
import com.example.data.repository.KartRepository
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.Profile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartOutlineButton
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartThemeTokens
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsHubScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onNavigateSection: (String) -> Unit,
    onNavigateVerify: () -> Unit,
    onNavigateInfo: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tokens = KartThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Settings", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsGroup("Account & Verification") {
                    SettingsRow(
                        icon = KartLucideIcons.IdCard,
                        title = "Identity Verification",
                        subtitle = if (profile?.isVerified == true) "Verified badge active" else "Submit ID & selfie",
                        onClick = onNavigateVerify
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Mail,
                        title = "Email Verification",
                        subtitle = when {
                            profile?.emailVerified == true && !profile.email.isNullOrBlank() -> "Verified • ${profile.email}"
                            profile?.emailVerified == true -> "Verified"
                            !profile?.email.isNullOrBlank() -> "${profile?.email} • Pending verification"
                            else -> "Verify your student email"
                        },
                        tint = if (profile?.emailVerified == true) KartSuccess else KartPrimary,
                        onClick = { onNavigateSection("email") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Phone,
                        title = "Phone Number Verification",
                        subtitle = when {
                            profile?.phoneVerified == true && !profile.phone.isNullOrBlank() -> "Verified • ${profile.phone}"
                            profile?.phoneVerified == true -> "Verified"
                            !profile?.phone.isNullOrBlank() -> "${profile?.phone} • Pending verification"
                            else -> "Add & verify Ghana mobile number"
                        },
                        tint = if (profile?.phoneVerified == true) KartSuccess else KartPrimary,
                        onClick = { onNavigateSection("phone") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Landmark,
                        title = "Payout Methods",
                        subtitle = profile?.momoNumber ?: "Mobile Money & Bank accounts",
                        onClick = { onNavigateSection("payout") }
                    )
                }
            }

            item {
                SettingsGroup("Security & Escrow") {
                    SettingsRow(
                        icon = KartLucideIcons.KeyRound,
                        title = "Delivery PIN",
                        subtitle = "4-digit PIN to release escrow funds on handoff",
                        onClick = { onNavigateSection("delivery-pin") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Lock,
                        title = "Change Password",
                        subtitle = "Update your Kart account password",
                        onClick = { onNavigateSection("password") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.ShieldCheck,
                        title = "Two-Factor & Login Activity",
                        subtitle = "Manage active sessions & security",
                        onClick = { onNavigateSection("security") }
                    )
                }
            }

            item {
                SettingsGroup("Preferences") {
                    SettingsRow(
                        icon = KartLucideIcons.Palette,
                        title = "Theme & Appearance",
                        subtitle = "System, Light, or Dark mode",
                        onClick = { onNavigateSection("theme") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Globe,
                        title = "Language & Region",
                        subtitle = "English (Ghana • ₵ GHS)",
                        onClick = { onNavigateSection("language") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.BellRing,
                        title = "Notification Preferences",
                        subtitle = "Push & email alert controls",
                        onClick = { onNavigateSection("notifications") }
                    )
                }
            }

            item {
                SettingsGroup("Support & Legal") {
                    SettingsRow(
                        icon = KartLucideIcons.Headphones,
                        title = "WhatsApp Support",
                        subtitle = "Chat with Kart team (+233 50 050 2158)",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/233500502158")))
                        }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.HelpCircle,
                        title = "FAQ & Safety Guide",
                        subtitle = "Escrow rules, safe meetups, returns",
                        onClick = { onNavigateInfo("faq") }
                    )
                    SettingsRow(
                        icon = KartLucideIcons.Trash2,
                        title = "Delete Account",
                        subtitle = "Request permanent account & data deletion",
                        tint = KartDanger,
                        onClick = { onNavigateSection("delete-account") }
                    )
                }
            }

            item {
                KartOutlineButton(
                    text = "Sign Out",
                    borderColor = KartDanger,
                    contentColor = KartDanger,
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kart for Android • v1.0 (kart.cx)",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = KartThemeTokens.colors.textSecondary,
            fontWeight = FontWeight.Bold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = KartPrimary
) {
    val tokens = KartThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Icon(KartLucideIcons.ChevronRight, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(start = 52.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDetailScreen(
    section: String,
    profile: Profile?,
    sessionViewModel: SessionViewModel,
    repository: KartRepository,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val themePref by sessionViewModel.themePreference.collectAsStateWithLifecycle()
    val langPref by sessionViewModel.languagePreference.collectAsStateWithLifecycle()
    val pushEnabled by sessionViewModel.pushEnabled.collectAsStateWithLifecycle()
    val emailNotifs by sessionViewModel.emailNotifsEnabled.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(section) {
        if (section == "email" || section == "phone") {
            sessionViewModel.refreshUserMetadata()
        }
    }

    val screenTitle = when (section) {
        "email" -> "Email Verification"
        "phone" -> "Phone Verification"
        "payout" -> "Payout Settings"
        "delivery-pin" -> "Delivery PIN"
        "password" -> "Change Password"
        "security" -> "Security & Activity"
        "theme" -> "Appearance"
        "language" -> "Language & Region"
        "notifications" -> "Notifications"
        "delete-account" -> "Delete Account"
        else -> "Settings"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = screenTitle, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (section) {
                "email" -> item {
                    var email by remember(profile?.email) { mutableStateOf(profile?.email.orEmpty()) }
                    var otp by remember { mutableStateOf("") }
                    var verifiedNow by remember { mutableStateOf(false) }
                    var isChanging by remember { mutableStateOf(false) }
                    val isAlreadyVerified = (profile?.emailVerified == true || verifiedNow) && !isChanging

                    if (isAlreadyVerified) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(18.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            KartLucideIcons.CheckCircle,
                                            contentDescription = null,
                                            tint = KartSuccess,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "Email Already Verified",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (email.isNotBlank()) {
                                        Text(
                                            email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        "Your email address is verified and linked to your Kart account. No further action is needed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                }
                            }
                            KartPrimaryButton(
                                text = "Done",
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth()
                            )
                            KartOutlineButton(
                                text = "Verify a Different Email",
                                onClick = {
                                    isChanging = true
                                    otp = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KartTextField(email, { email = it }, "Campus Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            KartOutlineButton(
                                text = "Send Verification Code",
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        val r = repository.sendEmailVerificationOtp(email)
                                        loading = false
                                        onShowMessage(if (r is ApiResult.Success) "Verification OTP sent!" else (r as ApiResult.Error).message)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            KartTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, "Enter 6-Digit OTP", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            KartPrimaryButton(
                                text = "Verify Email",
                                loading = loading,
                                enabled = otp.length >= 4,
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        val r = repository.verifyEmailOtp(email, otp)
                                        loading = false
                                        if (r is ApiResult.Success) {
                                            verifiedNow = true
                                            isChanging = false
                                            otp = ""
                                            sessionViewModel.refreshUserMetadata()
                                            onShowMessage("Email verified!")
                                        } else {
                                            onShowMessage((r as ApiResult.Error).message)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isChanging) {
                                KartOutlineButton(
                                    text = "Cancel",
                                    onClick = {
                                        isChanging = false
                                        email = profile?.email.orEmpty()
                                        otp = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                "phone" -> item {
                    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
                    var otp by remember { mutableStateOf("") }
                    var verifiedNow by remember { mutableStateOf(false) }
                    var isChanging by remember { mutableStateOf(false) }
                    val isAlreadyVerified = (profile?.phoneVerified == true || verifiedNow) && !isChanging

                    if (isAlreadyVerified) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(18.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            KartLucideIcons.CheckCircle,
                                            contentDescription = null,
                                            tint = KartSuccess,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "Phone Number Already Verified",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (phone.isNotBlank()) {
                                        Text(
                                            phone,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        "Your mobile number is verified and active on your Kart account. No further action is needed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KartThemeTokens.colors.textSecondary
                                    )
                                }
                            }
                            KartPrimaryButton(
                                text = "Done",
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth()
                            )
                            KartOutlineButton(
                                text = "Change Phone Number",
                                onClick = {
                                    isChanging = true
                                    otp = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KartTextField(phone, { phone = it }, "Mobile Number", placeholder = "+233540000000", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            KartOutlineButton(
                                text = "Send SMS OTP",
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        val r = repository.sendPhoneOtp(phone)
                                        loading = false
                                        onShowMessage(if (r is ApiResult.Success) "SMS OTP sent!" else (r as ApiResult.Error).message)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            KartTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, "SMS Verification Code", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            KartPrimaryButton(
                                text = "Verify Phone",
                                loading = loading,
                                enabled = otp.length >= 4,
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        val r = repository.verifyPhoneOtp(phone, otp)
                                        loading = false
                                        if (r is ApiResult.Success) {
                                            verifiedNow = true
                                            isChanging = false
                                            otp = ""
                                            sessionViewModel.refreshUserMetadata()
                                            onShowMessage("Phone number verified!")
                                        } else {
                                            onShowMessage((r as ApiResult.Error).message)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isChanging) {
                                KartOutlineButton(
                                    text = "Cancel",
                                    onClick = {
                                        isChanging = false
                                        phone = profile?.phone.orEmpty()
                                        otp = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                "payout" -> item {
                    var network by remember { mutableStateOf(profile?.momoNetwork ?: "MTN") }
                    var momoNum by remember { mutableStateOf(profile?.momoNumber.orEmpty()) }
                    var bankName by remember { mutableStateOf(profile?.bankName.orEmpty()) }
                    var bankAcc by remember { mutableStateOf(profile?.bankAccountNumber.orEmpty()) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Mobile Money Network", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MTN", "Telecel", "AT").forEach { n ->
                                KartChip(n, network == n, { network = n })
                            }
                        }
                        KartTextField(momoNum, { momoNum = it }, "MoMo Number", placeholder = "054 000 0000")
                        KartTextField(bankName, { bankName = it }, "Bank Name (Optional)", placeholder = "GCB / Ecobank / Fidelity")
                        KartTextField(bankAcc, { bankAcc = it }, "Bank Account Number (Optional)")
                        KartPrimaryButton(
                            text = "Save Payout Account",
                            loading = loading,
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val r = repository.updatePayoutSettings(network, momoNum, bankName, bankAcc)
                                    loading = false
                                    onShowMessage(if (r is ApiResult.Success) "Payout account saved!" else (r as ApiResult.Error).message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "delivery-pin" -> item {
                    var pin by remember { mutableStateOf("") }
                    var confirmPin by remember { mutableStateOf("") }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Your 4-digit Delivery PIN protects your escrow purchases. Enter it only when you have received and inspected your item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KartThemeTokens.colors.textSecondary
                        )
                        KartTextField(
                            pin,
                            { pin = it.filter(Char::isDigit).take(4) },
                            "New 4-Digit Delivery PIN",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        KartTextField(
                            confirmPin,
                            { confirmPin = it.filter(Char::isDigit).take(4) },
                            "Confirm PIN",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        KartPrimaryButton(
                            text = "Save Delivery PIN",
                            loading = loading,
                            enabled = pin.length == 4 && pin == confirmPin,
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val r = repository.saveDeliveryPin(pin)
                                    loading = false
                                    onShowMessage(if (r is ApiResult.Success) "Delivery PIN updated!" else (r as ApiResult.Error).message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "password" -> item {
                    var currentPw by remember { mutableStateOf("") }
                    var newPw by remember { mutableStateOf("") }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KartTextField(currentPw, { currentPw = it }, "Current Password", visualTransformation = PasswordVisualTransformation())
                        KartTextField(newPw, { newPw = it }, "New Password (min 6 chars)", visualTransformation = PasswordVisualTransformation())
                        KartPrimaryButton(
                            text = "Update Password",
                            loading = loading,
                            enabled = currentPw.length >= 6 && newPw.length >= 6,
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val r = repository.updatePassword(currentPw, newPw)
                                    loading = false
                                    onShowMessage(if (r is ApiResult.Success) "Password changed!" else (r as ApiResult.Error).message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "security" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                                Text("Active Session", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Kart Android Native App • Authenticated via Supabase JWT", style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                                Text("Email & Phone 2-Step Protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Email verified: ${if (profile?.emailVerified == true) "Yes" else "Pending"} • Phone verified: ${if (profile?.phoneVerified == true) "Yes" else "Pending"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KartThemeTokens.colors.textSecondary
                                )
                            }
                        }
                    }
                }
                "theme" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Choose Kart Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemePreference.entries.forEach { pref ->
                                KartChip(
                                    label = pref.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = themePref == pref,
                                    onClick = { sessionViewModel.setThemePreference(pref) }
                                )
                            }
                        }
                    }
                }
                "language" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Display Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf("English (GH)", "English (UK)", "English (US)", "French").forEach { lang ->
                            KartChip(label = lang, selected = langPref == lang, onClick = { sessionViewModel.setLanguage(lang) })
                        }
                    }
                }
                "notifications" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Push Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Chats, escrow updates, and order statuses", style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                            }
                            Switch(checked = pushEnabled, onCheckedChange = { sessionViewModel.setNotificationPrefs(it, emailNotifs) })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email Updates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Receipts, payouts, and campus deals", style = MaterialTheme.typography.bodySmall, color = KartThemeTokens.colors.textSecondary)
                            }
                            Switch(checked = emailNotifs, onCheckedChange = { sessionViewModel.setNotificationPrefs(pushEnabled, it) })
                        }
                    }
                }
                "delete-account" -> item {
                    var reason by remember { mutableStateOf("") }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "Submitting a deletion request notifies Kart administrators to permanently erase your profile, listings, and personal records after any pending escrow orders settle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KartThemeTokens.colors.textSecondary
                        )
                        KartTextField(reason, { reason = it }, "Reason for leaving", singleLine = false, maxLines = 4)
                        KartPrimaryButton(
                            text = "Submit Deletion Request",
                            containerColor = KartDanger,
                            loading = loading,
                            enabled = reason.trim().length >= 5,
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val r = repository.requestAccountDeletion(reason.trim())
                                    loading = false
                                    onShowMessage(if (r is ApiResult.Success) "Account deletion request submitted." else (r as ApiResult.Error).message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IdentityVerificationScreen(
    profile: Profile?,
    repository: KartRepository,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = KartThemeTokens.colors

    var step by remember { mutableIntStateOf(0) }
    var studentIdNum by remember { mutableStateOf("") }
    var idPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(profile?.verificationStatus.equals("Pending", true)) }

    var activeCameraTarget by remember { mutableStateOf<String?>(null) }

    val idPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) idPhotoUri = uri
    }
    val selfiePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selfieUri = uri
    }

    fun requestCamera(target: String) {
        activeCameraTarget = target
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Identity Verification", onBack = onBack)

        if (profile?.isVerified == true) {
            EmptyStateView(
                asset = EmptyStateAsset.VERIFICATION,
                title = "You're Verified!",
                subtitle = "Your blue verified badge is active on your profile and listings.",
                actionLabel = "Done",
                onActionClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            return
        }

        if (submitted) {
            EmptyStateView(
                asset = EmptyStateAsset.VERIFICATION_PENDING,
                title = "Verification Under Review",
                subtitle = "Our trust team is reviewing your ID and selfie. You'll receive a notification shortly.",
                actionLabel = "Back to Settings",
                onActionClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1. ID Card", "2. Selfie", "3. Review").forEachIndexed { idx, label ->
                        KartChip(label = label, selected = step == idx, onClick = { step = idx })
                    }
                }
            }

            when (step) {
                0 -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Step 1: Upload ID", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Take or select a clear photo of your valid government or institutional ID card.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        KartTextField(studentIdNum, { studentIdNum = it }, "ID Number", placeholder = "e.g. GHA-10948271-0")
                        if (idPhotoUri != null) {
                            AsyncImage(
                                model = idPhotoUri,
                                contentDescription = "ID preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KartOutlineButton(
                                text = "Take Photo",
                                icon = KartLucideIcons.Camera,
                                onClick = { requestCamera("id") },
                                modifier = Modifier.weight(1f)
                            )
                            KartOutlineButton(
                                text = "Upload Photo",
                                icon = KartLucideIcons.Image,
                                onClick = { idPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        KartPrimaryButton(
                            text = "Continue to Face Capture",
                            enabled = idPhotoUri != null && studentIdNum.isNotBlank(),
                            onClick = { step = 1 },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                1 -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Step 2: Face Verification", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Provide a clear, well-lit selfie matching your ID card.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        if (selfieUri != null) {
                            AsyncImage(
                                model = selfieUri,
                                contentDescription = "Selfie preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp))
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KartOutlineButton(
                                text = "Take Photo",
                                icon = KartLucideIcons.Camera,
                                onClick = { requestCamera("selfie") },
                                modifier = Modifier.weight(1f)
                            )
                            KartOutlineButton(
                                text = "Upload Photo",
                                icon = KartLucideIcons.Image,
                                onClick = { selfiePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        KartPrimaryButton(
                            text = "Review Submission",
                            enabled = selfieUri != null,
                            onClick = { step = 2 },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Step 3: Review & Submit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(KartLucideIcons.CheckCircle, contentDescription = null, tint = KartSuccess)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ID #: $studentIdNum", fontWeight = FontWeight.Bold)
                                }
                                Text("ID Document: ${if (idPhotoUri != null) "Attached" else "Missing"}", style = MaterialTheme.typography.bodySmall)
                                Text("Selfie Capture: ${if (selfieUri != null) "Attached" else "Missing"}", style = MaterialTheme.typography.bodySmall)
                                if (idPhotoUri != null || selfieUri != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (idPhotoUri != null) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "ID Card",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.textSecondary
                                                )
                                                AsyncImage(
                                                    model = idPhotoUri,
                                                    contentDescription = "ID review preview",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(120.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                            }
                                        }
                                        if (selfieUri != null) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "Selfie",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.textSecondary
                                                )
                                                AsyncImage(
                                                    model = selfieUri,
                                                    contentDescription = "Selfie review preview",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(120.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        KartPrimaryButton(
                            text = "Submit Verification",
                            loading = submitting,
                            enabled = idPhotoUri != null && selfieUri != null,
                            onClick = {
                                scope.launch {
                                    submitting = true
                                    val idUp = repository.uploadImageUri(context.contentResolver, idPhotoUri!!, bucket = "profiles")
                                    val selfieUp = repository.uploadImageUri(context.contentResolver, selfieUri!!, bucket = "profiles")
                                    if (idUp is ApiResult.Success && selfieUp is ApiResult.Success) {
                                        when (val res = repository.submitKycVerification(
                                            idImageUrl = idUp.data,
                                            selfieImageUrl = selfieUp.data,
                                            studentIdNumber = studentIdNum.trim(),
                                            fullName = profile?.resolvedName.orEmpty(),
                                            campus = profile?.campus.orEmpty()
                                        )) {
                                            is ApiResult.Success -> {
                                                submitting = false
                                                submitted = true
                                                onShowMessage("Verification submitted!")
                                            }
                                            is ApiResult.Error -> {
                                                submitting = false
                                                onShowMessage(res.message)
                                            }
                                        }
                                    } else {
                                        submitting = false
                                        val err = (idUp as? ApiResult.Error)?.message
                                            ?: (selfieUp as? ApiResult.Error)?.message
                                            ?: "Could not upload verification photos."
                                        onShowMessage(err)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (activeCameraTarget != null) {
        val target = activeCameraTarget
        com.example.ui.components.KartInAppCameraDialog(
            maxPhotos = 1,
            existingPhotoCount = 0,
            onDismiss = { activeCameraTarget = null },
            onPhotosCaptured = { captured ->
                val photo = captured.firstOrNull()
                if (photo != null) {
                    if (target == "id") idPhotoUri = photo else selfieUri = photo
                }
                activeCameraTarget = null
            },
            onFallbackToGallery = {
                activeCameraTarget = null
                if (target == "id") {
                    idPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    selfiePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        )
    }
}
