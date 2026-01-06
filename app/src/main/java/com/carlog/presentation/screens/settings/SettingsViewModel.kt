package com.carlog.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import com.carlog.util.DatabaseBackup
import com.carlog.util.ImportStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val database: CarLogDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val databaseBackup = DatabaseBackup(database, context)

    // === Тема ===

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)
        }
    }

    // === Валюта ===

    val currency: StateFlow<Currency> = appPreferences.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.RUB)

    fun setCurrency(currency: Currency) {
        viewModelScope.launch {
            appPreferences.setCurrency(currency)
        }
    }

    // === Язык ===

    val language: StateFlow<String> = appPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ru")

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            appPreferences.setLanguage(languageCode)
        }
    }
    
    // Для получения корутины, которая завершится после сохранения языка
    suspend fun setLanguageAndWait(languageCode: String) {
        appPreferences.setLanguage(languageCode)
    }
    
    // === Экспорт/Импорт ===
    
    suspend fun exportDatabase(outputStream: OutputStream): Result<Unit> {
        return databaseBackup.exportToJson(outputStream)
    }
    
    suspend fun importDatabase(inputStream: InputStream): Result<ImportStats> {
        return databaseBackup.importFromJson(inputStream)
    }
    
    fun generateBackupFileName(): String {
        return databaseBackup.generateBackupFileName()
    }
}
