package com.carlog.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

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
}
