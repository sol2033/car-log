package com.carlog.presentation.screens.car

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.repository.CarRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.CarFormRules
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.util.Attachments
import com.carlog.util.FileHelper
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddCarState(
    val carId: Long? = null,
    // Исходная запись при редактировании: сохраняем через copy (правило 1), иначе всё,
    // чего нет в форме (например, запрос нового отсчёта расхода), ушло бы в дефолт
    val original: Car? = null,
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
    // Бак, л (у электро — батарея, кВт·ч) и газовый баллон: для пробега на баке
    val tankVolume: String = "",
    val gasTankVolume: String = "",
    // Объём между MIN и MAX щупа (для расхода масла) и «не знаю» — подставится 1 л
    val oilDipstickVolume: String = "",
    val oilDipstickVolumeUnknown: Boolean = false,
    val oilConsumptionEnabled: Boolean = true,
    val currentMileage: String = "",
    val purchaseMileage: String = "",
    val purchaseDate: String = "",
    val purchaseDateMillis: Long? = null,
    val photosPaths: List<String> = emptyList(),
    val mainPhotoPath: String? = null,
    val notes: String = "",
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
    val tankVolumeError: String? = null,
    val gasTankVolumeError: String? = null,
    val oilDipstickVolumeError: String? = null,
    val engineVolumeError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddCarViewModel @Inject constructor(
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddCarState())
    val state: StateFlow<AddCarState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId")
        if (carId != null && carId != -1L) {
            loadCar(carId)
        }
        viewModelScope.launch {
            appPreferences.oilConsumptionEnabled.collect { enabled ->
                _state.value = _state.value.copy(oilConsumptionEnabled = enabled)
            }
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
                            original = car,
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
                            tankVolume = car.tankVolume?.let(::formatVolume) ?: "",
                            gasTankVolume = car.gasTankVolume?.let(::formatVolume) ?: "",
                            oilDipstickVolume = car.oilDipstickVolume
                                ?.takeIf { !car.oilDipstickVolumeIsGuess }?.let(::formatVolume) ?: "",
                            oilDipstickVolumeUnknown = car.oilDipstickVolumeIsGuess,
                            oilConsumptionEnabled = _state.value.oilConsumptionEnabled,
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
        _state.value = _state.value.copy(engineVolume = engineVolume, engineVolumeError = null)
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
    
    fun updateTankVolume(volume: String) {
        _state.value = _state.value.copy(tankVolume = volume, tankVolumeError = null)
    }

    fun updateGasTankVolume(volume: String) {
        _state.value = _state.value.copy(gasTankVolume = volume, gasTankVolumeError = null)
    }

    fun updateOilDipstickVolume(volume: String) {
        _state.value = _state.value.copy(oilDipstickVolume = volume, oilDipstickVolumeError = null)
    }

    fun updateOilDipstickVolumeUnknown(unknown: Boolean) {
        _state.value = _state.value.copy(oilDipstickVolumeUnknown = unknown, oilDipstickVolumeError = null)
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
        // Объём обязателен только для новой машины: у заведённых до 1.4.0 его нет,
        // и статистика сама попросит его указать
        val isNewCar = currentState.carId == null
        val tankVolumeError = CarFormRules.volumeError(currentState.tankVolume, required = isNewCar)
        val gasTankVolumeError = if (currentState.hasGasEquipment) {
            CarFormRules.volumeError(currentState.gasTankVolume, required = isNewCar)
        } else null
        val oilDipstickVolumeError = if (currentState.oilDipstickVolumeUnknown) null
            else CarFormRules.volumeError(currentState.oilDipstickVolume, required = false)
        // Объём двигателя разбирался через toDoubleOrNull: «1,6» с русской раскладки молча
        // сохранялся как «не указан», а при правке машины стирал уже записанный объём
        val engineVolumeError = CarFormRules.volumeError(currentState.engineVolume, required = false)

        if (brandError != null || modelError != null || fuelTypeError != null || mileageError != null || purchaseDateError != null || purchaseMileageError != null ||
            tankVolumeError != null || gasTankVolumeError != null || oilDipstickVolumeError != null ||
            engineVolumeError != null) {
            _state.value = currentState.copy(
                brandError = brandError,
                modelError = modelError,
                fuelTypeError = fuelTypeError,
                mileageError = mileageError,
                purchaseDateError = purchaseDateError,
                purchaseMileageError = purchaseMileageError,
                tankVolumeError = tankVolumeError,
                gasTankVolumeError = gasTankVolumeError,
                oilDipstickVolumeError = oilDipstickVolumeError,
                engineVolumeError = engineVolumeError
            )
            return
        }
        
        // Запись ещё не догрузилась — сохранять нельзя (правило 1)
        if (currentState.carId != null && currentState.original == null) return

        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val currentMileage = currentState.currentMileage.toMileageOrNull() ?: 0
                val purchaseMileage = currentState.purchaseMileage.toMileageOrNull()
                val tankVolume = currentState.tankVolume.toDecimalOrNull()
                // Без ГБО баллона нет — не храним забытое значение
                val gasTankVolume = if (currentState.hasGasEquipment) {
                    currentState.gasTankVolume.toDecimalOrNull()
                } else null
                // «Не знаю» — 1 л с пометкой: расход по щупу тогда считается с погрешностью
                val oilDipstickVolume = if (currentState.oilDipstickVolumeUnknown) {
                    OilConsumptionCalculator.DEFAULT_DIPSTICK_VOLUME
                } else currentState.oilDipstickVolume.toDecimalOrNull()
                val oilDipstickVolumeIsGuess = currentState.oilDipstickVolumeUnknown
                
                val original = currentState.original
                if (original != null && currentMileage != original.currentMileage) {
                    // Пробег ниже записей журнала форма молча сохраняла, а при следующем
                    // запуске синхронизация возвращала прежний максимум. Уменьшение с
                    // «прижатием» записей есть на карточке машины — там оно и подтверждается
                    val maxRecorded = carRepository.getMaxRecordedMileage(original.id)
                    if (currentMileage < maxRecorded) {
                        _state.value = currentState.copy(
                            isSaving = false,
                            mileageError = "В журнале есть записи на $maxRecorded км. " +
                                "Уменьшить пробег можно кнопкой обновления пробега на карточке автомобиля"
                        )
                        return@launch
                    }
                }
                if (original != null) {
                    // copy, а не новая сущность: поля вне формы (fuelResetPending,
                    // createdAt — по нему считается возраст авто) должны уцелеть
                    val car = original.copy(
                        brand = currentState.brand,
                        model = currentState.model,
                        year = currentState.year.toIntOrNull(),
                        color = currentState.color.ifBlank { null },
                        licensePlate = currentState.licensePlate.ifBlank { null },
                        vin = currentState.vin.ifBlank { null },
                        engineModel = currentState.engineModel.ifBlank { null },
                        engineVolume = currentState.engineVolume.toDecimalOrNull(),
                        transmissionType = currentState.transmissionType.ifBlank { null },
                        driveType = currentState.driveType.ifBlank { null },
                        bodyType = currentState.bodyType.ifBlank { null },
                        fuelType = currentState.fuelType,
                        hasGasEquipment = currentState.hasGasEquipment,
                        gasType = currentState.gasType,
                        tankVolume = tankVolume,
                        gasTankVolume = gasTankVolume,
                        oilDipstickVolume = oilDipstickVolume,
                        oilDipstickVolumeIsGuess = oilDipstickVolumeIsGuess,
                        currentMileage = currentMileage,
                        purchaseMileage = purchaseMileage,
                        purchaseDate = currentState.purchaseDateMillis,
                        mainPhotoPath = currentState.mainPhotoPath,
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
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
                        engineVolume = currentState.engineVolume.toDecimalOrNull(),
                        transmissionType = currentState.transmissionType.ifBlank { null },
                        driveType = currentState.driveType.ifBlank { null },
                        bodyType = currentState.bodyType.ifBlank { null },
                        fuelType = currentState.fuelType,
                        hasGasEquipment = currentState.hasGasEquipment,
                        gasType = currentState.gasType,
                        tankVolume = tankVolume,
                        gasTankVolume = gasTankVolume,
                        oilDipstickVolume = oilDipstickVolume,
                        oilDipstickVolumeIsGuess = oilDipstickVolumeIsGuess,
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

/** 50.0 → «50», 42.5 → «42.5»: без хвостового «.0» в поле формы */
private fun formatVolume(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
