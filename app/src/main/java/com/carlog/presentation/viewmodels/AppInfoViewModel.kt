package com.carlog.presentation.screens.info

import androidx.lifecycle.ViewModel
import com.carlog.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    suspend fun completeFirstLaunch() {
        appPreferences.setFirstLaunchCompleted()
    }
}
