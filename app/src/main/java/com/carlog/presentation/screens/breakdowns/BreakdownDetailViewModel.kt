package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Breakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BreakdownDetailUiState {
    object Loading : BreakdownDetailUiState()
    data class Success(val breakdown: Breakdown) : BreakdownDetailUiState()
    data class Error(val message: String) : BreakdownDetailUiState()
}

@HiltViewModel
class BreakdownDetailViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BreakdownDetailUiState>(BreakdownDetailUiState.Loading)
    val uiState: StateFlow<BreakdownDetailUiState> = _uiState.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    init {
        val breakdownId = savedStateHandle.get<Long>("breakdownId") ?: 0L
        loadBreakdown(breakdownId)
    }
    
    private fun loadBreakdown(breakdownId: Long) {
        viewModelScope.launch {
            try {
                breakdownRepository.getBreakdownById(breakdownId).collect { breakdown ->
                    if (breakdown != null) {
                        _uiState.value = BreakdownDetailUiState.Success(breakdown)
                    } else {
                        _uiState.value = BreakdownDetailUiState.Error("Поломка не найдена")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = BreakdownDetailUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
    
    fun showDeleteConfirmation() {
        _showDeleteDialog.value = true
    }
    
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun deleteBreakdown(breakdown: Breakdown, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                breakdownRepository.deleteBreakdown(breakdown)
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageAfterDelete(breakdown.carId, breakdown.breakdownMileage)
                dismissDeleteDialog()
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = BreakdownDetailUiState.Error(e.message ?: "Ошибка при удалении")
            }
        }
    }
}
