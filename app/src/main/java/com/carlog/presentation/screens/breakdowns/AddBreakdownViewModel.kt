package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.PartRepository
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.MaintenanceType
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

// Временная структура для расходника до сохранения в БД
data class TemporaryConsumable(
    val category: String,
    val manufacturer: String?,
    val articleNumber: String?,
    val cost: Double?,
    val isInstalledAtService: Boolean,
    val serviceCost: Double?,
    val volume: Double?,
    val replacementIntervalMileage: Int?,
    val replacementIntervalDays: Int?,
    val notes: String?
) {
    fun toConsumable(
        carId: Long,
        installationMileage: Int,
        installationDate: Long,
        linkedMaintenanceId: Long?
    ): Consumable {
        return Consumable(
            carId = carId,
            category = category,
            manufacturer = manufacturer,
            articleNumber = articleNumber,
            installationMileage = installationMileage,
            installationDate = installationDate,
            linkedMaintenanceId = linkedMaintenanceId,
            replacementMileage = null,
            replacementDate = null,
            cost = cost,
            isInstalledAtService = isInstalledAtService,
            serviceCost = serviceCost,
            volume = volume,
            replacementIntervalMileage = replacementIntervalMileage,
            replacementIntervalDays = replacementIntervalDays,
            isActive = true,
            notes = notes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}

data class AddBreakdownState(
    val carId: Long = 0,
    val breakdownId: Long? = null,
    val maintenanceType: MaintenanceType? = MaintenanceType.REPAIR,
    val title: String = "",
    val description: String = "",
    val breakdownDate: Long = System.currentTimeMillis(),
    val breakdownMileage: String = "",
    val brokenPartName: String = "",
    val isWarrantyRepair: Boolean = false,
    val isServiceMaintenance: Boolean = false,
    val useGeneralPartsCost: Boolean = true,
    val partsCost: String = "",
    val addedParts: List<AddedPart> = emptyList(),
    val temporaryConsumables: List<TemporaryConsumable> = emptyList(),
    val serviceCost: String = "",
    val serviceName: String = "",
    val serviceAddress: String = "",
    val notes: String = "",
    
    val maintenanceTypeError: String? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val breakdownMileageError: String? = null,
    val partsCostError: String? = null,
    val consumablesError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val calculatedPartsCost: Double
        get() = temporaryConsumables.sumOf { it.cost ?: 0.0 }
    
    val isEditMode: Boolean
        get() = breakdownId != null
}

@HiltViewModel
class AddBreakdownViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    private val partRepository: PartRepository,
    private val consumableRepository: ConsumableRepository,
    private val consumablePreferences: ConsumablePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddBreakdownState())
    val state: StateFlow<AddBreakdownState> = _state.asStateFlow()
    
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val breakdownId = savedStateHandle.get<Long>("breakdownId")
        
        _state.value = _state.value.copy(carId = carId)
        
        loadAvailableCategories()
        
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
    
    private fun loadAvailableCategories() {
        viewModelScope.launch {
            consumablePreferences.selectedCategories.collect { selected ->
                // Импортируем категории из ConsumableCategories
                val standardCategories = listOf(
                    "Масло в двигателе",
                    "Фильтр масляный",
                    "Фильтр воздушный",
                    "Фильтр салонный",
                    "Фильтр топливный",
                    "Свечи зажигания",
                    "Тормозная жидкость",
                    "Охлаждающая жидкость",
                    "Жидкость ГУР",
                    "Трансмиссионное масло",
                    "Аккумулятор",
                    "Ремень ГРМ",
                    "Ремень генератора",
                    "Тормозные колодки",
                    "Тормозные диски"
                )
                _availableCategories.value = standardCategories + selected
            }
        }
    }
    
    private fun loadBreakdown(breakdownId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val breakdown = breakdownRepository.getBreakdownById(breakdownId).firstOrNull()
                if (breakdown != null) {
                    // При редактировании ТО расходники уже в БД, не загружаем их в temporaryConsumables
                    // (редактирование расходников в существующем ТО запрещено)
                    
                    _state.value = AddBreakdownState(
                        carId = breakdown.carId,
                        breakdownId = breakdown.id,
                        maintenanceType = breakdown.maintenanceType?.let { MaintenanceType.fromString(it) },
                        title = breakdown.title,
                        description = breakdown.description,
                        breakdownDate = breakdown.breakdownDate,
                        breakdownMileage = breakdown.breakdownMileage.toString(),
                        brokenPartName = breakdown.brokenPartName ?: "",
                        isWarrantyRepair = breakdown.isWarrantyRepair,
                        isServiceMaintenance = breakdown.isServiceMaintenance,
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
    
    fun updateMaintenanceType(type: MaintenanceType?) {
        _state.value = _state.value.copy(
            maintenanceType = type,
            maintenanceTypeError = if (type != null) null else _state.value.maintenanceTypeError,
            // При смене типа на ТО сбрасываем ошибку расходников
            consumablesError = if (type == MaintenanceType.SCHEDULED_SERVICE && _state.value.temporaryConsumables.isNotEmpty()) null else _state.value.consumablesError
        )
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
    
    fun toggleServiceMaintenance(isService: Boolean) {
        _state.value = _state.value.copy(isServiceMaintenance = isService)
    }
    
    fun toggleUseGeneralPartsCost(useGeneral: Boolean) {
        _state.value = _state.value.copy(useGeneralPartsCost = useGeneral)
    }
    
    // Добавить временный расходник (из callback после AddConsumableScreen)
    fun addTemporaryConsumable(consumable: TemporaryConsumable): Boolean {
        // Валидация: проверка уникальности категории
        val existingCategories = _state.value.temporaryConsumables.map { it.category }
        if (existingCategories.contains(consumable.category)) {
            _state.value = _state.value.copy(
                consumablesError = "Расходник категории '${consumable.category}' уже добавлен"
            )
            return false
        }
        
        val updatedConsumables = _state.value.temporaryConsumables + consumable
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
        return true
    }
    
    // Обновить временный расходник
    fun updateTemporaryConsumable(index: Int, consumable: TemporaryConsumable): Boolean {
        val currentConsumables = _state.value.temporaryConsumables
        if (index !in currentConsumables.indices) return false
        
        // Проверка уникальности категории (кроме текущего)
        val otherCategories = currentConsumables.filterIndexed { i, _ -> i != index }.map { it.category }
        if (otherCategories.contains(consumable.category)) {
            _state.value = _state.value.copy(
                consumablesError = "Расходник категории '${consumable.category}' уже добавлен"
            )
            return false
        }
        
        val updatedConsumables = currentConsumables.toMutableList().apply {
            set(index, consumable)
        }
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
        return true
    }
    
    fun removeTemporaryConsumable(index: Int) {
        val updatedConsumables = _state.value.temporaryConsumables.toMutableList().apply {
            removeAt(index)
        }
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
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
        
        val maintenanceTypeError = if (currentState.maintenanceType == null) "Выберите тип обслуживания" else null
        val titleError = if (currentState.title.isBlank()) "Обязательное поле" else null
        val descriptionError = if (currentState.description.isBlank()) "Обязательное поле" else null
        val breakdownMileageError = if (currentState.breakdownMileage.isBlank()) "Обязательное поле" else null
        
        // Для ТО обязательно должен быть минимум 1 расходник
        val consumablesError = if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE && 
                                     currentState.temporaryConsumables.isEmpty()) {
            "Для ТО необходимо добавить минимум 1 расходник"
        } else null
        
        val partsCostError = if (currentState.maintenanceType != MaintenanceType.SCHEDULED_SERVICE) {
            if (currentState.useGeneralPartsCost) {
                if (currentState.partsCost.isBlank()) "Обязательное поле" else null
            } else {
                if (currentState.addedParts.isEmpty()) "Добавьте хотя бы одну запчасть" else null
            }
        } else null
        
        if (maintenanceTypeError != null || titleError != null || descriptionError != null || 
            breakdownMileageError != null || partsCostError != null || consumablesError != null) {
            _state.value = currentState.copy(
                maintenanceTypeError = maintenanceTypeError,
                titleError = titleError,
                descriptionError = descriptionError,
                breakdownMileageError = breakdownMileageError,
                partsCostError = partsCostError,
                consumablesError = consumablesError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val breakdownMileage = currentState.breakdownMileage.toInt()
                
                // Для ТО стоимость запчастей рассчитывается из расходников
                val partsCost = if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                    currentState.calculatedPartsCost
                } else if (currentState.useGeneralPartsCost) {
                    currentState.partsCost.toDouble()
                } else {
                    currentState.addedParts.sumOf { it.price }
                }
                
                val serviceCost = currentState.serviceCost.toDoubleOrNull()
                val totalCost = partsCost + (serviceCost ?: 0.0)
                
                // Создаем расходники в БД ТОЛЬКО для нового ТО (не при редактировании)
                val linkedConsumableIds = mutableListOf<Long>()
                if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE && 
                    currentState.breakdownId == null) { // Только при создании нового ТО
                    
                    // Перед созданием новых расходников, деактивируем старые в тех же категориях
                    for (tempConsumable in currentState.temporaryConsumables) {
                        // Находим расходники в этой категории
                        val consumablesInCategory = consumableRepository.getConsumablesByCategory(
                            currentState.carId, 
                            tempConsumable.category
                        ).firstOrNull() ?: emptyList()
                        
                        // Деактивируем активный расходник этой категории
                        val activeConsumable = consumablesInCategory.firstOrNull { it.isActive }
                        activeConsumable?.let { existing ->
                            consumableRepository.updateConsumable(
                                existing.copy(
                                    isActive = false,
                                    replacementDate = currentState.breakdownDate,
                                    replacementMileage = breakdownMileage,
                                    updatedAt = currentTime
                                )
                            )
                        }
                    }
                    
                    // Теперь создаем новые расходники
                    for (tempConsumable in currentState.temporaryConsumables) {
                        val consumable = tempConsumable.toConsumable(
                            carId = currentState.carId,
                            installationMileage = breakdownMileage,
                            installationDate = currentState.breakdownDate,
                            linkedMaintenanceId = null // Будет установлен после создания breakdown
                        )
                        val consumableId = consumableRepository.insertConsumable(consumable)
                        linkedConsumableIds.add(consumableId)
                    }
                }
                
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
                            maintenanceType = currentState.maintenanceType?.name,
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
                        maintenanceType = currentState.maintenanceType?.name,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        linkedConsumableIds = if (linkedConsumableIds.isNotEmpty()) linkedConsumableIds else null,
                        isServiceMaintenance = currentState.isServiceMaintenance,
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
                    
                    // Обновляем linkedMaintenanceId для новых расходников
                    if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                        linkedConsumableIds.forEach { consumableId ->
                            val consumable = consumableRepository.getConsumableById(consumableId).firstOrNull()
                            consumable?.let {
                                consumableRepository.updateConsumable(
                                    it.copy(linkedMaintenanceId = currentState.breakdownId)
                                )
                            }
                        }
                    }
                } else {
                    val breakdown = Breakdown(
                        carId = currentState.carId,
                        maintenanceType = currentState.maintenanceType?.name,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        linkedConsumableIds = if (linkedConsumableIds.isNotEmpty()) linkedConsumableIds else null,
                        isServiceMaintenance = currentState.isServiceMaintenance,
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
                    val newBreakdownId = breakdownRepository.insertBreakdown(breakdown)
                    
                    // Обновляем linkedMaintenanceId для расходников
                    if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                        linkedConsumableIds.forEach { consumableId ->
                            val consumable = consumableRepository.getConsumableById(consumableId).firstOrNull()
                            consumable?.let {
                                consumableRepository.updateConsumable(
                                    it.copy(linkedMaintenanceId = newBreakdownId)
                                )
                            }
                        }
                    }
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
