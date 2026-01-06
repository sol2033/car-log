package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.BreakdownRepository
import com.carlog.domain.model.Breakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownsUiState(
    val breakdowns: List<Breakdown> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BreakdownsViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
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
                    _uiState.value = BreakdownsUiState(
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
    
    fun deleteBreakdown(breakdown: Breakdown) {
        viewModelScope.launch {
            try {
                breakdownRepository.deleteBreakdown(breakdown)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
