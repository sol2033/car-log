package com.carlog.presentation.screens.language

import androidx.lifecycle.ViewModel
import com.carlog.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    suspend fun selectLanguage(languageCode: String) {
        appPreferences.setLanguage(languageCode)
        // НЕ вызываем setFirstLaunchCompleted() здесь - только после Welcome screen
    }
}
