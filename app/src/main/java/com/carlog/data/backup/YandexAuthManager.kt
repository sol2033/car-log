package com.carlog.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управляет OAuth2-авторизацией через Яндекс.
 *
 * Чтобы интеграция заработала, нужно один раз:
 * 1. Зайти на https://oauth.yandex.ru
 * 2. Создать приложение, указать платформу "Android"
 * 3. В "Redirect URI" добавить: carlog://oauth/yandex
 * 4. Выдать права: "Яндекс Диск: чтение и запись"
 * 5. Скопировать ClientID и вставить в CLIENT_ID ниже
 */
@Singleton
class YandexAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // ClientID OAuth-приложения, зарегистрированного на oauth.yandex.ru (аккаунт владельца)
        const val CLIENT_ID = "af843fb71d374176ad39447461ff8c2f"

        private const val PREFS_NAME = "backup_prefs"
        private const val KEY_TOKEN = "yandex_token"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isConnectedFlow = MutableStateFlow(getToken() != null)
    val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

    fun isConnected(): Boolean = getToken() != null

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _isConnectedFlow.value = true
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _isConnectedFlow.value = false
    }

    /** URL для открытия в браузере — начало OAuth flow */
    fun getAuthUrl(): String =
        "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID&redirect_uri=carlog://oauth/yandex"

    /**
     * Извлекает access_token из URI после редиректа.
     * Яндекс редиректит на: carlog://oauth/yandex#access_token=TOKEN&...
     */
    fun parseTokenFromUri(uri: Uri): String? {
        val fragment = uri.fragment ?: return null
        return fragment.split("&")
            .mapNotNull { param ->
                val parts = param.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .firstOrNull { it.first == "access_token" }
            ?.second
    }
}
