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

data class AddPartState(
    val carId: Long = 0,
    val partId: Long? = null,
    val name: String = "",
    val manufacturer: String = "",
    val partNumber: String = "",
    val installDate: Long = System.currentTimeMillis(),
    val installMileage: String = "",
    val installationType: String = "Самостоятельно",
    val price: String = "",
    val servicePrice: String = "",
    val serviceName: String = "",
    val serviceAddress: String = "",
    val photosPaths: List<String> = emptyList(),
    val notes: String = "",
    // Исходная запись при редактировании: поля, которых нет в форме, берутся из неё
    val originalPart: Part? = null,
    
    val nameError: String? = null,
    val installMileageError: String? = null,
    val priceError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddPartViewModel @Inject constructor(
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddPartState())
    val state: StateFlow<AddPartState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val partId = savedStateHandle.get<Long>("partId")
        
        _state.value = _state.value.copy(carId = carId)
        
        // Пробег машины подставляем только для новой запчасти: иначе асинхронная подстановка
        // может выиграть гонку у загрузки и затереть пробег установки существующей записи
        if (partId != null && partId != -1L) {
            loadPart(partId)
        } else {
            viewModelScope.launch {
                val car = carRepository.getCarById(carId).firstOrNull()
                car?.let {
                    _state.value = _state.value.copy(
                        installMileage = it.currentMileage.toString()
                    )
                }
            }
        }
    }
    
    private fun loadPart(partId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val part = partRepository.getPartById(partId).firstOrNull()
                if (part != null) {
                    _state.value = AddPartState(
                        carId = part.carId,
                        partId = part.id,
                        name = part.name,
                        manufacturer = part.manufacturer ?: "",
                        partNumber = part.partNumber ?: "",
                        installDate = part.installDate,
                        installMileage = part.installMileage.toString(),
                        installationType = part.installationType,
                        price = part.price.toString(),
                        servicePrice = part.servicePrice?.toString() ?: "",
                        serviceName = part.serviceName ?: "",
                        serviceAddress = part.serviceAddress ?: "",
                        photosPaths = part.photosPaths ?: emptyList(),
                        notes = part.notes ?: "",
                        originalPart = part,
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Запчасть не найдена"
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
    
    fun updateName(name: String) {
        _state.value = _state.value.copy(
            name = name,
            nameError = if (name.isNotBlank()) null else _state.value.nameError
        )
    }
    
    fun updateManufacturer(manufacturer: String) {
        _state.value = _state.value.copy(manufacturer = manufacturer)
    }
    
    fun updatePartNumber(partNumber: String) {
        _state.value = _state.value.copy(partNumber = partNumber)
    }
    
    fun updateInstallDate(date: Long) {
        _state.value = _state.value.copy(installDate = date)
    }
    
    fun updateInstallMileage(mileage: String) {
        _state.value = _state.value.copy(
            installMileage = mileage,
            installMileageError = if (mileage.isNotBlank()) null else _state.value.installMileageError
        )
    }
    
    fun updateInstallationType(type: String) {
        _state.value = _state.value.copy(installationType = type)
    }
    
    fun updatePrice(price: String) {
        _state.value = _state.value.copy(
            price = price,
            priceError = if (price.isNotBlank()) null else _state.value.priceError
        )
    }
    
    fun updateServicePrice(price: String) {
        _state.value = _state.value.copy(servicePrice = price)
    }
    
    fun updateServiceName(name: String) {
        _state.value = _state.value.copy(serviceName = name)
    }
    
    fun updateServiceAddress(address: String) {
        _state.value = _state.value.copy(serviceAddress = address)
    }
    
    fun addPhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths.toMutableList()
        currentPaths.add(photoPath)
        _state.value = _state.value.copy(photosPaths = currentPaths)
    }
    
    fun removePhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths.toMutableList()
        currentPaths.remove(photoPath)
        _state.value = _state.value.copy(photosPaths = currentPaths)
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun savePart() {
        val currentState = _state.value

        // Запись ещё не догрузилась — сохранять нельзя: обновлять нечего,
        // а вставка создала бы дубликат
        if (currentState.partId != null && currentState.originalPart == null) return

        // Validation
        val nameError = if (currentState.name.isBlank()) "Обязательное поле" else null
        val installMileageError = if (currentState.installMileage.isBlank()) "Обязательное поле" else null
        val priceError = if (currentState.price.isBlank()) "Обязательное поле" else null
        
        if (nameError != null || installMileageError != null || priceError != null) {
            _state.value = currentState.copy(
                nameError = nameError,
                installMileageError = installMileageError,
                priceError = priceError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val servicePriceValue = currentState.servicePrice.toDoubleOrNull()
                
                val original = currentState.originalPart
                if (currentState.partId != null && original != null) {
                    // Обновляем существующую запись через copy: поля, которых нет в форме
                    // (статус поломки, пройденный ресурс, тип обслуживания, createdAt),
                    // должны сохраниться — раньше они собирались заново и обнулялись,
                    // из-за чего редактирование «оживляло» сломанную запчасть
                    val installMileage = currentState.installMileage.toInt()
                    val part = original.copy(
                        carId = currentState.carId,
                        name = currentState.name,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        partNumber = currentState.partNumber.ifBlank { null },
                        installDate = currentState.installDate,
                        installMileage = installMileage,
                        installationType = currentState.installationType,
                        price = currentState.price.toDouble(),
                        servicePrice = servicePriceValue,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        // Пройденный ресурс зависит от пробега установки — пересчитываем
                        mileageDriven = original.breakdownMileage?.let { it - installMileage },
                        updatedAt = currentTime
                    )
                    partRepository.updatePart(part)
                } else {
                    // Insert new part
                    val part = Part(
                        carId = currentState.carId,
                        name = currentState.name,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        partNumber = currentState.partNumber.ifBlank { null },
                        installDate = currentState.installDate,
                        installMileage = currentState.installMileage.toInt(),
                        installationType = currentState.installationType,
                        price = currentState.price.toDouble(),
                        servicePrice = servicePriceValue,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    partRepository.insertPart(part)
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(currentState.carId, currentState.installMileage.toInt())

                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                
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
