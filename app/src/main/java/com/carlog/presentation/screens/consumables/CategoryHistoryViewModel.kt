package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.ConsumableRepository
import com.carlog.domain.model.Consumable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder {
    DATE_ASC,
    DATE_DESC,
    MILEAGE_ASC,
    MILEAGE_DESC
}

sealed class CategoryHistoryUiState {
    object Loading : CategoryHistoryUiState()
    data class Success(
        val consumables: List<Consumable>,
        val sortOrder: SortOrder
    ) : CategoryHistoryUiState()
    data class Error(val message: String) : CategoryHistoryUiState()
}

@HiltViewModel
class CategoryHistoryViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val category: String = java.net.URLDecoder.decode(
        savedStateHandle.get<String>("category") ?: "",
        "UTF-8"
    )
    
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    
    private val _uiState = MutableStateFlow<CategoryHistoryUiState>(CategoryHistoryUiState.Loading)
    val uiState: StateFlow<CategoryHistoryUiState> = _uiState.asStateFlow()
    
    init {
        loadConsumables()
    }
    
    private fun loadConsumables() {
        viewModelScope.launch {
            try {
                combine(
                    consumableRepository.getConsumablesByCategory(carId, category),
                    _sortOrder
                ) { consumables, order ->
                    val sorted = when (order) {
                        SortOrder.DATE_ASC -> consumables.sortedBy { it.installationDate }
                        SortOrder.DATE_DESC -> consumables.sortedByDescending { it.installationDate }
                        SortOrder.MILEAGE_ASC -> consumables.sortedBy { it.installationMileage }
                        SortOrder.MILEAGE_DESC -> consumables.sortedByDescending { it.installationMileage }
                    }
                    CategoryHistoryUiState.Success(
                        consumables = sorted,
                        sortOrder = order
                    )
                }.catch { e ->
                    _uiState.value = CategoryHistoryUiState.Error(e.message ?: "Ошибка загрузки")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = CategoryHistoryUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
    
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }
}
