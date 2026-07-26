package com.carlog.presentation.screens.cardetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Car
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CarDetailViewModel @Inject constructor(
    private val carRepository: CarRepository,
    private val refuelingRepository: RefuelingRepository,
    private val dataChangeNotifier: DataChangeNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow<CarDetailUiState>(CarDetailUiState.Loading)
    val uiState: StateFlow<CarDetailUiState> = _uiState.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showUpdateMileageDialog = MutableStateFlow(false)
    val showUpdateMileageDialog: StateFlow<Boolean> = _showUpdateMileageDialog.asStateFlow()
    
    private val _showMileageInputDialog = MutableStateFlow(false)
    val showMileageInputDialog: StateFlow<Boolean> = _showMileageInputDialog.asStateFlow()

    // Введённый пробег меньше текущего: ждём подтверждения пользователя (null — подтверждение не нужно)
    private val _lowerMileageConfirmation = MutableStateFlow<LowerMileageConfirmation?>(null)
    val lowerMileageConfirmation: StateFlow<LowerMileageConfirmation?> =
        _lowerMileageConfirmation.asStateFlow()

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
        _lowerMileageConfirmation.value = null
    }

    /**
     * Сохранение пробега из диалога ручного обновления.
     * Пробег больше или равный текущему сохраняется сразу; меньший — только после подтверждения
     * (диалог-предупреждение, см. [confirmLowerMileage]).
     */
    fun submitMileage(car: Car, newMileage: Int) {
        if (newMileage >= car.currentMileage) {
            updateMileage(car.id, newMileage)
            dismissMileageInputDialog()
            return
        }

        viewModelScope.launch {
            try {
                _lowerMileageConfirmation.value = LowerMileageConfirmation(
                    carId = car.id,
                    newMileage = newMileage,
                    currentMileage = car.currentMileage,
                    maxRecordedMileage = carRepository.getMaxRecordedMileage(car.id),
                    affectedRecordsCount = carRepository.getRecordsCountAboveMileage(car.id, newMileage)
                )
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Ошибка при обновлении пробега")
            }
        }
    }

    fun dismissLowerMileageConfirmation() {
        _lowerMileageConfirmation.value = null
    }

    // Пользователь подтвердил уменьшение: пробег записей прижимается к новому значению
    fun confirmLowerMileage() {
        val confirmation = _lowerMileageConfirmation.value ?: return
        viewModelScope.launch {
            try {
                carRepository.lowerMileageWithRecords(confirmation.carId, confirmation.newMileage)
                refuelingRepository.recalculateFuelConsumption(confirmation.carId)
                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                _lowerMileageConfirmation.value = null
                _showMileageInputDialog.value = false
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Ошибка при обновлении пробега")
            }
        }
    }

    fun deleteCar(car: Car, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                carRepository.deleteCar(car)
                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
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
                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
            } catch (e: Exception) {
                _uiState.value = CarDetailUiState.Error(e.message ?: "Ошибка при обновлении пробега")
            }
        }
    }
}

/**
 * Данные для предупреждения об уменьшении пробега.
 * @property affectedRecordsCount сколько записей машины имеют пробег больше нового значения —
 * их пробег будет уменьшен до него.
 */
data class LowerMileageConfirmation(
    val carId: Long,
    val newMileage: Int,
    val currentMileage: Int,
    val maxRecordedMileage: Int,
    val affectedRecordsCount: Int
)

sealed class CarDetailUiState {
    object Loading : CarDetailUiState()
    data class Success(val car: Car) : CarDetailUiState()
    data class Error(val message: String) : CarDetailUiState()
}
