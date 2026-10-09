package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.OilTopUpRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.domain.model.OilInterval
import com.carlog.domain.model.OilLevelDropInput
import com.carlog.domain.model.OilTopUp
import com.carlog.util.ConsumableStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConsumableDetailUiState {
    object Loading : ConsumableDetailUiState()
    data class Success(
        val consumable: Consumable,
        val statusInfo: ConsumableStatus.StatusInfo,
        val currentMileage: Int,
        val car: Car,
        // Расход масла этого интервала — только у моторного масла и при включённом учёте
        val oil: OilInterval? = null,
        // Средний расход за всю историю (по завершённым интервалам), л/1000 км
        val oilOverallPer1000Km: Double? = null,
        val oilOverallIsApproximate: Boolean = false
    ) : ConsumableDetailUiState()
    data class Error(val message: String) : ConsumableDetailUiState()
}

@HiltViewModel
class ConsumableDetailViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    private val carRepository: CarRepository,
    private val oilTopUpRepository: OilTopUpRepository,
    private val appPreferences: AppPreferences,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val consumableId: Long = savedStateHandle.get<Long>("consumableId") ?: 0L
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow<ConsumableDetailUiState>(ConsumableDetailUiState.Loading)
    val uiState: StateFlow<ConsumableDetailUiState> = _uiState.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _showReplaceDialog = MutableStateFlow(false)
    val showReplaceDialog: StateFlow<Boolean> = _showReplaceDialog.asStateFlow()
    
    private val _showReplaceSameDialog = MutableStateFlow(false)
    val showReplaceSameDialog: StateFlow<Boolean> = _showReplaceSameDialog.asStateFlow()
    
    init {
        loadConsumable()
    }
    
    private fun loadConsumable() {
        viewModelScope.launch {
            try {
                combine(
                    consumableRepository.getConsumableById(consumableId),
                    carRepository.getCarById(carId),
                    consumableRepository.getConsumablesByCategory(carId, OilConsumptionCalculator.ENGINE_OIL),
                    oilTopUpRepository.getTopUpsByCarId(carId),
                    appPreferences.oilConsumptionEnabled
                ) { consumable, car, oils, topUps, oilEnabled ->
                    if (consumable != null && car != null) {
                        val oilSummary = if (oilEnabled && OilConsumptionCalculator.isEngineOil(consumable)) {
                            OilConsumptionCalculator.summarize(oils, topUps, car, car.currentMileage)
                        } else null
                        ConsumableDetailUiState.Success(
                            consumable = consumable,
                            statusInfo = ConsumableStatus.calculateStatus(consumable, car.currentMileage),
                            currentMileage = car.currentMileage,
                            car = car,
                            oil = oilSummary?.intervals?.firstOrNull { it.oil.id == consumable.id },
                            oilOverallPer1000Km = oilSummary?.overallPer1000Km,
                            oilOverallIsApproximate = oilSummary?.overallIsApproximate == true
                        )
                    } else {
                        ConsumableDetailUiState.Error("Расходник не найден")
                    }
                }.catch { e ->
                    _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка загрузки")
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
    
    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }
    
    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun showReplaceDialog() {
        _showReplaceDialog.value = true
    }
    
    fun dismissReplaceDialog() {
        _showReplaceDialog.value = false
    }
    
    fun showReplaceSameDialog() {
        _showReplaceSameDialog.value = true
    }
    
    fun dismissReplaceSameDialog() {
        _showReplaceSameDialog.value = false
    }
    
    /** Можно ли после удаления вернуть в активные предыдущую установку категории */
    suspend fun deletePlan() = (_uiState.value as? ConsumableDetailUiState.Success)
        ?.let { consumableRepository.deletePlan(it.consumable) }
        ?: com.carlog.data.repository.ConsumableDeletePlan(removedCount = 1, restorable = emptyList())

    fun deleteConsumable(restorePredecessor: Boolean = false, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    consumableRepository.deleteConsumable(state.consumable, restorePredecessor)
                    // Обновляем пробег автомобиля до максимального
                    val maxMileage = maxOf(
                        state.consumable.installationMileage,
                        state.consumable.replacementMileage ?: 0
                    )
                    carRepository.updateCarMileageAfterDelete(state.consumable.carId, maxMileage)
                    // Уведомляем о изменении данных
                    dataChangeNotifier.notifyDataChanged()
                    onDeleted()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }
    
    // Замена на тот же расходник с новыми параметрами
    fun replaceSameConsumable(
        newCost: Double?,
        isInstalledAtService: Boolean,
        serviceCost: Double?,
        replacementDate: Long = System.currentTimeMillis(),
        levelDrop: OilLevelDropInput? = null,
        onReplaced: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    val currentTime = System.currentTimeMillis()
                    val oldConsumable = state.consumable
                    
                    // Обновляем старый расходник: дата замены — выбранная пользователем,
                    // а не «сейчас» (при замене задним числом история врала)
                    val updatedOld = oldConsumable.copy(
                        isActive = false,
                        replacementMileage = state.currentMileage,
                        replacementDate = replacementDate,
                        oilLevelDropMl = if (levelDrop != null) levelDrop.ml else oldConsumable.oilLevelDropMl,
                        oilLevelDropFraction = if (levelDrop != null) levelDrop.fraction else oldConsumable.oilLevelDropFraction,
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(updatedOld)
                    
                    // Создаем новый с новыми параметрами
                    val newConsumable = oldConsumable.copy(
                        id = 0,
                        // Новый расходник — отдельный, даже если старый ставили в ТО. Привязка
                        // наследовалась: новый попадал в карточку старого ТО, но не в его список,
                        // и его стоимость пропадала из статистики (не «отдельный» и не в ТО)
                        linkedMaintenanceId = null,
                        // Уровень относится к старому маслу — новому его не наследовать
                        oilLevelDropMl = null,
                        oilLevelDropFraction = null,
                        installationMileage = state.currentMileage,
                        installationDate = replacementDate,
                        cost = newCost,
                        isInstalledAtService = isInstalledAtService,
                        serviceCost = serviceCost,
                        replacementMileage = null,
                        replacementDate = null,
                        isActive = true,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    consumableRepository.insertConsumable(newConsumable)
                    
                    // Обновляем пробег автомобиля до максимального
                    carRepository.updateCarMileageIfNeeded(oldConsumable.carId, state.currentMileage)
                    
                    // Уведомляем о изменении данных
                    dataChangeNotifier.notifyDataChanged()
                    
                    onReplaced()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка замены")
            }
        }
    }
    
    // Замена на другой расходник (просто деактивация)
    fun replaceWithDifferentConsumable(levelDrop: OilLevelDropInput? = null, onReplaced: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is ConsumableDetailUiState.Success) {
                    val currentTime = System.currentTimeMillis()
                    val updatedConsumable = state.consumable.copy(
                        isActive = false,
                        replacementMileage = state.currentMileage,
                        replacementDate = currentTime,
                        oilLevelDropMl = if (levelDrop != null) levelDrop.ml else state.consumable.oilLevelDropMl,
                        oilLevelDropFraction = if (levelDrop != null) levelDrop.fraction else state.consumable.oilLevelDropFraction,
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(updatedConsumable)
                    // Обновляем пробег автомобиля до максимального
                    carRepository.updateCarMileageIfNeeded(state.consumable.carId, state.currentMileage)
                    // Уведомляем о изменении данных
                    dataChangeNotifier.notifyDataChanged()
                    onReplaced()
                }
            } catch (e: Exception) {
                _uiState.value = ConsumableDetailUiState.Error(e.message ?: "Ошибка замены")
            }
        }
    }

    // === Расход масла ===

    /** Падение уровня к замене — указать или исправить на уже заменённом масле */
    fun setOilLevelDrop(input: OilLevelDropInput) {
        val state = _uiState.value as? ConsumableDetailUiState.Success ?: return
        viewModelScope.launch {
            consumableRepository.updateConsumable(
                state.consumable.copy(
                    oilLevelDropMl = input.ml,
                    oilLevelDropFraction = input.fraction,
                    updatedAt = System.currentTimeMillis()
                )
            )
            dataChangeNotifier.notifyDataChanged()
        }
    }

    /**
     * Объём между MIN и MAX щупа. «Не знаю» — 1 л с пометкой: расход по щупу тогда
     * считается с погрешностью. Значение машины, а не масла: исправить его можно в любой
     * момент, и все интервалы, указанные долей щупа, пересчитаются.
     */
    fun setDipstickVolume(volume: Double, isGuess: Boolean) {
        viewModelScope.launch {
            carRepository.setOilDipstickVolume(carId, volume, isGuess)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    /** Новый долив (existing == null) или правка — через copy, как все формы (правило 1) */
    fun saveTopUp(existing: OilTopUp?, date: Long, mileage: Int, volume: Double, cost: Double?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existing == null) {
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
            } else {
                oilTopUpRepository.updateTopUp(
                    existing.copy(date = date, mileage = mileage, volume = volume, cost = cost, updatedAt = now)
                )
            }
            carRepository.updateCarMileageIfNeeded(carId, mileage)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    fun deleteTopUp(topUp: OilTopUp) {
        viewModelScope.launch {
            oilTopUpRepository.deleteTopUp(topUp)
            carRepository.updateCarMileageAfterDelete(carId, topUp.mileage)
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
