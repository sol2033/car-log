package com.carlog.presentation.screens.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PartDetailUiState {
    object Loading : PartDetailUiState()
    data class Success(val part: Part) : PartDetailUiState()
    data class Error(val message: String) : PartDetailUiState()
}

@HiltViewModel
class PartDetailViewModel @Inject constructor(
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PartDetailUiState>(PartDetailUiState.Loading)
    val uiState: StateFlow<PartDetailUiState> = _uiState.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showMarkBrokenDialog = MutableStateFlow(false)
    val showMarkBrokenDialog: StateFlow<Boolean> = _showMarkBrokenDialog.asStateFlow()
    
    private val _showReplacePartDialog = MutableStateFlow(false)
    val showReplacePartDialog: StateFlow<Boolean> = _showReplacePartDialog.asStateFlow()
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L

    /** Текущий пробег машины — предзаполняет поле пробега поломки */
    private val _currentCarMileage = MutableStateFlow(0)
    val currentCarMileage: StateFlow<Int> = _currentCarMileage.asStateFlow()

    init {
        val partId = savedStateHandle.get<Long>("partId") ?: 0L
        loadPart(partId)
        viewModelScope.launch {
            _currentCarMileage.value = carRepository.getCarByIdOnce(carId)?.currentMileage ?: 0
        }
    }
    
    private fun loadPart(partId: Long) {
        viewModelScope.launch {
            try {
                partRepository.getPartById(partId).collect { part ->
                    _uiState.value = if (part != null) {
                        PartDetailUiState.Success(part)
                    } else {
                        PartDetailUiState.Error("Запчасть не найдена")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = PartDetailUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
    
    fun showDeleteConfirmation() {
        _showDeleteDialog.value = true
    }
    
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun deletePart(part: Part, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                partRepository.deletePart(part)
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageAfterDelete(part.carId, part.installMileage)
                // Уведомляем о изменении данных
                dataChangeNotifier.notifyDataChanged()
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = PartDetailUiState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }
    
    fun showMarkBrokenDialog() {
        _showMarkBrokenDialog.value = true
    }
    
    fun dismissMarkBrokenDialog() {
        _showMarkBrokenDialog.value = false
    }
    
    fun showReplacePartDialog() {
        _showReplacePartDialog.value = true
    }
    
    fun dismissReplacePartDialog() {
        _showReplacePartDialog.value = false
    }
    
    /**
     * Отметить запчасть сломанной. Дата и пробег поломки приходят из диалога —
     * раньше здесь молча подставлялись «сейчас» и текущий пробег машины, хотя в списке
     * запчастей их спрашивают у пользователя.
     */
    fun markPartAsBroken(part: Part, breakdownDate: Long, breakdownMileage: Int) {
        viewModelScope.launch {
            try {
                val updatedPart = part.copy(
                    isBroken = true,
                    breakdownDate = breakdownDate,
                    breakdownMileage = breakdownMileage,
                    mileageDriven = breakdownMileage - part.installMileage,
                    updatedAt = System.currentTimeMillis()
                )
                partRepository.updatePart(updatedPart)
                // Уведомляем о изменении данных
                dataChangeNotifier.notifyDataChanged()
                _showMarkBrokenDialog.value = false
            } catch (e: Exception) {
                _uiState.value = PartDetailUiState.Error(e.message ?: "Ошибка обновления")
            }
        }
    }
}
