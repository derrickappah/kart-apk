package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.UserSession
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
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
    }

    val sessionFlow: Flow<UserSession> = context.kartDataStore.data.map { prefs ->
        UserSession(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            email = prefs[Keys.USER_EMAIL].orEmpty()
        )
    }

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

    suspend fun currentSession(): UserSession = sessionFlow.first()

    suspend fun saveSession(session: UserSession) {
        context.kartDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
        }
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
}
