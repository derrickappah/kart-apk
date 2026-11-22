package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import android.util.Log
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.EmptyStateAsset
import com.example.domain.model.KartGhanaCampuses
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KartChip
import com.example.ui.components.KartLucideIcons
import com.example.ui.components.KartPrimaryButton
import com.example.ui.components.KartSubScreenTopBar
import com.example.ui.components.KartTextField
import com.example.ui.theme.KartDanger
import com.example.ui.theme.KartDangerContainer
import com.example.ui.theme.KartPrimary
import com.example.ui.theme.KartSuccess
import com.example.ui.theme.KartSuccessContainer
import com.example.ui.theme.KartThemeTokens
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun LoginScreen(
    sessionViewModel: SessionViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateSignUp: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
    onBack: () -> Unit
) {
    val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { sessionViewModel.clearAuthFeedback() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.authBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.kart_logo),
                    contentDescription = "Kart logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(34.dp)
                )
                Text(
                    text = "Browse Guest",
                    style = MaterialTheme.typography.labelLarge,
                    color = KartPrimary,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = tokens.textPrimary
            )
            Text(
                text = "Sign in to buy, sell, chat, and manage your campus orders.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )

            val displayedError = localError ?: state.authError
            if (!displayedError.isNullOrBlank()) {
                AuthBanner(text = displayedError, isError = true)
            }
            if (!state.authInfo.isNullOrBlank()) {
                AuthBanner(text = state.authInfo!!, isError = false)
            }

            GoogleContinueButton(
                sessionViewModel = sessionViewModel,
                enabled = !state.authLoading,
                onSuccess = onLoginSuccess,
                onError = { msg -> localError = msg },
                testTag = "login_google_button"
            )

            AuthOrDivider()

            KartTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = "Email address",
                placeholder = "student@campus.edu",
                leadingIcon = { Icon(KartLucideIcons.Mail, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                testTag = "login_email_input"
            )

            KartTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = "Password",
                placeholder = "Enter your password",
                leadingIcon = { Icon(KartLucideIcons.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) KartLucideIcons.EyeOff else KartLucideIcons.Eye,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                testTag = "login_password_input"
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = KartPrimary,
                    modifier = Modifier
                        .clickable(onClick = onNavigateForgotPassword)
                        .padding(vertical = 4.dp)
                )
            }

            KartPrimaryButton(
                text = "Sign In",
                loading = state.authLoading,
                onClick = {
                    val cleanEmail = email.trim()
                    when {
                        cleanEmail.isBlank() || !cleanEmail.contains("@") ->
                            localError = "Please enter a valid email address."
                        password.length < 6 ->
                            localError = "Password must be at least 6 characters."
                        else -> sessionViewModel.signIn(cleanEmail, password, onLoginSuccess)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "login_submit_button"
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New to Kart? ", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                Text(
                    text = "Create account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = KartPrimary,
                    modifier = Modifier.clickable(onClick = onNavigateSignUp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignUpScreen(
    sessionViewModel: SessionViewModel,
    onSignedUp: (signedInImmediately: Boolean) -> Unit,
    onNavigateLogin: () -> Unit
) {
    val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val tokens = KartThemeTokens.colors
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var campus by remember { mutableStateOf(KartGhanaCampuses.first()) }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.authBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.kart_logo),
                contentDescription = "Kart logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(34.dp)
            )
            Text(
                text = "Create your Kart account",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Join thousands of students buying and selling safely across campus.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )

            val err = localError ?: state.authError
            if (!err.isNullOrBlank()) AuthBanner(err, isError = true)
            if (!state.authInfo.isNullOrBlank()) AuthBanner(state.authInfo!!, isError = false)

            GoogleContinueButton(
                sessionViewModel = sessionViewModel,
                enabled = !state.authLoading,
                onSuccess = { onSignedUp(true) },
                onError = { msg -> localError = msg },
                testTag = "signup_google_button"
            )

            AuthOrDivider()

            KartTextField(
                value = fullName,
                onValueChange = { fullName = it; localError = null },
                label = "Full Name",
                placeholder = "Kwame Mensah",
                leadingIcon = { Icon(KartLucideIcons.User, contentDescription = null) },
                testTag = "signup_name_input"
            )
            KartTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = "Email address",
                placeholder = "student@campus.edu",
                leadingIcon = { Icon(KartLucideIcons.Mail, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                testTag = "signup_email_input"
            )
            KartTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone Number (Ghana)",
                placeholder = "050 000 0000",
                leadingIcon = { Icon(KartLucideIcons.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                testTag = "signup_phone_input"
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Select Your Campus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KartGhanaCampuses.take(6).forEach { c ->
                        KartChip(label = c, selected = campus == c, onClick = { campus = c })
                    }
                }
            }
            KartTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = "Password",
                placeholder = "At least 6 characters",
                leadingIcon = { Icon(KartLucideIcons.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                testTag = "signup_password_input"
            )

            KartPrimaryButton(
                text = "Create Account",
                loading = state.authLoading,
                onClick = {
                    when {
                        fullName.trim().length < 2 -> localError = "Please enter your full name."
                        !email.contains("@") -> localError = "Please enter a valid email address."
                        password.length < 6 -> localError = "Password must be at least 6 characters."
                        else -> sessionViewModel.signUp(email, password, fullName, campus, phone, onSignedUp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "signup_submit_button"
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Already have an account? ", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = KartPrimary,
                    modifier = Modifier.clickable(onClick = onNavigateLogin)
                )
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KartSubScreenTopBar(title = "Reset Password", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(24.dp)
        ) {
            Text(
                text = "Forgot your password?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enter the email linked to your Kart account and we will send you a password reset link.",
                style = MaterialTheme.typography.bodyMedium,
                color = KartThemeTokens.colors.textSecondary
            )
            if (!state.authError.isNullOrBlank()) AuthBanner(state.authError!!, isError = true)
            if (!state.authInfo.isNullOrBlank()) AuthBanner(state.authInfo!!, isError = false)

            KartTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email address",
                placeholder = "student@campus.edu",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            KartPrimaryButton(
                text = "Send Reset Link",
                loading = state.authLoading,
                enabled = email.contains("@"),
                onClick = { sessionViewModel.sendPasswordReset(email) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatusGuardScreen(
    asset: EmptyStateAsset,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        EmptyStateView(
            asset = asset,
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onActionClick = onAction
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun launchBrowserGoogleOAuth(context: Context, onError: (String) -> Unit): Boolean {
    return try {
        val baseUrl = BuildConfig.KART_API_BASE_URL.trimEnd('/')
        val authUri = Uri.parse("$baseUrl/api/auth/google?return_to_app=true")
        val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        onError("No browser found to complete Google sign-in.")
        false
    } catch (_: Exception) {
        onError("Could not start Google sign-in. Please try again.")
        false
    }
}

@Composable
private fun GoogleContinueButton(
    sessionViewModel: SessionViewModel,
    enabled: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    testTag: String,
    label: String = "Continue with Google"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val tokens = KartThemeTokens.colors
    var googleLoading by remember { mutableStateOf(false) }

    // Reset spinner when returning from the browser tab fallback
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                googleLoading = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isBusy = googleLoading || !enabled

    Surface(
        onClick = {
            if (isBusy) return@Surface
            val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
            val hasWebClientId = webClientId.isNotBlank() && !webClientId.startsWith("YOUR_")

            if (!hasWebClientId) {
                googleLoading = true
                if (!launchBrowserGoogleOAuth(context, onError)) {
                    googleLoading = false
                }
                return@Surface
            }

            googleLoading = true
            scope.launch {
                try {
                    val activityContext = context.findActivity() ?: context
                    val credentialManager = CredentialManager.create(activityContext)
                    val rawNonce = UUID.randomUUID().toString()
                    val hashedNonce = sha256Hex(rawNonce)

                    val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
                        .setNonce(hashedNonce)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(
                        request = request,
                        context = activityContext
                    )
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        googleLoading = false
                        sessionViewModel.signInWithGoogleIdToken(
                            idToken = googleIdTokenCredential.idToken,
                            rawNonce = rawNonce,
                            onSuccess = onSuccess
                        )
                    } else {
                        googleLoading = false
                        onError("Unexpected Google credential type. Please try again.")
                    }
                } catch (_: GetCredentialCancellationException) {
                    googleLoading = false
                } catch (e: NoCredentialException) {
                    Log.w("KartGoogleAuth", "NoCredentialException: ${e.errorMessage}", e)
                    // No Google account signed in on the device (or Android OAuth client SHA-1 not registered) — fall back to browser OAuth.
                    if (!launchBrowserGoogleOAuth(context, onError)) {
                        googleLoading = false
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("KartGoogleAuth", "GoogleIdTokenParsingException", e)
                    googleLoading = false
                    onError("Could not read Google account token. Please try again.")
                } catch (e: GetCredentialException) {
                    Log.e("KartGoogleAuth", "GetCredentialException: ${e.type} - ${e.errorMessage}", e)
                    googleLoading = false
                    onError(e.errorMessage?.toString() ?: "Google sign-in failed. Please try again.")
                } catch (e: Exception) {
                    Log.e("KartGoogleAuth", "Unexpected error", e)
                    googleLoading = false
                    onError(e.localizedMessage ?: "Could not start Google sign-in. Please try again.")
                }
            }
        },
        enabled = !isBusy,
        shape = RoundedCornerShape(12.dp),
        color = tokens.chatBackground,
        contentColor = tokens.textPrimary,
        border = BorderStroke(1.dp, tokens.subtleDivider),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (googleLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = KartPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Connecting to Google...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = "Google logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
            }
        }
    }
}

@Composable
private fun AuthOrDivider() {
    val tokens = KartThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(
            color = tokens.subtleDivider,
            thickness = 1.dp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "OR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = tokens.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalDivider(
            color = tokens.subtleDivider,
            thickness = 1.dp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AuthBanner(text: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) KartDangerContainer else KartSuccessContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) KartDanger else KartSuccess,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(14.dp)
        )
    }
}
