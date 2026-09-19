package com.carlog.presentation.screens.car

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Car
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.util.Attachments
import com.carlog.util.FileHelper
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.util.toMileageOrNull
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
    val purchaseDate: String = "",
    val purchaseDateMillis: Long? = null,
    val photosPaths: List<String> = emptyList(),
    val mainPhotoPath: String? = null,
    val notes: String = "",
    // Сохраняется при редактировании, чтобы не затирать исходное время создания записи
    val createdAt: Long = 0L,
    /**
     * Все фото, прошедшие через экран: исходные плюс выбранные в этот заход. Файл копируется
     * в хранилище сразу при выборе, поэтому без такого учёта снимок, выбранный и затем
     * отменённый, оставался бы в хранилище навсегда — и попадал бы в каждый бэкап.
     */
    val touchedPhotos: Set<String> = emptySet(),
    /** Фото, на которые ссылается сохранённая запись: при отмене они должны уцелеть */
    val originalPhotos: Set<String> = emptySet(),

    val brandError: String? = null,
    val modelError: String? = null,
    val fuelTypeError: String? = null,
    val mileageError: String? = null,
    val purchaseDateError: String? = null,
    val purchaseMileageError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddCarViewModel @Inject constructor(
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
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
                            purchaseDate = car.purchaseDate?.let {
                                val calendar = java.util.Calendar.getInstance()
                                calendar.timeInMillis = it
                                String.format("%02d.%02d.%04d",
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                                    calendar.get(java.util.Calendar.MONTH) + 1,
                                    calendar.get(java.util.Calendar.YEAR)
                                )
                            } ?: "",
                            purchaseDateMillis = car.purchaseDate,
                            photosPaths = car.photosPaths ?: emptyList(),
                            mainPhotoPath = car.mainPhotoPath,
                            touchedPhotos = (car.photosPaths ?: emptyList()).toSet(),
                            originalPhotos = (car.photosPaths ?: emptyList()).toSet(),
                            notes = car.notes ?: "",
                            createdAt = car.createdAt,
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
    
    fun updatePurchaseDate(dateMillis: Long?) {
        val dateString = dateMillis?.let {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = it
            String.format("%02d.%02d.%04d",
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.YEAR)
            )
        } ?: ""
        _state.value = _state.value.copy(
            purchaseDate = dateString,
            purchaseDateMillis = dateMillis,
            purchaseDateError = null
        )
    }
    
    fun addPhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths + photoPath
        _state.value = _state.value.copy(
            photosPaths = currentPaths,
            // Основным становится первая картинка: PDF нечем показать ни в списке машин,
            // ни в шапке карточки
            mainPhotoPath = Attachments.nextMainPhoto(currentPaths, _state.value.mainPhotoPath),
            touchedPhotos = _state.value.touchedPhotos + photoPath
        )
    }
    
    fun removePhoto(photoPath: String) {
        // Файл не удаляем здесь: при отмене редактирования запись в БД осталась бы
        // со ссылкой на уже удалённый снимок. Ненужные файлы чистятся одним местом —
        // при сохранении и в onCleared, по разнице «затронутые минус нужные»
        val currentPaths = _state.value.photosPaths - photoPath

        _state.value = _state.value.copy(
            photosPaths = currentPaths,
            // Удалили основное — берём следующую картинку, документ обложкой стать не может
            mainPhotoPath = Attachments.nextMainPhoto(currentPaths, _state.value.mainPhotoPath)
        )
    }
    
    /** Обложка автомобиля. PDF сюда не попадает: нажатие на документ его открывает, а не выбирает */
    fun setMainPhoto(photoPath: String) {
        if (Attachments.isPdf(photoPath)) return
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
        val mileageError = BreakdownFormRules.mileageError(currentState.currentMileage)
        val purchaseDateError = if (currentState.purchaseDateMillis == null) "Обязательное поле" else null
        // Пробег покупки уходил в toIntOrNull и при нечисловом значении молча становился
        // null — а без него статистика не может считать средний пробег в день
        val purchaseMileageError = BreakdownFormRules.mileageError(currentState.purchaseMileage)
        
        if (brandError != null || modelError != null || fuelTypeError != null || mileageError != null || purchaseDateError != null || purchaseMileageError != null) {
            _state.value = currentState.copy(
                brandError = brandError,
                modelError = modelError,
                fuelTypeError = fuelTypeError,
                mileageError = mileageError,
                purchaseDateError = purchaseDateError,
                purchaseMileageError = purchaseMileageError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val currentMileage = currentState.currentMileage.toMileageOrNull() ?: 0
                val purchaseMileage = currentState.purchaseMileage.toMileageOrNull()
                
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
                        currentMileage = currentMileage,
                        purchaseMileage = purchaseMileage,
                        purchaseDate = currentState.purchaseDateMillis,
                        mainPhotoPath = currentState.mainPhotoPath,
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        // @Update перезаписывает строку целиком, поэтому исходное время
                        // создания нужно передать явно: по нему считается возраст авто
                        // в статистике, если не указана дата покупки
                        createdAt = currentState.createdAt,
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
                        currentMileage = currentMileage,
                        purchaseMileage = purchaseMileage,
                        purchaseDate = currentState.purchaseDateMillis,
                        mainPhotoPath = currentState.mainPhotoPath,
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    carRepository.insertCar(car)
                }

                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()

                // Снимки, не попавшие в итоговую запись (добавили и убрали, либо убрали
                // существующий) — иначе они остались бы в хранилище навсегда
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(
                        currentState.touchedPhotos,
                        currentState.photosPaths.toSet()
                    )
                )

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

    override fun onCleared() {
        // Ушли, не сохранив: снимки, выбранные в этот заход, никому не принадлежат.
        // Эталон при отмене — исходный набор записи, а не то, что осталось в форме,
        // поэтому убранное, но сохранённое в БД фото уцелеет
        val current = _state.value
        if (!current.isSaved) {
            FileHelper.deleteFiles(
                orphanPhotosToDelete(current.touchedPhotos, current.originalPhotos)
            )
        }
        super.onCleared()
    }
}
