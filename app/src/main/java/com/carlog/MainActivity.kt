package com.carlog

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.ThemeMode
import com.carlog.data.repository.CarRepository
import com.carlog.presentation.navigation.NavGraph
import com.carlog.presentation.theme.CarLogTheme
import com.carlog.presentation.util.LocalCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var carRepository: CarRepository
    
    @Inject
    lateinit var appPreferences: AppPreferences
    
    override fun attachBaseContext(newBase: Context) {
        // Читаем выбранный язык из SharedPreferences
        val sharedPrefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val languageCode = sharedPrefs.getString("language", "ru") ?: "ru"
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Синхронизируем пробег всех автомобилей при запуске
        lifecycleScope.launch {
            carRepository.syncAllCarsMileage()
        }
        
        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val currency by appPreferences.currency.collectAsState(initial = com.carlog.data.preferences.Currency.RUB)
            val isFirstLaunch by appPreferences.isFirstLaunch.collectAsState(initial = null)
            val systemDarkTheme = isSystemInDarkTheme()
            
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }
            
            // Ждем загрузки isFirstLaunch из DataStore
            if (isFirstLaunch == null) {
                // Показываем загрузочный экран
                CarLogTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                val startDestination = if (isFirstLaunch == true) {
                    com.carlog.presentation.navigation.Screen.LanguageSelection.route
                } else {
                    com.carlog.presentation.navigation.Screen.CarList.route
                }
                
                CarLogTheme(darkTheme = darkTheme) {
                    CompositionLocalProvider(LocalCurrency provides currency) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            NavGraph(startDestination = startDestination)
                        }
                    }
                }
            }
        }
    }
}
