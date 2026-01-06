package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddedPart(
    val name: String = "",
    val price: Double = 0.0
)

data class AddBreakdownState(
    val carId: Long = 0,
    val breakdownId: Long? = null,
    val title: String = "",
    val description: String = "",
    val breakdownDate: Long = System.currentTimeMillis(),
    val breakdownMileage: String = "",
    val brokenPartName: String = "",
    val isWarrantyRepair: Boolean = false,
    val useGeneralPartsCost: Boolean = true,
    val partsCost: String = "",
    val addedParts: List<AddedPart> = emptyList(),
    val serviceCost: String = "",
    val serviceName: String = "",
    val serviceAddress: String = "",
    val notes: String = "",
    
    val titleError: String? = null,
    val descriptionError: String? = null,
    val breakdownMileageError: String? = null,
    val partsCostError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddBreakdownViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    private val partRepository: PartRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddBreakdownState())
    val state: StateFlow<AddBreakdownState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val breakdownId = savedStateHandle.get<Long>("breakdownId")
        
        _state.value = _state.value.copy(carId = carId)
        
        viewModelScope.launch {
            val car = carRepository.getCarById(carId).firstOrNull()
            car?.let {
                _state.value = _state.value.copy(
                    breakdownMileage = it.currentMileage.toString()
                )
            }
        }
        
        if (breakdownId != null && breakdownId != -1L) {
            loadBreakdown(breakdownId)
        }
    }
    
    private fun loadBreakdown(breakdownId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val breakdown = breakdownRepository.getBreakdownById(breakdownId).firstOrNull()
                if (breakdown != null) {
                    _state.value = AddBreakdownState(
                        carId = breakdown.carId,
                        breakdownId = breakdown.id,
                        title = breakdown.title,
                        description = breakdown.description,
                        breakdownDate = breakdown.breakdownDate,
                        breakdownMileage = breakdown.breakdownMileage.toString(),
                        brokenPartName = breakdown.brokenPartName ?: "",
                        isWarrantyRepair = breakdown.isWarrantyRepair,
                        partsCost = breakdown.partsCost.toString(),
                        serviceCost = breakdown.serviceCost?.toString() ?: "",
                        serviceName = breakdown.serviceName ?: "",
                        serviceAddress = breakdown.serviceAddress ?: "",
                        notes = breakdown.notes ?: "",
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Поломка не найдена"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun updateTitle(title: String) {
        _state.value = _state.value.copy(
            title = title,
            titleError = if (title.isNotBlank()) null else _state.value.titleError
        )
    }
    
    fun updateDescription(description: String) {
        _state.value = _state.value.copy(
            description = description,
            descriptionError = if (description.isNotBlank()) null else _state.value.descriptionError
        )
    }
    
    fun updateBreakdownDate(date: Long) {
        _state.value = _state.value.copy(breakdownDate = date)
    }
    
    fun updateBreakdownMileage(mileage: String) {
        _state.value = _state.value.copy(
            breakdownMileage = mileage,
            breakdownMileageError = if (mileage.isNotBlank()) null else _state.value.breakdownMileageError
        )
    }
    
    fun updateBrokenPartName(name: String) {
        _state.value = _state.value.copy(brokenPartName = name)
    }
    
    fun updatePartsCost(cost: String) {
        _state.value = _state.value.copy(
            partsCost = cost,
            partsCostError = if (cost.isNotBlank()) null else _state.value.partsCostError
        )
    }
    
    fun updateServiceCost(cost: String) {
        _state.value = _state.value.copy(serviceCost = cost)
    }
    
    fun updateServiceName(name: String) {
        _state.value = _state.value.copy(serviceName = name)
    }
    
    fun updateServiceAddress(address: String) {
        _state.value = _state.value.copy(serviceAddress = address)
    }
    
    fun toggleWarrantyRepair(isWarranty: Boolean) {
        _state.value = _state.value.copy(isWarrantyRepair = isWarranty)
    }
    
    fun toggleUseGeneralPartsCost(useGeneral: Boolean) {
        _state.value = _state.value.copy(useGeneralPartsCost = useGeneral)
    }
    
    fun addPart(name: String, price: Double) {
        if (name.isNotBlank() && price > 0) {
            val updatedParts = _state.value.addedParts + AddedPart(name, price)
            _state.value = _state.value.copy(addedParts = updatedParts)
        }
    }
    
    fun removePart(index: Int) {
        val updatedParts = _state.value.addedParts.toMutableList().apply {
            removeAt(index)
        }
        _state.value = _state.value.copy(addedParts = updatedParts)
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun saveBreakdown() {
        val currentState = _state.value
        
        val titleError = if (currentState.title.isBlank()) "Обязательное поле" else null
        val descriptionError = if (currentState.description.isBlank()) "Обязательное поле" else null
        val breakdownMileageError = if (currentState.breakdownMileage.isBlank()) "Обязательное поле" else null
        
        val partsCostError = if (currentState.useGeneralPartsCost) {
            if (currentState.partsCost.isBlank()) "Обязательное поле" else null
        } else {
            if (currentState.addedParts.isEmpty()) "Добавьте хотя бы одну запчасть" else null
        }
        
        if (titleError != null || descriptionError != null || breakdownMileageError != null || partsCostError != null) {
            _state.value = currentState.copy(
                titleError = titleError,
                descriptionError = descriptionError,
                breakdownMileageError = breakdownMileageError,
                partsCostError = partsCostError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val breakdownMileage = currentState.breakdownMileage.toInt()
                
                // Рассчитываем стоимость запчастей
                val partsCost = if (currentState.useGeneralPartsCost) {
                    currentState.partsCost.toDouble()
                } else {
                    currentState.addedParts.sumOf { it.price }
                }
                
                val serviceCost = currentState.serviceCost.toDoubleOrNull()
                val totalCost = partsCost + (serviceCost ?: 0.0)
                
                // Если используются конкретные запчасти, добавляем их в БД
                val addedPartIds = mutableListOf<Long>()
                if (!currentState.useGeneralPartsCost) {
                    for (addedPart in currentState.addedParts) {
                        val part = Part(
                            carId = currentState.carId,
                            name = addedPart.name,
                            installDate = currentState.breakdownDate,
                            installMileage = breakdownMileage,
                            installationType = "Сервис",
                            price = addedPart.price,
                            servicePrice = null,
                            isBroken = false,
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )
                        val partId = partRepository.insertPart(part)
                        addedPartIds.add(partId)
                    }
                }
                
                if (currentState.breakdownId != null) {
                    val breakdown = Breakdown(
                        id = currentState.breakdownId,
                        carId = currentState.carId,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        partsCost = partsCost,
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        notes = currentState.notes.ifBlank { null },
                        updatedAt = currentTime
                    )
                    breakdownRepository.updateBreakdown(breakdown)
                } else {
                    val breakdown = Breakdown(
                        carId = currentState.carId,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        partsCost = partsCost,
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    breakdownRepository.insertBreakdown(breakdown)
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(currentState.carId, currentState.breakdownMileage.toInt())
                
                _state.value = currentState.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }
}
