package com.carlog.presentation.screens.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PartFilter {
    object All : PartFilter()
    object Active : PartFilter()
    object Broken : PartFilter()
    object OnlyAccident : PartFilter()
    object WithoutAccident : PartFilter()
}

sealed class PartSortOrder {
    object ByInstallDate : PartSortOrder()
    object ByPrice : PartSortOrder()
    object ByMileage : PartSortOrder()
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class PartsUiState(
    val parts: List<Part> = emptyList(),
    val filter: PartFilter = PartFilter.All,
    val sortOrder: PartSortOrder = PartSortOrder.ByInstallDate,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val currentCarMileage: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PartsViewModel @Inject constructor(
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _filter = MutableStateFlow<PartFilter>(PartFilter.All)
    private val _sortOrder = MutableStateFlow<PartSortOrder>(PartSortOrder.ByInstallDate)
    private val _sortDirection = MutableStateFlow<SortDirection>(SortDirection.DESCENDING)
    
    private val _uiState = MutableStateFlow(PartsUiState())
    val uiState: StateFlow<PartsUiState> = _uiState.asStateFlow()
    
    init {
        loadParts()
        loadCarMileage()
    }
    
    private fun loadCarMileage() {
        viewModelScope.launch {
            carRepository.getCarById(carId).collect { car ->
                _uiState.update { it.copy(currentCarMileage = car?.currentMileage ?: 0) }
            }
        }
    }
    
    private fun loadParts() {
        viewModelScope.launch {
            try {
                combine(_filter, _sortOrder, _sortDirection) { filter, sort, direction ->
                    Triple(filter, sort, direction)
                }.flatMapLatest { (filter, sort, direction) ->
                    when (filter) {
                        PartFilter.All -> partRepository.getPartsByCarId(carId)
                        PartFilter.Active -> partRepository.getActivePartsByCarId(carId)
                        PartFilter.Broken -> partRepository.getBrokenPartsByCarId(carId)
                        PartFilter.OnlyAccident -> partRepository.getPartsByCarId(carId).map { parts ->
                            parts.filter { it.installationType == "ДТП" }
                        }
                        PartFilter.WithoutAccident -> partRepository.getPartsByCarId(carId).map { parts ->
                            parts.filter { it.installationType != "ДТП" }
                        }
                    }.map { parts ->
                        val sortedParts = when (sort) {
                            PartSortOrder.ByInstallDate -> {
                                if (direction == SortDirection.DESCENDING) {
                                    parts.sortedByDescending { it.installDate }
                                } else {
                                    parts.sortedBy { it.installDate }
                                }
                            }
                            PartSortOrder.ByPrice -> {
                                if (direction == SortDirection.DESCENDING) {
                                    parts.sortedByDescending { it.price + (it.servicePrice ?: 0.0) }
                                } else {
                                    parts.sortedBy { it.price + (it.servicePrice ?: 0.0) }
                                }
                            }
                            PartSortOrder.ByMileage -> {
                                if (direction == SortDirection.DESCENDING) {
                                    parts.sortedByDescending { it.installMileage }
                                } else {
                                    parts.sortedBy { it.installMileage }
                                }
                            }
                        }
                        sortedParts
                    }
                }.collect { parts ->
                    _uiState.update {
                        it.copy(
                            parts = parts,
                            filter = _filter.value,
                            sortOrder = _sortOrder.value,
                            sortDirection = _sortDirection.value,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun setFilter(filter: PartFilter) {
        _filter.value = filter
    }
    
    fun setSortOrder(sortOrder: PartSortOrder) {
        _sortOrder.value = sortOrder
    }
    
    fun toggleSortDirection() {
        _sortDirection.value = if (_sortDirection.value == SortDirection.ASCENDING) {
            SortDirection.DESCENDING
        } else {
            SortDirection.ASCENDING
        }
    }
    
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    
    fun deletePart(part: Part) {
        viewModelScope.launch {
            try {
                partRepository.deletePart(part)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
