package com.carlog.presentation.screens.refuelings

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
class RefuelingsViewModel @Inject constructor(
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
    
    val refuelings = _carId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getRefuelingsByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val refuelingsCount = _carId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getRefuelingsCountByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    
    val totalCost = _carId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getTotalCostByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    val totalLiters = _carId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getTotalLitersByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    val averageConsumption = _carId.filterNotNull().flatMapLatest { id ->
        refuelingRepository.getAverageConsumptionByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    fun setCarId(carId: Long) {
        _carId.value = carId
    }
    
    fun deleteRefueling(refueling: Refueling) {
        viewModelScope.launch {
            refuelingRepository.deleteRefueling(refueling)
            // Обновляем пробег автомобиля до максимального
            carRepository.updateCarMileageAfterDelete(refueling.carId, refueling.mileage)
        }
    }
}
