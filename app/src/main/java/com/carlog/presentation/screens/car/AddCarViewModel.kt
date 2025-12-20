package com.carlog.presentation.screens.car

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
    val fuelType: String = "Бензин АИ-95",
    val currentMileage: String = "",
    val purchaseMileage: String = "",
    val notes: String = "",
    
    val brandError: String? = null,
    val modelError: String? = null,
    val fuelTypeError: String? = null,
    val mileageError: String? = null,
    
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddCarViewModel @Inject constructor(
    private val carRepository: CarRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddCarState())
    val state: StateFlow<AddCarState> = _state.asStateFlow()
    
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
    
    fun updateFuelType(fuelType: String) {
        _state.value = _state.value.copy(
            fuelType = fuelType,
            fuelTypeError = if (fuelType.isNotBlank()) null else _state.value.fuelTypeError
        )
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
                    fuelType = currentState.fuelType,
                    currentMileage = currentState.currentMileage.toInt(),
                    purchaseMileage = currentState.purchaseMileage.toIntOrNull(),
                    purchaseDate = null,
                    mainPhotoPath = null,
                    photosPaths = null,
                    notes = currentState.notes.ifBlank { null },
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
                
                carRepository.insertCar(car)
                
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
