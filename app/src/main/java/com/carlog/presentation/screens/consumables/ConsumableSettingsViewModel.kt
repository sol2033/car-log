package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.domain.model.ConsumableCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsumableSettingsState(
    val selectedCategories: Set<String> = emptySet(),
    val categoryIntervals: Map<String, Pair<Int?, Int?>> = emptyMap(), // Pair(mileage, days)
    val categoryVolumes: Map<String, Double?> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConsumableSettingsViewModel @Inject constructor(
    private val preferences: ConsumablePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _state = MutableStateFlow(ConsumableSettingsState())
    val state: StateFlow<ConsumableSettingsState> = _state.asStateFlow()
    
    private val allCategories = ConsumableCategories.STANDARD_CATEGORIES + 
                                 ConsumableCategories.ADDITIONAL_CATEGORIES
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                preferences.selectedCategories.firstOrNull()?.let { selected ->
                    _state.value = _state.value.copy(selectedCategories = selected)
                }
                
                // Загрузить интервалы и объемы для всех категорий
                val intervals = mutableMapOf<String, Pair<Int?, Int?>>()
                val volumes = mutableMapOf<String, Double?>()
                
                allCategories.forEach { category ->
                    val mileage = preferences.getIntervalMileage(category).firstOrNull()
                    val days = preferences.getIntervalDays(category).firstOrNull()
                    val volume = preferences.getVolume(category).firstOrNull()
                    
                    intervals[category] = Pair(mileage, days)
                    if (ConsumableCategories.FLUID_CATEGORIES.contains(category)) {
                        volumes[category] = volume
                    }
                }
                
                _state.value = _state.value.copy(
                    categoryIntervals = intervals,
                    categoryVolumes = volumes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun toggleCategory(category: String) {
        val current = _state.value.selectedCategories
        _state.value = _state.value.copy(
            selectedCategories = if (current.contains(category)) {
                current - category
            } else {
                current + category
            }
        )
    }
    
    fun updateIntervalMileage(category: String, mileage: String) {
        val currentIntervals = _state.value.categoryIntervals.toMutableMap()
        val current = currentIntervals[category] ?: Pair(null, null)
        currentIntervals[category] = Pair(mileage.toIntOrNull(), current.second)
        _state.value = _state.value.copy(categoryIntervals = currentIntervals)
    }
    
    fun updateIntervalDays(category: String, days: String) {
        val currentIntervals = _state.value.categoryIntervals.toMutableMap()
        val current = currentIntervals[category] ?: Pair(null, null)
        currentIntervals[category] = Pair(current.first, days.toIntOrNull())
        _state.value = _state.value.copy(categoryIntervals = currentIntervals)
    }
    
    fun updateVolume(category: String, volume: String) {
        val currentVolumes = _state.value.categoryVolumes.toMutableMap()
        currentVolumes[category] = volume.toDoubleOrNull()
        _state.value = _state.value.copy(categoryVolumes = currentVolumes)
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isSaving = true, error = null)
                
                // Сохранить выбранные категории
                preferences.saveSelectedCategories(_state.value.selectedCategories)
                
                // Сохранить интервалы
                _state.value.categoryIntervals.forEach { (category, intervals) ->
                    preferences.saveIntervalMileage(category, intervals.first)
                    preferences.saveIntervalDays(category, intervals.second)
                }
                
                // Сохранить объемы
                _state.value.categoryVolumes.forEach { (category, volume) ->
                    preferences.saveVolume(category, volume)
                }
                
                // Отметить, что первый запуск завершен
                preferences.setFirstLaunchCompleted(carId)
                
                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }
    
    fun getInterval(category: String): Pair<String, String> {
        val intervals = _state.value.categoryIntervals[category] ?: Pair(null, null)
        val defaultIntervals = ConsumableCategories.DEFAULT_INTERVALS[category]
        
        val mileage = intervals.first?.toString() 
            ?: defaultIntervals?.first?.toString() 
            ?: ""
        val days = intervals.second?.toString() 
            ?: defaultIntervals?.second?.toString() 
            ?: ""
        
        return Pair(mileage, days)
    }
    
    fun getVolume(category: String): String {
        return _state.value.categoryVolumes[category]?.toString() ?: ""
    }
}
