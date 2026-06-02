package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.MaintenanceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownsUiState(
    val breakdowns: List<Breakdown> = emptyList(),
    val selectedMaintenanceType: MaintenanceType? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val filteredBreakdowns: List<Breakdown> get() = when (selectedMaintenanceType) {
        null -> breakdowns
        else -> breakdowns.filter { 
            val type = it.maintenanceType?.let { typeStr -> 
                MaintenanceType.fromString(typeStr) 
            } ?: MaintenanceType.REPAIR  // Старые записи без типа = Ремонты
            type == selectedMaintenanceType
        }
    }
}

@HiltViewModel
class BreakdownsViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow(BreakdownsUiState())
    val uiState: StateFlow<BreakdownsUiState> = _uiState.asStateFlow()
    
    init {
        loadBreakdowns()
    }
    
    private fun loadBreakdowns() {
        viewModelScope.launch {
            try {
                breakdownRepository.getBreakdownsByCarId(carId).collect { breakdowns ->
                    _uiState.value = _uiState.value.copy(
                        breakdowns = breakdowns,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun selectMaintenanceType(type: MaintenanceType?) {
        _uiState.value = _uiState.value.copy(selectedMaintenanceType = type)
    }
    
    fun deleteBreakdown(breakdown: Breakdown) {
        viewModelScope.launch {
            try {
                breakdownRepository.deleteBreakdown(breakdown)
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageAfterDelete(breakdown.carId, breakdown.breakdownMileage)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
