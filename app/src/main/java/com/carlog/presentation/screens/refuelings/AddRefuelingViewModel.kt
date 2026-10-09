package com.carlog.presentation.screens.refuelings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.NameSuggestionRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Refueling
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMoneyInput
import com.carlog.util.toMileageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddRefuelingViewModel @Inject constructor(
    private val refuelingRepository: RefuelingRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    private val nameSuggestionRepository: NameSuggestionRepository
) : ViewModel() {
    
    private val _carId = MutableStateFlow<Long?>(null)
    
    private val _car = _carId.filterNotNull().flatMapLatest { id ->
        carRepository.getCarById(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    val car: StateFlow<Car?> = _car
    
    private val _refuelingId = MutableStateFlow<Long?>(null)
    
    private val _refueling = _refuelingId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getRefuelingById(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    val refueling: StateFlow<Refueling?> = _refueling
    
    private val _date = mutableStateOf(System.currentTimeMillis())
    val date: State<Long> = _date
    
    private val _mileage = mutableStateOf("")
    val mileage: State<String> = _mileage
    
    private val _mileageError = mutableStateOf<String?>(null)
    val mileageError: State<String?> = _mileageError
    
    private val _liters = mutableStateOf("")
    val liters: State<String> = _liters
    
    private val _litersError = mutableStateOf<String?>(null)
    val litersError: State<String?> = _litersError
    
    private val _fuelType = mutableStateOf("")
    val fuelType: State<String> = _fuelType
    
    private val _pricePerLiter = mutableStateOf("")
    val pricePerLiter: State<String> = _pricePerLiter
    
    private val _pricePerLiterError = mutableStateOf<String?>(null)
    val pricePerLiterError: State<String?> = _pricePerLiterError
    
    private val _totalCost = mutableStateOf("")
    val totalCost: State<String> = _totalCost
    
    private val _totalCostError = mutableStateOf<String?>(null)
    val totalCostError: State<String?> = _totalCostError
    
    // Км на этом топливе с прошлой заправки им же — только для машин с ГБО
    private val _fuelDistance = mutableStateOf("")
    val fuelDistance: State<String> = _fuelDistance

    private val _fuelDistanceError = mutableStateOf<String?>(null)
    val fuelDistanceError: State<String?> = _fuelDistanceError
    
    private val _stationName = mutableStateOf("")
    val stationName: State<String> = _stationName

    /**
     * Ввод названия ещё и потоком — для подсказок. Compose-`State` из ViewModel наблюдать
     * как Flow ненадёжно (snapshotFlow без кадров не получает изменений), поэтому значение
     * дублируется здесь в каждом месте, где меняется `_stationName`.
     */
    private val stationNameInput = MutableStateFlow("")

    /** Подсказки к полю названия заправки — из прошлых заправок, см. [NameSuggestionRepository] */
    val stationNameSuggestions: StateFlow<List<String>> = nameSuggestionRepository
        .stationNameSuggestions(stationNameInput)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _notes = mutableStateOf("")
    val notes: State<String> = _notes
    
    private val _availableFuelTypes = MutableStateFlow<List<String>>(emptyList())
    val availableFuelTypes: StateFlow<List<String>> = _availableFuelTypes.asStateFlow()

    /** Ошибка сохранения: раньше исключение уходило в лог, а экран молча не закрывался */
    private val _saveError = mutableStateOf<String?>(null)
    val saveError: State<String?> = _saveError
    
    fun setCarId(carId: Long, refuelingId: Long? = null) {
        _carId.value = carId
        _refuelingId.value = refuelingId

        viewModelScope.launch {
            _car.collect { car ->
                if (car != null) {
                    updateAvailableFuelTypes(car)
                }
            }
        }
        
        // Если редактируем существующую заправку
        if (refuelingId != null) {
            viewModelScope.launch {
                _refueling.collect { refueling ->
                    if (refueling != null) {
                        _date.value = refueling.date
                        _mileage.value = refueling.mileage.toString()
                        _liters.value = refueling.liters.toString()
                        _fuelType.value = refueling.fuelType
                        _pricePerLiter.value = refueling.pricePerLiter?.toString() ?: ""
                        _totalCost.value = refueling.totalCost?.toString() ?: ""
                        _fuelDistance.value = refueling.fuelDistanceKm?.toString() ?: ""
                        _stationName.value = refueling.stationName ?: ""
                        stationNameInput.value = _stationName.value
                        _notes.value = refueling.notes ?: ""
                    }
                }
            }
        }
    }
    
    private fun updateAvailableFuelTypes(car: Car) {
        val types = mutableListOf<String>()
        
        when (car.fuelType) {
            "Бензин" -> {
                types.addAll(listOf("АИ-92", "АИ-92+", "АИ-95", "АИ-95+", "АИ-100"))
            }
            "Дизель" -> {
                types.add("Дизель")
            }
            "Электро" -> {
                types.add("Электричество")
            }
        }
        
        // Добавляем газ, если установлено оборудование
        if (car.hasGasEquipment && car.gasType != null) {
            types.add(car.gasType)
        }
        
        _availableFuelTypes.value = types
        
        // Устанавливаем первый тип по умолчанию, если ничего не выбрано
        if (_fuelType.value.isEmpty() && types.isNotEmpty()) {
            _fuelType.value = types.first()
        }
    }
    
    fun onDateChanged(date: Long) {
        _date.value = date
    }
    
    fun onMileageChanged(mileage: String) {
        _mileage.value = mileage
        _mileageError.value = null
    }
    
    fun onLitersChanged(liters: String) {
        _liters.value = liters
        _litersError.value = null
        
        // Автоматически рассчитываем стоимость, если есть цена за литр
        val price = _pricePerLiter.value.toDecimalOrNull()
        val ltr = liters.toDecimalOrNull()
        if (price != null && ltr != null) {
            _totalCost.value = (price * ltr).toMoneyInput()
        }
    }
    
    fun onFuelTypeChanged(fuelType: String) {
        _fuelType.value = fuelType
    }
    
    fun onPricePerLiterChanged(price: String) {
        _pricePerLiter.value = price
        _pricePerLiterError.value = null
        _totalCostError.value = null
        
        // Автоматически рассчитываем стоимость
        val pricePerLtr = price.toDecimalOrNull()
        val ltr = _liters.value.toDecimalOrNull()
        if (pricePerLtr != null && ltr != null) {
            _totalCost.value = (pricePerLtr * ltr).toMoneyInput()
        }
    }
    
    fun onTotalCostChanged(cost: String) {
        _totalCost.value = cost
        _totalCostError.value = null
        _pricePerLiterError.value = null
    }
    
    fun onFuelDistanceChanged(distance: String) {
        _fuelDistance.value = distance
        _fuelDistanceError.value = null
    }
    
    fun onStationNameChanged(name: String) {
        _stationName.value = name
        stationNameInput.value = name
    }
    
    fun onNotesChanged(notes: String) {
        _notes.value = notes
    }
    
    fun dismissSaveError() {
        _saveError.value = null
    }

    /**
     * Идёт сохранение. Ставится синхронно, до запуска корутины: два быстрых тапа (галочка
     * в шапке и кнопка внизу) иначе оба проходили проверки и вставляли две записи.
     * После успеха не сбрасывается — экран вот-вот закроется.
     */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun saveRefueling(onSuccess: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            var saved = false
            try {
                saveRefuelingInternal {
                    saved = true
                    onSuccess()
                }
            } finally {
                if (!saved) _isSaving.value = false
            }
        }
    }

    private suspend fun saveRefuelingInternal(onSuccess: () -> Unit) {
        run {
            try {
                val carId = _carId.value ?: return
                _saveError.value = null

                // Запись ещё не догрузилась — сохранять нельзя: вставка создала бы дубликат
                val existing = _refueling.value
                if (_refuelingId.value != null && existing == null) return

                // Валидация
                // Разбор общий с остальными формами: «80 000» и «41,5» — ровно то,
                // что даёт русская раскладка, и отказывать в них незачем
                val mileageValue = _mileage.value.toMileageOrNull()
                val litersValue = _liters.value.toDecimalOrNull()
                
                var hasError = false
                
                if (_mileage.value.isBlank()) {
                    _mileageError.value = "Обязательное поле"
                    hasError = true
                } else if (mileageValue == null) {
                    _mileageError.value = "Введите корректное число"
                    hasError = true
                }
                
                if (_liters.value.isBlank()) {
                    _litersError.value = "Обязательное поле"
                    hasError = true
                } else if (litersValue == null) {
                    _litersError.value = "Введите корректное число"
                    hasError = true
                }
                
                // Проверяем, что указана хотя бы одна стоимость
                val hasPricePerLiter = _pricePerLiter.value.isNotBlank() && _pricePerLiter.value.toDecimalOrNull() != null
                val hasTotalCost = _totalCost.value.isNotBlank() && _totalCost.value.toDecimalOrNull() != null
                
                if (!hasPricePerLiter && !hasTotalCost) {
                    _pricePerLiterError.value = "Укажите цену за литр"
                    _totalCostError.value = "или общую стоимость"
                    hasError = true
                }
                // Достаточно одной стоимости, но введённый во вторую мусор молча не теряем
                if (_pricePerLiter.value.isNotBlank() && !hasPricePerLiter) {
                    _pricePerLiterError.value = "Введите корректное число"
                    hasError = true
                }
                if (_totalCost.value.isNotBlank() && !hasTotalCost) {
                    _totalCostError.value = "Введите корректное число"
                    hasError = true
                }
                
                // Км на топливе необязательны: без них расход ГБО-машины считается
                // приблизительно, по общему пробегу. Но мусор в поле молча не теряем
                val hasGas = _car.value?.hasGasEquipment == true
                val fuelDistanceValue = if (hasGas) _fuelDistance.value.toMileageOrNull() else null
                if (hasGas && _fuelDistance.value.isNotBlank() &&
                    (fuelDistanceValue == null || fuelDistanceValue <= 0)
                ) {
                    _fuelDistanceError.value = "Введите целое число км"
                    hasError = true
                }

                if (hasError) return
                
                val currentTime = System.currentTimeMillis()

                if (existing == null) {
                    // Если пользователь запросил новый отсчёт расхода — точкой отсчёта
                    // становится эта заправка, и запрос считается исполненным
                    val isResetPoint = carRepository.consumeFuelResetPending(carId)
                    refuelingRepository.insertRefueling(
                        Refueling(
                            carId = carId,
                            date = _date.value,
                            mileage = mileageValue!!,
                            liters = litersValue!!,
                            fuelType = _fuelType.value,
                            pricePerLiter = _pricePerLiter.value.toDecimalOrNull(),
                            totalCost = _totalCost.value.toDecimalOrNull(),
                            stationName = _stationName.value.ifEmpty { null },
                            isConsumptionResetPoint = isResetPoint,
                            fuelDistanceKm = fuelDistanceValue,
                            notes = _notes.value.ifEmpty { null },
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )
                    )
                } else {
                    // copy сохраняет исходный createdAt: @Update перезаписывает строку целиком
                    refuelingRepository.updateRefueling(
                        existing.copy(
                            date = _date.value,
                            mileage = mileageValue!!,
                            liters = litersValue!!,
                            fuelType = _fuelType.value,
                            pricePerLiter = _pricePerLiter.value.toDecimalOrNull(),
                            totalCost = _totalCost.value.toDecimalOrNull(),
                            stationName = _stationName.value.ifEmpty { null },
                            // Поля нет в форме без ГБО — тогда сохранённое значение не трогаем
                            fuelDistanceKm = if (hasGas) fuelDistanceValue else existing.fuelDistanceKm,
                            notes = _notes.value.ifEmpty { null },
                            updatedAt = currentTime
                        )
                    )
                }

                // Обновляем пробег автомобиля до максимального. Берём разобранное значение:
                // toInt() по сырому вводу падал на «80 000», уже прошедшем проверку
                carRepository.updateCarMileageIfNeeded(carId, mileageValue!!)
                
                // Уведомляем о изменении данных
                dataChangeNotifier.notifyDataChanged()
                
                onSuccess()
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Не удалось сохранить заправку"
            }
        }
    }
}
