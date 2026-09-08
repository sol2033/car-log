package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.EventComposition
import com.carlog.domain.model.MaintenanceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreakdownsUiState(
    val breakdowns: List<Breakdown> = emptyList(),
    val selectedMaintenanceType: MaintenanceType? = null,
    /**
     * Типы каждого события: одно обслуживание может быть сразу ТО, ремонтом и тюнингом,
     * и выводятся они из его содержимого, а не из поля записи.
     */
    val typesByBreakdown: Map<Long, List<MaintenanceType>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Пока типы не досчитались, показываем то, что записано в самой записи */
    fun typesOf(breakdown: Breakdown): List<MaintenanceType> =
        typesByBreakdown[breakdown.id]
            ?: listOf(
                MaintenanceType.fromString(breakdown.maintenanceType) ?: MaintenanceType.REPAIR
            )

    /**
     * Фильтр по типу. Смешанное событие видно во всех своих типах: заезд «ТО + ремонт»
     * найдётся и в «ТО», и в «Ремонт» — это один и тот же заезд, а не две записи.
     */
    val filteredBreakdowns: List<Breakdown> get() = when (selectedMaintenanceType) {
        null -> breakdowns
        else -> breakdowns.filter { typesOf(it).contains(selectedMaintenanceType) }
    }
}

@HiltViewModel
class BreakdownsViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    private val partRepository: PartRepository,
    private val consumableRepository: ConsumableRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L

    private val _uiState = MutableStateFlow(BreakdownsUiState())
    val uiState: StateFlow<BreakdownsUiState> = _uiState.asStateFlow()

    init {
        loadBreakdowns()
    }

    private fun loadBreakdowns() {
        viewModelScope.launch {
            try {
                // Тип события выводится из его позиций, поэтому списку нужны ещё запчасти
                // и расходники машины. Готовый набор типов в записи не храним: денормализация
                // разъезжается с содержимым, а объёмы здесь — журнал одной машины
                combine(
                    breakdownRepository.getBreakdownsByCarId(carId),
                    partRepository.getPartsByCarId(carId),
                    consumableRepository.getConsumablesByCarId(carId)
                ) { breakdowns, parts, consumables ->
                    val partsById = parts.associateBy { it.id }
                    val types = breakdowns.associate { breakdown ->
                        breakdown.id to EventComposition.typesOf(
                            fallbackType = MaintenanceType.fromString(breakdown.maintenanceType),
                            hasConsumables = consumables.any {
                                it.linkedMaintenanceId == breakdown.id
                            },
                            partTypes = breakdown.installedPartIds.orEmpty()
                                .mapNotNull { partsById[it] }
                                .map { EventComposition.typeOfPart(it.maintenanceType) }
                        )
                    }
                    breakdowns to types
                }.collect { (breakdowns, types) ->
                    _uiState.value = _uiState.value.copy(
                        breakdowns = breakdowns,
                        typesByBreakdown = types,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun selectMaintenanceType(type: MaintenanceType?) {
        _uiState.value = _uiState.value.copy(selectedMaintenanceType = type)
    }

    fun deleteBreakdown(breakdown: Breakdown) {
        viewModelScope.launch {
            try {
                breakdownRepository.deleteBreakdown(breakdown)
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageAfterDelete(breakdown.carId, breakdown.breakdownMileage)
                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
