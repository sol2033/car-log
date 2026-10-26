package com.carlog.presentation.screens.refuelings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.FuelConsumptionCalculator
import com.carlog.domain.model.FuelGroupConsumption
import com.carlog.domain.model.Refueling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RefuelingsViewModel @Inject constructor(
    private val refuelingRepository: RefuelingRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier
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
    
    // Средний расход и пробег на баке по группам топлива — тем же расчётом, что в статистике.
    // Раньше здесь усреднялся расход «полных баков», которые почти никто не отмечал,
    // и строка со средним просто не появлялась
    val consumption: StateFlow<List<FuelGroupConsumption>> = combine(_car.filterNotNull(), refuelings) { car, list ->
        FuelConsumptionCalculator.summarize(list, car)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val consumptionSince = refuelings.map { FuelConsumptionCalculator.consumptionSince(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    fun setCarId(carId: Long) {
        _carId.value = carId
    }
    
    /** Новый отсчёт расхода: точкой отсчёта станет следующая добавленная заправка (§4.5) */
    fun requestConsumptionReset() = setResetPending(true)

    fun cancelConsumptionReset() = setResetPending(false)

    private fun setResetPending(pending: Boolean) {
        val carId = _carId.value ?: return
        viewModelScope.launch {
            carRepository.setFuelResetPending(carId, pending)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    /** Снимает точку отсчёта: расход снова считается по всей истории */
    fun clearConsumptionReset() {
        val carId = _carId.value ?: return
        viewModelScope.launch {
            refuelingRepository.clearConsumptionResetPoints(carId)
            carRepository.setFuelResetPending(carId, false)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    fun deleteRefueling(refueling: Refueling) {
        viewModelScope.launch {
            // Точку отсчёта расхода не теряем вместе с записью — см. resetPointOnDelete
            val all = refuelingRepository.getRefuelingsByCarId(refueling.carId).first()
            val resetPoint = FuelConsumptionCalculator.resetPointOnDelete(all, refueling)
            refuelingRepository.deleteRefueling(refueling)
            when (resetPoint) {
                is FuelConsumptionCalculator.ResetPointOnDelete.MoveTo -> refuelingRepository.updateRefueling(
                    resetPoint.successor.copy(
                        isConsumptionResetPoint = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                FuelConsumptionCalculator.ResetPointOnDelete.Pending ->
                    carRepository.setFuelResetPending(refueling.carId, true)
                FuelConsumptionCalculator.ResetPointOnDelete.Unaffected -> Unit
            }
            // Обновляем пробег автомобиля до максимального
            carRepository.updateCarMileageAfterDelete(refueling.carId, refueling.mileage)
            // Уведомляем о изменении данных
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
