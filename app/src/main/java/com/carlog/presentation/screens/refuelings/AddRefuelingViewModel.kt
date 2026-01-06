package com.carlog.presentation.screens.refuelings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Refueling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddRefuelingViewModel @Inject constructor(
    private val refuelingRepository: RefuelingRepository,
    private val carRepository: CarRepository
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
    
    private val _isFullTank = mutableStateOf(false)
    val isFullTank: State<Boolean> = _isFullTank
    
    private val _stationName = mutableStateOf("")
    val stationName: State<String> = _stationName
    
    private val _notes = mutableStateOf("")
    val notes: State<String> = _notes
    
    private val _availableFuelTypes = MutableStateFlow<List<String>>(emptyList())
    val availableFuelTypes: StateFlow<List<String>> = _availableFuelTypes.asStateFlow()
    
    private val _previousRefueling = MutableStateFlow<Refueling?>(null)
    val previousRefueling: StateFlow<Refueling?> = _previousRefueling.asStateFlow()
    
    fun setCarId(carId: Long, refuelingId: Long? = null) {
        _carId.value = carId
        _refuelingId.value = refuelingId
        
        viewModelScope.launch {
            _car.collect { car ->
                if (car != null) {
                    updateAvailableFuelTypes(car)
                    
                    // Загружаем предыдущую заправку для расчета расхода
                    refuelingRepository.getRefuelingsByCarId(carId).collect { refuelings ->
                        _previousRefueling.value = refuelings.firstOrNull()
                    }
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
                        _isFullTank.value = refueling.isFullTank
                        _stationName.value = refueling.stationName ?: ""
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
        if (_pricePerLiter.value.isNotEmpty()) {
            try {
                val price = _pricePerLiter.value.toDouble()
                val ltr = liters.toDouble()
                _totalCost.value = (price * ltr).toString()
            } catch (e: Exception) {
                // Ignore
            }
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
        if (_liters.value.isNotEmpty()) {
            try {
                val pricePerLtr = price.toDouble()
                val ltr = _liters.value.toDouble()
                _totalCost.value = (pricePerLtr * ltr).toString()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun onTotalCostChanged(cost: String) {
        _totalCost.value = cost
        _totalCostError.value = null
        _pricePerLiterError.value = null
    }
    
    fun onIsFullTankChanged(isFullTank: Boolean) {
        _isFullTank.value = isFullTank
    }
    
    fun onStationNameChanged(name: String) {
        _stationName.value = name
    }
    
    fun onNotesChanged(notes: String) {
        _notes.value = notes
    }
    
    fun saveRefueling(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val carId = _carId.value ?: return@launch
                
                // Валидация
                val mileageValue = _mileage.value.toIntOrNull()
                val litersValue = _liters.value.toDoubleOrNull()
                
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
                val hasPricePerLiter = _pricePerLiter.value.isNotBlank() && _pricePerLiter.value.toDoubleOrNull() != null
                val hasTotalCost = _totalCost.value.isNotBlank() && _totalCost.value.toDoubleOrNull() != null
                
                if (!hasPricePerLiter && !hasTotalCost) {
                    _pricePerLiterError.value = "Укажите цену за литр"
                    _totalCostError.value = "или общую стоимость"
                    hasError = true
                }
                
                if (hasError) return@launch
                
                // Вычисляем расход, если это полный бак и есть предыдущая заправка
                var fuelConsumption: Double? = null
                if (_isFullTank.value && _previousRefueling.value != null) {
                    val previousMileage = _previousRefueling.value?.mileage ?: 0
                    val distance = mileageValue!! - previousMileage
                    if (distance > 0) {
                        fuelConsumption = (litersValue!! / distance) * 100
                    }
                }
                
                val refueling = Refueling(
                    id = _refuelingId.value ?: 0,
                    carId = carId,
                    date = _date.value,
                    mileage = mileageValue!!,
                    liters = litersValue!!,
                    fuelType = _fuelType.value,
                    pricePerLiter = _pricePerLiter.value.toDoubleOrNull(),
                    totalCost = _totalCost.value.toDoubleOrNull(),
                    isFullTank = _isFullTank.value,
                    stationName = _stationName.value.ifEmpty { null },
                    fuelConsumption = fuelConsumption,
                    notes = _notes.value.ifEmpty { null },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                if (_refuelingId.value == null) {
                    refuelingRepository.insertRefueling(refueling)
                } else {
                    refuelingRepository.updateRefueling(refueling)
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(carId, _mileage.value.toInt())
                
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
