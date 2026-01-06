package com.carlog.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences"
)

private const val SHARED_PREFS_NAME = "app_settings"
private const val KEY_LANGUAGE = "language"

/**
 * Настройки приложения (тема, валюта)
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.appPreferencesDataStore
    private val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val CURRENCY_KEY = stringPreferencesKey("currency")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
    }

    // === Тема ===

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        ThemeMode.valueOf(themeName)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    // === Валюта ===

    val currency: Flow<Currency> = dataStore.data.map { preferences ->
        val currencyCode = preferences[CURRENCY_KEY] ?: Currency.RUB.code
        Currency.values().find { it.code == currencyCode } ?: Currency.RUB
    }

    suspend fun setCurrency(currency: Currency) {
        dataStore.edit { preferences ->
            preferences[CURRENCY_KEY] = currency.code
        }
    }

    // === Язык ===

    val language: Flow<String> = dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "ru" // По умолчанию русский
    }

    suspend fun setLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
        // Также сохраняем в SharedPreferences для синхронного чтения при старте
        // Используем commit() для синхронного сохранения
        sharedPrefs.edit().putString(KEY_LANGUAGE, languageCode).commit()
    }

    // Синхронное чтение языка (для attachBaseContext)
    fun getLanguageSync(): String {
        return sharedPrefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
    }

    // === Первый запуск ===

    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_FIRST_LAUNCH_KEY] ?: true // По умолчанию true
    }

    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] = false
        }
    }
}

/**
 * Режим темы
 */
enum class ThemeMode {
    LIGHT,      // Светлая
    DARK,       // Темная
    SYSTEM      // Системная
}

/**
 * Валюта
 */
enum class Currency(
    val code: String,
    val symbol: String,
    val displayName: String
) {
    RUB("RUB", "₽", "Российский рубль"),
    USD("USD", "$", "Доллар США"),
    EUR("EUR", "€", "Евро"),
    KZT("KZT", "₸", "Казахстанский тенге"),
    BYN("BYN", "Br", "Белорусский рубль")
}
