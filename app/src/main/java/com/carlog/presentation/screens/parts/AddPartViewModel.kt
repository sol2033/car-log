package com.carlog.presentation.screens.parts

import com.carlog.util.moneyInputError
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.NameSuggestionRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Part
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.util.FileHelper
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /**
     * Все фото, прошедшие через экран: исходные плюс выбранные в этот заход. Файл копируется
     * в хранилище сразу при выборе, поэтому без такого учёта снимок, выбранный и затем
     * отменённый, оставался бы в хранилище навсегда — и попадал бы в каждый бэкап.
     */
    val touchedPhotos: Set<String> = emptySet(),
    
    val nameError: String? = null,
    val installMileageError: String? = null,
    val priceError: String? = null,
    val servicePriceError: String? = null,
    
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
    private val nameSuggestionRepository: NameSuggestionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddPartState())
    val state: StateFlow<AddPartState> = _state.asStateFlow()

    /** Подсказки к полю «Название сервиса» — сервисы из прошлых записей, см. [NameSuggestionRepository] */
    val serviceNameSuggestions: StateFlow<List<String>> = nameSuggestionRepository
        .serviceNameSuggestions(_state.map { it.serviceName }.distinctUntilChanged())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
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
                        touchedPhotos = (part.photosPaths ?: emptyList()).toSet(),
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
        // Стоимость установки видна только при «Сервис». Переключили на «Самостоятельно» —
        // сумма уходит вместе с полем: иначе она сохранялась скрытой и попадала
        // в «Услуги сервисов» статистики, хотя за установку никто не платил
        _state.value = _state.value.copy(
            installationType = type,
            servicePrice = if (type == "Сервис") _state.value.servicePrice else "",
            servicePriceError = null
        )
    }
    
    fun updatePrice(price: String) {
        _state.value = _state.value.copy(
            price = price,
            priceError = if (price.isNotBlank()) null else _state.value.priceError
        )
    }
    
    fun updateServicePrice(price: String) {
        _state.value = _state.value.copy(servicePrice = price, servicePriceError = null)
    }
    
    fun updateServiceName(name: String) {
        _state.value = _state.value.copy(serviceName = name)
    }
    
    fun updateServiceAddress(address: String) {
        _state.value = _state.value.copy(serviceAddress = address)
    }

    /**
     * Сервис выбран из подсказок: подставляем и его адрес из прошлой записи — но только
     * в пустое поле, введённый адрес не перетираем. Пока адрес ищется, пользователь мог
     * начать его набирать, поэтому пустота проверяется ещё раз перед записью.
     */
    fun pickServiceNameSuggestion(name: String) {
        updateServiceName(name)
        if (_state.value.serviceAddress.isNotBlank()) return
        viewModelScope.launch {
            val address = nameSuggestionRepository.lastServiceAddress(name) ?: return@launch
            if (_state.value.serviceAddress.isBlank() && _state.value.serviceName == name) {
                _state.value = _state.value.copy(serviceAddress = address)
            }
        }
    }
    
    fun addPhoto(photoPath: String) {
        val currentPaths = _state.value.photosPaths.toMutableList()
        currentPaths.add(photoPath)
        _state.value = _state.value.copy(
            photosPaths = currentPaths,
            touchedPhotos = _state.value.touchedPhotos + photoPath
        )
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
        val installMileageError = BreakdownFormRules.mileageError(currentState.installMileage)
        // Цена уходила в toDouble() — на русской раскладке «1250,50» роняло сохранение
        val priceError = when {
            currentState.price.isBlank() -> "Обязательное поле"
            currentState.price.toDecimalOrNull() == null -> "Введите стоимость числом"
            else -> null
        }
        
        // Стоимость установки необязательна, но введённый мусор раньше молча становился «не указано»
        val servicePriceError = if (currentState.installationType == "Сервис") {
            moneyInputError(currentState.servicePrice)
        } else null

        if (nameError != null || installMileageError != null || priceError != null || servicePriceError != null) {
            _state.value = currentState.copy(
                nameError = nameError,
                installMileageError = installMileageError,
                priceError = priceError,
                servicePriceError = servicePriceError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val installMileage = currentState.installMileage.toMileageOrNull() ?: 0
                val price = currentState.price.toDecimalOrNull() ?: 0.0
                val servicePriceValue = currentState.servicePrice.toDecimalOrNull()
                
                val original = currentState.originalPart
                if (currentState.partId != null && original != null) {
                    // Обновляем существующую запись через copy: поля, которых нет в форме
                    // (статус поломки, пройденный ресурс, тип обслуживания, createdAt),
                    // должны сохраниться — раньше они собирались заново и обнулялись,
                    // из-за чего редактирование «оживляло» сломанную запчасть
                    val part = original.copy(
                        carId = currentState.carId,
                        name = currentState.name,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        partNumber = currentState.partNumber.ifBlank { null },
                        installDate = currentState.installDate,
                        installMileage = installMileage,
                        installationType = currentState.installationType,
                        price = price,
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
                        installMileage = installMileage,
                        installationType = currentState.installationType,
                        price = price,
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
                val editedFrom = currentState.originalPart?.installMileage
                if (editedFrom != null) {
                    carRepository.updateCarMileageAfterEdit(currentState.carId, editedFrom, installMileage)
                } else {
                    carRepository.updateCarMileageIfNeeded(currentState.carId, installMileage)
                }

                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                
                // Файлы, выбранные на экране, но не попавшие в итоговую запись
                // (добавили и тут же убрали) — иначе они остались бы навсегда
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(
                        currentState.touchedPhotos,
                        currentState.photosPaths.toSet()
                    )
                )

                // Снимок currentState сделан до сохранения: набранное за это время
                // он бы откатил, а поля ввода во время сохранения не блокируются
                _state.value = _state.value.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                // Сообщения у исключения может не быть — экран замирал молча
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message ?: "Не удалось сохранить запчасть"
                )
            }
        }
    }

    override fun onCleared() {
        // Ушли, не сохранив: снимки, выбранные в этот заход, никому не принадлежат.
        // Фото, на которые ссылается запись в БД, не трогаем — при отмене «эталоном»
        // считается исходный набор, а не то, что осталось в форме
        val current = _state.value
        if (!current.isSaved) {
            FileHelper.deleteFiles(
                orphanPhotosToDelete(
                    current.touchedPhotos,
                    (current.originalPart?.photosPaths ?: emptyList()).toSet()
                )
            )
        }
        super.onCleared()
    }
}
