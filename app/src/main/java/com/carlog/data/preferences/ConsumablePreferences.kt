package com.carlog.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "consumable_settings")

@Singleton
class ConsumablePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    
    companion object {
        private val SELECTED_CATEGORIES = stringSetPreferencesKey("selected_categories")
        
        // Интервалы по пробегу (ключ = категория)
        private fun intervalMileageKey(category: String) = intPreferencesKey("interval_mileage_$category")
        
        // Интервалы по дням (ключ = категория)
        private fun intervalDaysKey(category: String) = intPreferencesKey("interval_days_$category")
        
        // Объемы жидкостей (ключ = категория)
        private fun volumeKey(category: String) = doublePreferencesKey("volume_$category")
        
        // Флаг первого запуска для машины
        private fun firstLaunchKey(carId: Long) = booleanPreferencesKey("first_launch_$carId")
    }
    
    // Получить выбранные дополнительные категории
    val selectedCategories: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[SELECTED_CATEGORIES] ?: emptySet()
    }
    
    // Сохранить выбранные категории
    suspend fun saveSelectedCategories(categories: Set<String>) {
        dataStore.edit { preferences ->
            preferences[SELECTED_CATEGORIES] = categories
        }
    }
    
    // Получить интервал по пробегу для категории
    fun getIntervalMileage(category: String): Flow<Int?> = dataStore.data.map { preferences ->
        preferences[intervalMileageKey(category)]
    }
    
    // Сохранить интервал по пробегу
    suspend fun saveIntervalMileage(category: String, mileage: Int?) {
        dataStore.edit { preferences ->
            if (mileage != null) {
                preferences[intervalMileageKey(category)] = mileage
            } else {
                preferences.remove(intervalMileageKey(category))
            }
        }
    }
    
    // Получить интервал по дням для категории
    fun getIntervalDays(category: String): Flow<Int?> = dataStore.data.map { preferences ->
        preferences[intervalDaysKey(category)]
    }
    
    // Сохранить интервал по дням
    suspend fun saveIntervalDays(category: String, days: Int?) {
        dataStore.edit { preferences ->
            if (days != null) {
                preferences[intervalDaysKey(category)] = days
            } else {
                preferences.remove(intervalDaysKey(category))
            }
        }
    }
    
    // Получить объем жидкости для категории
    fun getVolume(category: String): Flow<Double?> = dataStore.data.map { preferences ->
        preferences[volumeKey(category)]
    }
    
    // Сохранить объем жидкости
    suspend fun saveVolume(category: String, volume: Double?) {
        dataStore.edit { preferences ->
            if (volume != null) {
                preferences[volumeKey(category)] = volume
            } else {
                preferences.remove(volumeKey(category))
            }
        }
    }
    
    // Получить все настройки для категории
    fun getCategorySettings(category: String): Flow<CategorySettings> = dataStore.data.map { preferences ->
        CategorySettings(
            intervalMileage = preferences[intervalMileageKey(category)],
            intervalDays = preferences[intervalDaysKey(category)],
            volume = preferences[volumeKey(category)]
        )
    }
    
    // Проверить, первый ли это запуск для машины
    fun isFirstLaunch(carId: Long): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[firstLaunchKey(carId)] ?: true
    }
    
    // Установить флаг, что первый запуск завершен
    suspend fun setFirstLaunchCompleted(carId: Long) {
        dataStore.edit { preferences ->
            preferences[firstLaunchKey(carId)] = false
        }
    }
}

data class CategorySettings(
    val intervalMileage: Int?,
    val intervalDays: Int?,
    val volume: Double?
)
