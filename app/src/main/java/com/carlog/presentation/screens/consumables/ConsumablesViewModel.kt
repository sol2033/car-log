package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.util.ConsumableStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsumableWithStatus(
    val category: String,
    val consumable: Consumable?,
    val statusInfo: ConsumableStatus.StatusInfo?
)

sealed class ConsumablesUiState {
    object Loading : ConsumablesUiState()
    data class Success(
        val activeConsumables: List<ConsumableWithStatus>,
        val currentMileage: Int
    ) : ConsumablesUiState()
    data class Error(val message: String) : ConsumablesUiState()
}

@HiltViewModel
class ConsumablesViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    private val carRepository: CarRepository,
    private val preferences: ConsumablePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow<ConsumablesUiState>(ConsumablesUiState.Loading)
    val uiState: StateFlow<ConsumablesUiState> = _uiState.asStateFlow()
    
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()
    
    private val _showFirstLaunchDialog = MutableStateFlow(false)
    val showFirstLaunchDialog: StateFlow<Boolean> = _showFirstLaunchDialog.asStateFlow()
    
    init {
        loadConsumables()
        loadAvailableCategories()
        checkFirstLaunch()
    }
    
    private fun checkFirstLaunch() {
        viewModelScope.launch {
            preferences.isFirstLaunch(carId).collect { isFirst ->
                _showFirstLaunchDialog.value = isFirst
            }
        }
    }
    
    private fun loadAvailableCategories() {
        viewModelScope.launch {
            preferences.selectedCategories.collect { selected ->
                _availableCategories.value = ConsumableCategories.STANDARD_CATEGORIES + selected
            }
        }
    }
    
    private fun loadConsumables() {
        viewModelScope.launch {
            try {
                combine(
                    consumableRepository.getActiveConsumablesByCarId(carId),
                    carRepository.getCarById(carId),
                    preferences.selectedCategories
                ) { consumables, car, selectedCategories ->
                    val currentMileage = car?.currentMileage ?: 0
                    val allCategories = ConsumableCategories.STANDARD_CATEGORIES + selectedCategories
                    
                    // Группируем расходники по категориям
                    val consumablesByCategory = consumables.groupBy { it.category }
                    
                    // Создаем карточки для всех категорий
                    val consumablesWithStatus = allCategories.map { category ->
                        val consumable = consumablesByCategory[category]?.firstOrNull()
                        ConsumableWithStatus(
                            category = category,
                            consumable = consumable,
                            statusInfo = consumable?.let { 
                                ConsumableStatus.calculateStatus(it, currentMileage)
                            }
                        )
                    }
                    
                    ConsumablesUiState.Success(
                        activeConsumables = consumablesWithStatus,
                        currentMileage = currentMileage
                    )
                }.catch { e ->
                    _uiState.value = ConsumablesUiState.Error(e.message ?: "Ошибка загрузки")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ConsumablesUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
}
