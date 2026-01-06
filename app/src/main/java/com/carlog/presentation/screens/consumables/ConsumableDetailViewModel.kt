package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.domain.model.Consumable
import com.carlog.util.ConsumableStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConsumableDetailUiState {
    object Loading : ConsumableDetailUiState()
    data class Success(
        val consumable: Consumable,
        val statusInfo: ConsumableStatus.StatusInfo,
        val currentMileage: Int
    ) : ConsumableDetailUiState()
    data class Error(val message: String) : ConsumableDetailUiState()
}

@HiltViewModel
class ConsumableDetailViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val consumableId: Long = savedStateHandle.get<Long>("consumableId") ?: 0L
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow<ConsumableDetailUiState>(ConsumableDetailUiState.Loading)
    val uiState: StateFlow<ConsumableDetailUiState> = _uiState.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showReplaceDialog = MutableStateFlow(false)
    val showReplaceDialog: StateFlow<Boolean> = _showReplaceDialog.asStateFlow()
    
    private val _showReplaceSameDialog = MutableStateFlow(false)
    val showReplaceSameDialog: StateFlow<Boolean> = _showReplaceSameDialog.asStateFlow()
    
    init {
        loadConsumable()
    }
    
    private fun loadConsumable() {
        viewModelScope.launch {
            try {
                combine(
                    consumableRepository.getConsumableById(consumableId),
                    carRepository.getCarById(carId)
                ) { consumable, car ->
                    if (consumable != null && car != null) {
                        ConsumableDetailUiState.Success(
                            consumable = consumable,
                            statusInfo = ConsumableStatus.calculateStatus(consumable, car.currentMileage),
                            currentMileage = car.currentMileage
                        )
                    } else {
                        ConsumableDetailUiState.Error("Расходник не найден")
                    }
                }.catch { e ->
                    _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка загрузки")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
    
    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }
    
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun showReplaceDialog() {
        _showReplaceDialog.value = true
    }
    
    fun dismissReplaceDialog() {
        _showReplaceDialog.value = false
    }
    
    fun showReplaceSameDialog() {
        _showReplaceSameDialog.value = true
    }
    
    fun dismissReplaceSameDialog() {
        _showReplaceSameDialog.value = false
    }
    
    fun deleteConsumable(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    consumableRepository.deleteConsumable(state.consumable)
                    // Обновляем пробег автомобиля до максимального
                    val maxMileage = maxOf(
                        state.consumable.installationMileage,
                        state.consumable.replacementMileage ?: 0
                    )
                    carRepository.updateCarMileageAfterDelete(state.consumable.carId, maxMileage)
                    onDeleted()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }
    
    // Замена на тот же расходник с новыми параметрами
    fun replaceSameConsumable(
        newCost: Double?,
        isInstalledAtService: Boolean,
        serviceCost: Double?,
        onReplaced: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    val currentTime = System.currentTimeMillis()
                    val oldConsumable = state.consumable
                    
                    // Обновляем старый расходник
                    val updatedOld = oldConsumable.copy(
                        isActive = false,
                        replacementMileage = state.currentMileage,
                        replacementDate = currentTime,
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(updatedOld)
                    
                    // Создаем новый с новыми параметрами
                    val newConsumable = oldConsumable.copy(
                        id = 0,
                        installationMileage = state.currentMileage,
                        installationDate = currentTime,
                        cost = newCost,
                        isInstalledAtService = isInstalledAtService,
                        serviceCost = serviceCost,
                        replacementMileage = null,
                        replacementDate = null,
                        isActive = true,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    consumableRepository.insertConsumable(newConsumable)
                    
                    // Обновляем пробег автомобиля до максимального
                    carRepository.updateCarMileageIfNeeded(oldConsumable.carId, state.currentMileage)
                    
                    onReplaced()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка замены")
            }
        }
    }
    
    // Замена на другой расходник (просто деактивация)
    fun replaceWithDifferentConsumable(onReplaced: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    val currentTime = System.currentTimeMillis()
                    val updatedConsumable = state.consumable.copy(
                        isActive = false,
                        replacementMileage = state.currentMileage,
                        replacementDate = currentTime,
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(updatedConsumable)
                    // Обновляем пробег автомобиля до максимального
                    carRepository.updateCarMileageIfNeeded(state.consumable.carId, state.currentMileage)
                    onReplaced()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка замены")
            }
        }
    }
}
