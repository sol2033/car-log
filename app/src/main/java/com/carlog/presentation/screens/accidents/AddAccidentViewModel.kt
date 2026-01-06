package com.carlog.presentation.screens.accidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.AccidentRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddedPart(
    val name: String,
    val manufacturer: String = "",
    val price: Double
)

data class AddAccidentState(
    val carId: Long = 0,
    val accidentId: Long? = null,
    
    val date: Long = System.currentTimeMillis(),
    val mileage: String = "",
    val location: String = "",
    val damageDescription: String = "",
    val severity: String = "Средняя",
    val isUserAtFault: Boolean = false,
    
    // Выплаты
    val hasOsagoPayout: Boolean = false,
    val osagoPayout: String = "",
    val hasKaskoPayout: Boolean = false,
    val kaskoPayout: String = "",
    val hasCulpritPayout: Boolean = false,
    val culpritPayout: String = "",
    
    // Ремонт
    val usePartsForRepair: Boolean = true, // true = добавлять запчасти, false = общая стоимость
    val addedParts: List<AddedPart> = emptyList(),
    val serviceCost: String = "",
    val totalRepairCost: String = "",
    
    // Медиа
    val photosPaths: List<String> = emptyList(),
    val documentPath: String? = null,
    
    val notes: String = "",
    
    // Validation errors
    val mileageError: String? = null,
    val damageDescriptionError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddAccidentViewModel @Inject constructor(
    private val accidentRepository: AccidentRepository,
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddAccidentState())
    val state: StateFlow<AddAccidentState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val accidentId = savedStateHandle.get<Long>("accidentId")
        
        _state.value = _state.value.copy(carId = carId)
        
        // Load current mileage
        viewModelScope.launch {
            val car = carRepository.getCarById(carId).firstOrNull()
            car?.let {
                _state.value = _state.value.copy(mileage = it.currentMileage.toString())
            }
        }
        
        // Load accident if editing
        if (accidentId != null && accidentId != -1L) {
            loadAccident(accidentId)
        }
    }
    
    private fun loadAccident(accidentId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val accident = accidentRepository.getAccidentById(accidentId).firstOrNull()
                if (accident != null) {
                    _state.value = AddAccidentState(
                        carId = accident.carId,
                        accidentId = accident.id,
                        date = accident.date,
                        mileage = accident.mileage.toString(),
                        location = accident.location ?: "",
                        damageDescription = accident.damageDescription,
                        severity = accident.severity,
                        isUserAtFault = accident.isUserAtFault,
                        hasOsagoPayout = accident.osagoPayout != null,
                        osagoPayout = accident.osagoPayout?.toString() ?: "",
                        hasKaskoPayout = accident.kaskoPayout != null,
                        kaskoPayout = accident.kaskoPayout?.toString() ?: "",
                        hasCulpritPayout = accident.culpritPayout != null,
                        culpritPayout = accident.culpritPayout?.toString() ?: "",
                        usePartsForRepair = accident.installedPartIds != null,
                        totalRepairCost = accident.repairCost?.toString() ?: "",
                        photosPaths = accident.photosPaths ?: emptyList(),
                        documentPath = accident.documentPath,
                        notes = accident.notes ?: "",
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ДТП не найдено"
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
    
    fun updateDate(date: Long) {
        _state.value = _state.value.copy(date = date)
    }
    
    fun updateMileage(mileage: String) {
        _state.value = _state.value.copy(
            mileage = mileage,
            mileageError = if (mileage.isNotBlank()) null else _state.value.mileageError
        )
    }
    
    fun updateLocation(location: String) {
        _state.value = _state.value.copy(location = location)
    }
    
    fun updateDamageDescription(description: String) {
        _state.value = _state.value.copy(
            damageDescription = description,
            damageDescriptionError = if (description.isNotBlank()) null else _state.value.damageDescriptionError
        )
    }
    
    fun updateSeverity(severity: String) {
        _state.value = _state.value.copy(severity = severity)
    }
    
    fun toggleUserAtFault(isAtFault: Boolean) {
        val currentState = _state.value
        _state.value = currentState.copy(
            isUserAtFault = isAtFault,
            // Если пользователь виновник, убираем ОСАГО и выплату от виновника
            hasOsagoPayout = if (isAtFault) false else currentState.hasOsagoPayout,
            osagoPayout = if (isAtFault) "" else currentState.osagoPayout,
            hasCulpritPayout = if (isAtFault) false else currentState.hasCulpritPayout,
            culpritPayout = if (isAtFault) "" else currentState.culpritPayout
        )
    }
    
    fun toggleOsagoPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasOsagoPayout = has,
            osagoPayout = if (!has) "" else _state.value.osagoPayout
        )
    }
    
    fun updateOsagoPayout(amount: String) {
        _state.value = _state.value.copy(osagoPayout = amount)
    }
    
    fun toggleKaskoPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasKaskoPayout = has,
            kaskoPayout = if (!has) "" else _state.value.kaskoPayout
        )
    }
    
    fun updateKaskoPayout(amount: String) {
        _state.value = _state.value.copy(kaskoPayout = amount)
    }
    
    fun toggleCulpritPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasCulpritPayout = has,
            culpritPayout = if (!has) "" else _state.value.culpritPayout
        )
    }
    
    fun updateCulpritPayout(amount: String) {
        _state.value = _state.value.copy(culpritPayout = amount)
    }
    
    fun toggleRepairMethod(usePartsForRepair: Boolean) {
        _state.value = _state.value.copy(usePartsForRepair = usePartsForRepair)
    }
    
    fun addPart(name: String, manufacturer: String, price: Double) {
        val currentState = _state.value
        _state.value = currentState.copy(
            addedParts = currentState.addedParts + AddedPart(name, manufacturer, price)
        )
    }
    
    fun removePart(part: AddedPart) {
        val currentState = _state.value
        _state.value = currentState.copy(
            addedParts = currentState.addedParts - part
        )
    }
    
    fun updateServiceCost(cost: String) {
        _state.value = _state.value.copy(serviceCost = cost)
    }
    
    fun updateTotalRepairCost(cost: String) {
        _state.value = _state.value.copy(totalRepairCost = cost)
    }
    
    fun addPhoto(photoPath: String) {
        val currentState = _state.value
        _state.value = currentState.copy(
            photosPaths = currentState.photosPaths + photoPath
        )
    }
    
    fun removePhoto(photoPath: String) {
        val currentState = _state.value
        _state.value = currentState.copy(
            photosPaths = currentState.photosPaths - photoPath
        )
    }
    
    fun updateDocumentPath(path: String?) {
        _state.value = _state.value.copy(documentPath = path)
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun saveAccident() {
        val currentState = _state.value
        
        // Validation
        val mileageError = if (currentState.mileage.isBlank()) "Обязательное поле" else null
        val damageDescriptionError = if (currentState.damageDescription.isBlank()) "Обязательное поле" else null
        
        if (mileageError != null || damageDescriptionError != null) {
            _state.value = currentState.copy(
                mileageError = mileageError,
                damageDescriptionError = damageDescriptionError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                
                // Рассчитываем стоимость ремонта
                val repairCost = if (currentState.usePartsForRepair) {
                    val partsCost = currentState.addedParts.sumOf { it.price }
                    val serviceCostValue = currentState.serviceCost.toDoubleOrNull() ?: 0.0
                    partsCost + serviceCostValue
                } else {
                    currentState.totalRepairCost.toDoubleOrNull()
                }
                
                // Создаем или обновляем запчасти
                val installedPartIds = if (currentState.usePartsForRepair && currentState.addedParts.isNotEmpty()) {
                    currentState.addedParts.map { addedPart ->
                        val part = Part(
                            carId = currentState.carId,
                            name = addedPart.name,
                            manufacturer = addedPart.manufacturer.ifBlank { null },
                            partNumber = null,
                            installDate = currentState.date,
                            installMileage = currentState.mileage.toInt(),
                            installationType = "ДТП",
                            price = addedPart.price,
                            servicePrice = null,
                            notes = "Установлена после ДТП ${formatDate(currentState.date)}",
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )
                        partRepository.insertPart(part)
                    }
                } else null
                
                val accident = Accident(
                    id = currentState.accidentId ?: 0,
                    carId = currentState.carId,
                    date = currentState.date,
                    mileage = currentState.mileage.toInt(),
                    location = currentState.location.ifBlank { null },
                    damageDescription = currentState.damageDescription,
                    severity = currentState.severity,
                    isUserAtFault = currentState.isUserAtFault,
                    osagoPayout = if (currentState.hasOsagoPayout) currentState.osagoPayout.toDoubleOrNull() else null,
                    kaskoPayout = if (currentState.hasKaskoPayout) currentState.kaskoPayout.toDoubleOrNull() else null,
                    culpritPayout = if (currentState.hasCulpritPayout) currentState.culpritPayout.toDoubleOrNull() else null,
                    installedPartIds = installedPartIds,
                    repairCost = repairCost,
                    photosPaths = currentState.photosPaths.ifEmpty { null },
                    documentPath = currentState.documentPath,
                    notes = currentState.notes.ifBlank { null },
                    createdAt = if (currentState.accidentId != null) currentState.accidentId else currentTime,
                    updatedAt = currentTime
                )
                
                if (currentState.accidentId != null) {
                    accidentRepository.updateAccident(accident)
                } else {
                    accidentRepository.insertAccident(accident)
                }
                
                carRepository.updateCarMileageIfNeeded(currentState.carId, currentState.mileage.toInt())
                
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
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
