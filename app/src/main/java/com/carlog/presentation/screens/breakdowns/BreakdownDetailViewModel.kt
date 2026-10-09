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
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.EventComposition
import com.carlog.domain.model.EventCostSplit
import com.carlog.domain.model.MaintenanceType
import com.carlog.domain.model.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BreakdownDetailUiState {
    object Loading : BreakdownDetailUiState()
    data class Success(
        val breakdown: Breakdown,
        /** Расходники события — его ТО-часть */
        val consumables: List<Consumable> = emptyList(),
        /** Запчасти события: каждая несёт свой тип — ремонт или тюнинг */
        val parts: List<Part> = emptyList()
    ) : BreakdownDetailUiState() {
        /**
         * Разбивка стоимости по категориям — та же, по которой событие попадает
         * в статистику, поэтому карточка и цифры в статистике не расходятся.
         */
        val costSplit: EventCostSplit
            get() = EventComposition.split(
                partsCost = breakdown.partsCost,
                fallbackType = MaintenanceType.fromString(breakdown.maintenanceType),
                consumableCosts = consumables.map { it.cost },
                partPrices = parts.map {
                    EventComposition.typeOfPart(it.maintenanceType) to it.price
                }
            )
    }
    data class Error(val message: String) : BreakdownDetailUiState()
}

@HiltViewModel
class BreakdownDetailViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val consumableRepository: ConsumableRepository,
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow<BreakdownDetailUiState>(BreakdownDetailUiState.Loading)
    val uiState: StateFlow<BreakdownDetailUiState> = _uiState.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    init {
        val breakdownId = savedStateHandle.get<Long>("breakdownId") ?: 0L
        loadBreakdown(breakdownId)
    }
    
    private fun loadBreakdown(breakdownId: Long) {
        viewModelScope.launch {
            try {
                combine(
                    breakdownRepository.getBreakdownById(breakdownId),
                    consumableRepository.getConsumablesByCarId(carId),
                    partRepository.getPartsByCarId(carId)
                ) { breakdown, allConsumables, allParts ->
                    // Расходники события берём по обеим сторонам связи: обычно они совпадают,
                    // но у записей из прежних версий одна сторона бывает потеряна, и карточка
                    // тогда показывала пустоту — правку было видно как «не сохранилось»
                    val linkedIds = breakdown?.linkedConsumableIds.orEmpty().toSet()
                    val consumables = if (breakdown == null) {
                        emptyList()
                    } else {
                        // Сравнение с id самого события, а не с breakdown?.id: у «отдельного»
                        // расходника связь пустая, и с null он совпал бы весь
                        allConsumables.filter {
                            it.linkedMaintenanceId == breakdown.id || linkedIds.contains(it.id)
                        }
                    }
                    // Запчасти именно этого события: у машины их может быть много,
                    // а нас интересуют только привязанные
                    val eventParts = breakdown?.installedPartIds.orEmpty()
                        .mapNotNull { id -> allParts.firstOrNull { it.id == id } }
                    Triple(breakdown, consumables, eventParts)
                }
                    .collect { (breakdown, consumables, eventParts) ->
                        if (breakdown != null) {
                            // Запчасти обязаны дойти до состояния: по ним раскладывается
                            // стоимость события, и без них ремонтная и тюнинговая части
                            // уезжали в тип события целиком — карточка расходилась
                            // со статистикой, которая считает запчасти правильно
                            _uiState.value =
                                BreakdownDetailUiState.Success(breakdown, consumables, eventParts)
                        } else {
                            _uiState.value = BreakdownDetailUiState.Error("Поломка не найдена")
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = BreakdownDetailUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
    
    fun showDeleteConfirmation() {
        _showDeleteDialog.value = true
    }
    
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    /** Сколько расходников уйдёт вместе с ТО и кого из предшественников можно вернуть */
    suspend fun deletePlan(breakdown: Breakdown) = breakdownRepository.deletePlan(breakdown)

    fun deleteBreakdown(breakdown: Breakdown, restorePredecessors: Boolean = false, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                breakdownRepository.deleteBreakdown(breakdown, restorePredecessors)
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageAfterDelete(breakdown.carId, breakdown.breakdownMileage)
                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                dismissDeleteDialog()
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = BreakdownDetailUiState.Error(e.message ?: "Ошибка при удалении")
            }
        }
    }
}
