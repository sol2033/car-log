package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.ConsumableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ConsumablesHistoryState(
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ConsumablesHistoryViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _state = MutableStateFlow(ConsumablesHistoryState())
    val state: StateFlow<ConsumablesHistoryState> = _state.asStateFlow()
    
    init {
        loadCategories()
    }
    
    private fun loadCategories() {
        consumableRepository.getActiveConsumablesByCarId(carId)
            .map { consumables ->
                // Получаем уникальные категории из активных расходников
                val categories = consumables.map { it.category }.distinct().sorted()
                ConsumablesHistoryState(
                    categories = categories,
                    isLoading = false
                )
            }
            .catch { _ ->
                _state.value = ConsumablesHistoryState(
                    categories = emptyList(),
                    isLoading = false
                )
            }
            .onEach { state ->
                _state.value = state
            }
            .launchIn(viewModelScope)
    }
}
