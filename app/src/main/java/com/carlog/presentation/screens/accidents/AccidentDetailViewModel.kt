package com.carlog.presentation.screens.accidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.AccidentRepository
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Accident
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccidentDetailViewModel @Inject constructor(
    private val accidentRepository: AccidentRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<AccidentDetailState>(AccidentDetailState.Loading)
    val state: StateFlow<AccidentDetailState> = _state.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val accidentId: Long = checkNotNull(savedStateHandle.get<Long>("accidentId"))

    init {
        loadAccident()
    }

    private fun loadAccident() {
        viewModelScope.launch {
            try {
                _state.value = AccidentDetailState.Loading
                accidentRepository.getAccidentById(accidentId).collect { accident ->
                    if (accident != null) {
                        _state.value = AccidentDetailState.Success(accident)
                    } else {
                        _state.value = AccidentDetailState.Error("ДТП не найдено")
                    }
                }
            } catch (e: Exception) {
                _state.value = AccidentDetailState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }

    fun deleteAccident(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                if (_state.value is AccidentDetailState.Success) {
                    val accident = (_state.value as AccidentDetailState.Success).accident
                    accidentRepository.deleteAccident(accident)
                    carRepository.updateCarMileageAfterDelete(accident.carId, accident.mileage)
                    onDeleted()
                }
            } catch (e: Exception) {
                _state.value = AccidentDetailState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }
}

sealed class AccidentDetailState {
    object Loading : AccidentDetailState()
    data class Success(val accident: Accident) : AccidentDetailState()
    data class Error(val message: String) : AccidentDetailState()
}
