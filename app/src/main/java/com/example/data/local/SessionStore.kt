package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.UserSession
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kartDataStore: DataStore<Preferences> by preferencesDataStore(name = "kart_prefs")

class SessionStore(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val THEME_PREF = stringPreferencesKey("theme_pref")
        val LANGUAGE = stringPreferencesKey("language")
        val PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        val EMAIL_NOTIFS_ENABLED = booleanPreferencesKey("email_notifs_enabled")
        val PKCE_VERIFIER = stringPreferencesKey("oauth_pkce_verifier")
        val PKCE_STATE = stringPreferencesKey("oauth_pkce_state")
        val PKCE_CREATED_AT = longPreferencesKey("oauth_pkce_created_at")
    }

    val sessionFlow: Flow<UserSession> = context.kartDataStore.data.map { prefs ->
        val rawAccess = prefs[Keys.ACCESS_TOKEN].orEmpty()
        val rawRefresh = prefs[Keys.REFRESH_TOKEN].orEmpty()
        val decAccess = TokenCipher.decrypt(rawAccess)
        val decRefresh = TokenCipher.decrypt(rawRefresh)
        val accessCorrupted = rawAccess.startsWith(TokenCipher.PREFIX) && decAccess.isBlank()
        val refreshCorrupted = rawRefresh.startsWith(TokenCipher.PREFIX) && decRefresh.isBlank()
        if (accessCorrupted || refreshCorrupted) {
            UserSession()
        } else {
            UserSession(
                accessToken = decAccess,
                refreshToken = decRefresh,
                userId = prefs[Keys.USER_ID].orEmpty(),
                email = prefs[Keys.USER_EMAIL].orEmpty()
            )
        }
    }.distinctUntilChanged()

    val themePreferenceFlow: Flow<ThemePreference> = context.kartDataStore.data.map { prefs ->
        when (prefs[Keys.THEME_PREF]) {
            "LIGHT" -> ThemePreference.LIGHT
            "DARK" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    val languageFlow: Flow<String> = context.kartDataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE] ?: "English (GH)"
    }

    val pushEnabledFlow: Flow<Boolean> = context.kartDataStore.data.map { prefs ->
        prefs[Keys.PUSH_ENABLED] ?: true
    }

    val emailNotifsEnabledFlow: Flow<Boolean> = context.kartDataStore.data.map { prefs ->
        prefs[Keys.EMAIL_NOTIFS_ENABLED] ?: true
    }

    suspend fun currentSession(): UserSession {
        val prefs = context.kartDataStore.data.first()
        val rawAccess = prefs[Keys.ACCESS_TOKEN].orEmpty()
        val rawRefresh = prefs[Keys.REFRESH_TOKEN].orEmpty()
        val decAccess = TokenCipher.decrypt(rawAccess)
        val decRefresh = TokenCipher.decrypt(rawRefresh)
        val accessCorrupted = rawAccess.startsWith(TokenCipher.PREFIX) && decAccess.isBlank()
        val refreshCorrupted = rawRefresh.startsWith(TokenCipher.PREFIX) && decRefresh.isBlank()
        if (accessCorrupted || refreshCorrupted) {
            clearSession()
            return UserSession()
        }
        return UserSession(
            accessToken = decAccess,
            refreshToken = decRefresh,
            userId = prefs[Keys.USER_ID].orEmpty(),
            email = prefs[Keys.USER_EMAIL].orEmpty()
        )
    }

    suspend fun isPushEnabled(): Boolean = pushEnabledFlow.first()

    suspend fun saveSession(session: UserSession): Boolean {
        val encAccess = TokenCipher.encrypt(session.accessToken)
        val encRefresh = TokenCipher.encrypt(session.refreshToken)
        if ((session.accessToken.isNotBlank() && encAccess.isBlank()) ||
            (session.refreshToken.isNotBlank() && encRefresh.isBlank())
        ) {
            // Never persist plaintext or partially encrypted session tokens if Keystore encryption fails
            return false
        }
        context.kartDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = encAccess
            prefs[Keys.REFRESH_TOKEN] = encRefresh
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
        }
        return true
    }

    suspend fun clearSession() {
        context.kartDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
        }
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.THEME_PREF] = pref.name
        }
    }

    suspend fun setLanguage(language: String) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language
        }
    }

    suspend fun setNotificationPrefs(pushEnabled: Boolean, emailEnabled: Boolean) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.PUSH_ENABLED] = pushEnabled
            prefs[Keys.EMAIL_NOTIFS_ENABLED] = emailEnabled
        }
    }

    suspend fun setPushEnabled(pushEnabled: Boolean) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.PUSH_ENABLED] = pushEnabled
        }
    }

    suspend fun setEmailNotifsEnabled(emailEnabled: Boolean) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.EMAIL_NOTIFS_ENABLED] = emailEnabled
        }
    }

    suspend fun savePendingPkce(verifier: String, state: String) {
        val encVerifier = TokenCipher.encrypt(verifier).ifBlank { verifier }
        context.kartDataStore.edit { prefs ->
            prefs[Keys.PKCE_VERIFIER] = encVerifier
            prefs[Keys.PKCE_STATE] = state
            prefs[Keys.PKCE_CREATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun consumePendingPkce(): Pair<String, String>? {
        val prefs = context.kartDataStore.data.first()
        val rawVerifier = prefs[Keys.PKCE_VERIFIER].orEmpty()
        val state = prefs[Keys.PKCE_STATE].orEmpty()
        val createdAt = prefs[Keys.PKCE_CREATED_AT] ?: 0L
        context.kartDataStore.edit {
            it.remove(Keys.PKCE_VERIFIER)
            it.remove(Keys.PKCE_STATE)
            it.remove(Keys.PKCE_CREATED_AT)
        }
        val verifier = TokenCipher.decrypt(rawVerifier)
        val isExpired = createdAt <= 0L || (System.currentTimeMillis() - createdAt) > 10 * 60_000L
        if (verifier.isBlank() || isExpired) return null
        return verifier to state
    }
}
