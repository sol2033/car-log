package com.carlog.presentation.screens.cardetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Car
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CarDetailViewModel @Inject constructor(
    private val carRepository: CarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CarDetailUiState>(CarDetailUiState.Loading)
    val uiState: StateFlow<CarDetailUiState> = _uiState.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showUpdateMileageDialog = MutableStateFlow(false)
    val showUpdateMileageDialog: StateFlow<Boolean> = _showUpdateMileageDialog.asStateFlow()
    
    private val _showMileageInputDialog = MutableStateFlow(false)
    val showMileageInputDialog: StateFlow<Boolean> = _showMileageInputDialog.asStateFlow()

    fun loadCar(carId: Long) {
        viewModelScope.launch {
            try {
                carRepository.getCarById(carId).collect { car ->
                    if (car != null) {
                        _uiState.value = CarDetailUiState.Success(car)
                    } else {
                        _uiState.value = CarDetailUiState.Error("Автомобиль не найден")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun showDeleteConfirmation() {
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun showUpdateMileageDialog() {
        _showUpdateMileageDialog.value = true
    }
    
    fun dismissUpdateMileageDialog() {
        _showUpdateMileageDialog.value = false
    }
    
    fun showMileageInputDialog() {
        _showMileageInputDialog.value = true
    }
    
    fun dismissMileageInputDialog() {
        _showMileageInputDialog.value = false
    }

    fun deleteCar(car: Car, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                carRepository.deleteCar(car)
                _showDeleteDialog.value = false
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Ошибка при удалении")
            }
        }
    }

    fun updateMileage(carId: Long, newMileage: Int) {
        viewModelScope.launch {
            try {
                carRepository.updateMileage(carId, newMileage)
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Ошибка при обновлении пробега")
            }
        }
    }
}

sealed class CarDetailUiState {
    object Loading : CarDetailUiState()
    data class Success(val car: Car) : CarDetailUiState()
    data class Error(val message: String) : CarDetailUiState()
}
