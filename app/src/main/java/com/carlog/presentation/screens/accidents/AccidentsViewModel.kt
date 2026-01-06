package com.carlog.presentation.screens.accidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.AccidentRepository
import com.carlog.domain.model.Accident
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccidentsUiState(
    val accidents: List<Accident> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AccidentsViewModel @Inject constructor(
    private val accidentRepository: AccidentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow(AccidentsUiState())
    val uiState: StateFlow<AccidentsUiState> = _uiState.asStateFlow()
    
    init {
        loadAccidents()
    }
    
    private fun loadAccidents() {
        viewModelScope.launch {
            try {
                accidentRepository.getAccidentsByCarId(carId).collect { accidents ->
                    _uiState.value = AccidentsUiState(
                        accidents = accidents,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AccidentsUiState(
                    isLoading = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
    
    fun deleteAccident(accident: Accident) {
        viewModelScope.launch {
            try {
                accidentRepository.deleteAccident(accident)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Ошибка при удалении"
                )
            }
        }
    }
}
