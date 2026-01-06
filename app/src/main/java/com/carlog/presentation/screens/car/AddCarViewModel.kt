package com.carlog.presentation.screens.car

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Car
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddCarState(
    val carId: Long? = null,
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val licensePlate: String = "",
    val vin: String = "",
    val engineModel: String = "",
    val engineVolume: String = "",
    val transmissionType: String = "",
    val driveType: String = "",
    val bodyType: String = "",
    val fuelType: String = "Бензин",
    val hasGasEquipment: Boolean = false,
    val gasType: String? = null,
    val currentMileage: String = "",
    val purchaseMileage: String = "",
    val photosPaths: List<String> = emptyList(),
    val mainPhotoPath: String? = null,
    val notes: String = "",
    
    val brandError: String? = null,
    val modelError: String? = null,
    val fuelTypeError: String? = null,
    val mileageError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddCarViewModel @Inject constructor(
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddCarState())
    val state: StateFlow<AddCarState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId")
        if (carId != null && carId != -1L) {
            loadCar(carId)
        }
    }
    
    private fun loadCar(carId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                carRepository.getCarById(carId).collect { car ->
                    if (car != null) {
                        _state.value = AddCarState(
                            carId = car.id,
                            brand = car.brand,
                            model = car.model,
                            year = car.year?.toString() ?: "",
                            color = car.color ?: "",
                            licensePlate = car.licensePlate ?: "",
                            vin = car.vin ?: "",
                            engineModel = car.engineModel ?: "",
                            engineVolume = car.engineVolume?.toString() ?: "",
                            transmissionType = car.transmissionType ?: "",
                            driveType = car.driveType ?: "",
                            bodyType = car.bodyType ?: "",
                            fuelType = car.fuelType,
                            hasGasEquipment = car.hasGasEquipment,
                            gasType = car.gasType,
                            currentMileage = car.currentMileage.toString(),
                            purchaseMileage = car.purchaseMileage?.toString() ?: "",
                            photosPaths = car.photosPaths ?: emptyList(),
                            mainPhotoPath = car.mainPhotoPath,
                            notes = car.notes ?: "",
                            isLoading = false
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Автомобиль не найден"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun updateBrand(brand: String) {
        _state.value = _state.value.copy(
            brand = brand,
            brandError = if (brand.isNotBlank()) null else _state.value.brandError
        )
    }
    
    fun updateModel(model: String) {
        _state.value = _state.value.copy(
            model = model,
            modelError = if (model.isNotBlank()) null else _state.value.modelError
        )
    }
    
    fun updateYear(year: String) {
        _state.value = _state.value.copy(year = year)
    }
    
    fun updateColor(color: String) {
        _state.value = _state.value.copy(color = color)
    }
    
    fun updateLicensePlate(licensePlate: String) {
        _state.value = _state.value.copy(licensePlate = licensePlate)
    }
    
    fun updateVin(vin: String) {
        _state.value = _state.value.copy(vin = vin)
    }
    
    fun updateEngineModel(engineModel: String) {
        _state.value = _state.value.copy(engineModel = engineModel)
    }
    
    fun updateEngineVolume(engineVolume: String) {
        _state.value = _state.value.copy(engineVolume = engineVolume)
    }
    
    fun updateTransmissionType(transmissionType: String) {
        _state.value = _state.value.copy(transmissionType = transmissionType)
    }
    
    fun updateDriveType(driveType: String) {
        _state.value = _state.value.copy(driveType = driveType)
    }
    
    fun updateBodyType(bodyType: String) {
        _state.value = _state.value.copy(bodyType = bodyType)
    }
    
    fun updateFuelType(fuelType: String) {
        _state.value = _state.value.copy(
            fuelType = fuelType,
            fuelTypeError = if (fuelType.isNotBlank()) null else _state.value.fuelTypeError
        )
    }
    
    fun updateHasGasEquipment(hasGas: Boolean) {
        _state.value = _state.value.copy(
            hasGasEquipment = hasGas,
            gasType = if (!hasGas) null else _state.value.gasType // Сбрасываем тип газа если убрали ГБО
        )
    }
    
    fun updateGasType(gasType: String) {
        _state.value = _state.value.copy(gasType = gasType)
    }
    
    fun updateCurrentMileage(mileage: String) {
        _state.value = _state.value.copy(
            currentMileage = mileage,
            mileageError = if (mileage.isNotBlank()) null else _state.value.mileageError
        )
    }
    
    fun updatePurchaseMileage(mileage: String) {
        _state.value = _state.value.copy(purchaseMileage = mileage)
    }
    
    fun addPhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths.toMutableList()
        currentPaths.add(photoPath)
        _state.value = _state.value.copy(
            photosPaths = currentPaths,
            mainPhotoPath = _state.value.mainPhotoPath ?: photoPath // Первое фото становится основным
        )
    }
    
    fun removePhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths.toMutableList()
        currentPaths.remove(photoPath)
        
        val newMainPhoto = if (_state.value.mainPhotoPath == photoPath) {
            currentPaths.firstOrNull() // Если удалили основное, берем первое
        } else {
            _state.value.mainPhotoPath
        }
        
        _state.value = _state.value.copy(
            photosPaths = currentPaths,
            mainPhotoPath = newMainPhoto
        )
    }
    
    fun setMainPhoto(photoPath: String) {
        _state.value = _state.value.copy(mainPhotoPath = photoPath)
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun saveCar() {
        val currentState = _state.value
        
        // Validation
        val brandError = if (currentState.brand.isBlank()) "Обязательное поле" else null
        val modelError = if (currentState.model.isBlank()) "Обязательное поле" else null
        val fuelTypeError = if (currentState.fuelType.isBlank()) "Обязательное поле" else null
        val mileageError = if (currentState.currentMileage.isBlank()) "Обязательное поле" else null
        
        if (brandError != null || modelError != null || fuelTypeError != null || mileageError != null) {
            _state.value = currentState.copy(
                brandError = brandError,
                modelError = modelError,
                fuelTypeError = fuelTypeError,
                mileageError = mileageError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                
                if (currentState.carId != null) {
                    // Update existing car
                    val car = Car(
                        id = currentState.carId,
                        brand = currentState.brand,
                        model = currentState.model,
                        year = currentState.year.toIntOrNull(),
                        color = currentState.color.ifBlank { null },
                        licensePlate = currentState.licensePlate.ifBlank { null },
                        vin = currentState.vin.ifBlank { null },
                        engineModel = currentState.engineModel.ifBlank { null },
                        engineVolume = currentState.engineVolume.toDoubleOrNull(),
                        transmissionType = currentState.transmissionType.ifBlank { null },
                        driveType = currentState.driveType.ifBlank { null },
                        bodyType = currentState.bodyType.ifBlank { null },
                        fuelType = currentState.fuelType,
                        hasGasEquipment = currentState.hasGasEquipment,
                        gasType = currentState.gasType,
                        currentMileage = currentState.currentMileage.toInt(),
                        purchaseMileage = currentState.purchaseMileage.toIntOrNull(),
                        purchaseDate = null,
                        mainPhotoPath = currentState.mainPhotoPath,
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime, // Will be ignored by update
                        updatedAt = currentTime
                    )
                    carRepository.updateCar(car)
                } else {
                    // Insert new car
                    val car = Car(
                        brand = currentState.brand,
                        model = currentState.model,
                        year = currentState.year.toIntOrNull(),
                        color = currentState.color.ifBlank { null },
                        licensePlate = currentState.licensePlate.ifBlank { null },
                        vin = currentState.vin.ifBlank { null },
                        engineModel = currentState.engineModel.ifBlank { null },
                        engineVolume = currentState.engineVolume.toDoubleOrNull(),
                        transmissionType = currentState.transmissionType.ifBlank { null },
                        driveType = currentState.driveType.ifBlank { null },
                        bodyType = currentState.bodyType.ifBlank { null },
                        fuelType = currentState.fuelType,
                        hasGasEquipment = currentState.hasGasEquipment,
                        gasType = currentState.gasType,
                        currentMileage = currentState.currentMileage.toInt(),
                        purchaseMileage = currentState.purchaseMileage.toIntOrNull(),
                        purchaseDate = null,
                        mainPhotoPath = currentState.mainPhotoPath,
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    carRepository.insertCar(car)
                }
                
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
