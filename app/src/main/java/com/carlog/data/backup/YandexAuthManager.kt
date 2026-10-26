package com.carlog.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
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
        // Случайная метка начатого входа: редирект без неё (или с чужой) не принимается
        private const val KEY_PENDING_STATE = "yandex_oauth_state"

        /**
         * Токен из фрагмента редиректа (`access_token=…&state=…`) — только если `state`
         * совпал с меткой входа, который начали в приложении.
         *
         * Раньше принимался любой `carlog://oauth#access_token=…`: ссылка на веб-странице
         * или другое приложение могли подключить Car Log к чужому Диску, и бэкапы
         * (VIN, номера, фото документов) уезжали бы туда.
         */
        fun extractToken(fragment: String?, expectedState: String?): String? {
            if (fragment.isNullOrEmpty() || expectedState.isNullOrEmpty()) return null
            val params = fragment.split("&")
                .mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()
            if (params["state"] != expectedState) return null
            return params["access_token"]?.takeIf { it.isNotEmpty() }
        }
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

    /**
     * URL для открытия в браузере — начало OAuth flow. Каждый вызов начинает новый вход
     * со своей меткой `state`: Яндекс вернёт её в редиректе вместе с токеном
     */
    fun getAuthUrl(): String {
        val state = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_PENDING_STATE, state).commit()
        return "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID" +
            "&redirect_uri=carlog://oauth/yandex&state=$state"
    }

    /**
     * Извлекает access_token из URI после редиректа.
     * Яндекс редиректит на: carlog://oauth/yandex#access_token=TOKEN&state=STATE&...
     * Метка входа одноразовая: после принятого токена повторить тот же редирект нельзя.
     */
    fun parseTokenFromUri(uri: Uri): String? {
        val token = extractToken(uri.fragment, prefs.getString(KEY_PENDING_STATE, null))
            ?: return null
        prefs.edit().remove(KEY_PENDING_STATE).apply()
        return token
    }
}
