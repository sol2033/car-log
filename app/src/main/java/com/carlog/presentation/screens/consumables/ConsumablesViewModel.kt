package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.OilTopUpRepository
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.domain.model.OilInterval
import com.carlog.domain.model.OilTopUp
import com.carlog.util.ConsumableStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsumableWithStatus(
    val category: String,
    val consumable: Consumable?,
    val statusInfo: ConsumableStatus.StatusInfo?,
    // Текущее моторное масло при включённом учёте расхода: сколько долито и расход
    val oil: OilInterval? = null
)

sealed class ConsumablesUiState {
    object Loading : ConsumablesUiState()
    data class Success(
        val activeConsumables: List<ConsumableWithStatus>,
        val currentMileage: Int
    ) : ConsumablesUiState()
    data class Error(val message: String) : ConsumablesUiState()
}

@HiltViewModel
class ConsumablesViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    private val carRepository: CarRepository,
    private val preferences: ConsumablePreferences,
    private val oilTopUpRepository: OilTopUpRepository,
    private val appPreferences: AppPreferences,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow<ConsumablesUiState>(ConsumablesUiState.Loading)
    val uiState: StateFlow<ConsumablesUiState> = _uiState.asStateFlow()
    
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()
    
    private val _showFirstLaunchDialog = MutableStateFlow(false)
    val showFirstLaunchDialog: StateFlow<Boolean> = _showFirstLaunchDialog.asStateFlow()
    
    init {
        loadConsumables()
        loadAvailableCategories()
        checkFirstLaunch()
    }
    
    private fun checkFirstLaunch() {
        viewModelScope.launch {
            preferences.isFirstLaunch(carId).collect { isFirst ->
                _showFirstLaunchDialog.value = isFirst
            }
        }
    }
    
    private fun loadAvailableCategories() {
        viewModelScope.launch {
            preferences.selectedCategories.collect { selected ->
                _availableCategories.value = ConsumableCategories.STANDARD_CATEGORIES + selected
            }
        }
    }
    
    private fun loadConsumables() {
        viewModelScope.launch {
            try {
                combine(
                    consumableRepository.getActiveConsumablesByCarId(carId),
                    carRepository.getCarById(carId),
                    preferences.selectedCategories,
                    consumableRepository.getConsumablesByCategory(carId, OilConsumptionCalculator.ENGINE_OIL),
                    combine(oilTopUpRepository.getTopUpsByCarId(carId), appPreferences.oilConsumptionEnabled, ::Pair)
                ) { consumables, car, selectedCategories, oils, (topUps, oilEnabled) ->
                    val currentMileage = car?.currentMileage ?: 0
                    // distinct обязателен: selectedCategories может содержать стандартную
                    // категорию, и тогда на экране рисовались две одинаковые карточки
                    val allCategories =
                        (ConsumableCategories.STANDARD_CATEGORIES + selectedCategories).distinct()
                    
                    // Группируем расходники по категориям
                    val consumablesByCategory = consumables.groupBy { it.category }
                    val oilIntervals = if (oilEnabled) {
                        OilConsumptionCalculator.intervals(oils, topUps, car, currentMileage)
                    } else emptyList()
                    
                    // Создаем карточки для всех категорий
                    val consumablesWithStatus = allCategories.map { category ->
                        val consumable = consumablesByCategory[category]?.firstOrNull()
                        ConsumableWithStatus(
                            category = category,
                            consumable = consumable,
                            statusInfo = consumable?.let { 
                                ConsumableStatus.calculateStatus(it, currentMileage)
                            },
                            oil = consumable?.let { c -> oilIntervals.firstOrNull { it.oil.id == c.id } }
                        )
                    }
                    
                    ConsumablesUiState.Success(
                        activeConsumables = consumablesWithStatus,
                        currentMileage = currentMileage
                    )
                }.catch { e ->
                    _uiState.value = ConsumablesUiState.Error(e.message ?: "Ошибка загрузки")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ConsumablesUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    /**
     * Долив прямо с плитки масла, без захода на экран масла. Шаги те же, что у
     * `ConsumableDetailViewModel.saveTopUp`: запись, пробег машины (долив мог быть на
     * пробеге выше текущего) и уведомление для авто-бэкапа (правило 5).
     */
    fun addTopUp(date: Long, mileage: Int, volume: Double, cost: Double?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            oilTopUpRepository.insertTopUp(
                OilTopUp(
                    carId = carId,
                    date = date,
                    mileage = mileage,
                    volume = volume,
                    cost = cost,
                    createdAt = now,
                    updatedAt = now
                )
            )
            carRepository.updateCarMileageIfNeeded(carId, mileage)
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
