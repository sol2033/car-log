package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddConsumableState(
    val consumableId: Long? = null,
    val category: String = "",
    val manufacturer: String = "",
    val articleNumber: String = "",
    val installationMileage: String = "",
    val installationDate: Long = System.currentTimeMillis(),
    val cost: String = "",
    val isInstalledAtService: Boolean = false,
    val serviceCost: String = "",
    val volume: String = "",
    val replacementIntervalMileage: String = "",
    val replacementIntervalDays: String = "",
    val notes: String = "",
    // Исходная запись при редактировании: поля, которых нет в форме
    // (привязка к ТО, активность, пробег/дата замены, createdAt), берутся из неё
    val originalConsumable: Consumable? = null,

    val categoryError: String? = null,
    val installationMileageError: String? = null,
    val costError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val currentCarMileage: Int = 0
)

@HiltViewModel
class AddConsumableViewModel @Inject constructor(
    private val consumableRepository: ConsumableRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    private val preferences: ConsumablePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val consumableId: Long? = savedStateHandle.get<Long>("consumableId")
    private val preSelectedCategory: String? = savedStateHandle.get<String>("category")
    
    private val _state = MutableStateFlow(AddConsumableState())
    val state: StateFlow<AddConsumableState> = _state.asStateFlow()
    
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()
    
    init {
        loadCarMileage()
        loadAvailableCategories()
        preSelectedCategory?.let { category ->
            _state.value = _state.value.copy(category = category)
            setDefaultIntervalsFromPreferences(category)
        }
        consumableId?.let { loadConsumable(it) }
    }
    
    private fun loadAvailableCategories() {
        viewModelScope.launch {
            preferences.selectedCategories.collect { selected ->
                _availableCategories.value = ConsumableCategories.STANDARD_CATEGORIES + selected
            }
        }
    }
    
    private fun loadCarMileage() {
        viewModelScope.launch {
            carRepository.getCarById(carId).firstOrNull()?.let { car ->
                // Текущий пробег нужен всегда (для статуса), а вот подставлять его в поле
                // пробега установки можно только у новой записи: иначе эта асинхронная
                // подстановка может выиграть гонку у загрузки и затереть пробег существующей
                _state.value = if (consumableId == null) {
                    _state.value.copy(
                        currentCarMileage = car.currentMileage,
                        installationMileage = car.currentMileage.toString()
                    )
                } else {
                    _state.value.copy(currentCarMileage = car.currentMileage)
                }
            }
        }
    }
    
    private fun loadConsumable(id: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                consumableRepository.getConsumableById(id).firstOrNull()?.let { consumable ->
                    _state.value = AddConsumableState(
                        consumableId = consumable.id,
                        category = consumable.category,
                        manufacturer = consumable.manufacturer ?: "",
                        articleNumber = consumable.articleNumber ?: "",
                        installationMileage = consumable.installationMileage.toString(),
                        installationDate = consumable.installationDate,
                        cost = consumable.cost?.toString() ?: "",
                        isInstalledAtService = consumable.isInstalledAtService,
                        serviceCost = consumable.serviceCost?.toString() ?: "",
                        volume = consumable.volume?.toString() ?: "",
                        replacementIntervalMileage = consumable.replacementIntervalMileage?.toString() ?: "",
                        replacementIntervalDays = consumable.replacementIntervalDays?.toString() ?: "",
                        notes = consumable.notes ?: "",
                        originalConsumable = consumable,
                        isLoading = false,
                        currentCarMileage = _state.value.currentCarMileage
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
    
    private fun setDefaultIntervalsFromPreferences(category: String) {
        viewModelScope.launch {
            val intervalMileage = preferences.getIntervalMileage(category).firstOrNull()
                ?: ConsumableCategories.DEFAULT_INTERVALS[category]?.first
            val intervalDays = preferences.getIntervalDays(category).firstOrNull()
                ?: ConsumableCategories.DEFAULT_INTERVALS[category]?.second
            val volume = if (ConsumableCategories.FLUID_CATEGORIES.contains(category)) {
                preferences.getVolume(category).firstOrNull()
            } else {
                null
            }
            
            _state.value = _state.value.copy(
                replacementIntervalMileage = intervalMileage?.toString() ?: "",
                replacementIntervalDays = intervalDays?.toString() ?: "",
                volume = volume?.toString() ?: ""
            )
        }
    }
    
    private fun setDefaultIntervals(category: String) {
        ConsumableCategories.DEFAULT_INTERVALS[category]?.let { (mileage, days) ->
            _state.value = _state.value.copy(
                replacementIntervalMileage = mileage?.toString() ?: "",
                replacementIntervalDays = days?.toString() ?: ""
            )
        }
    }
    
    fun updateCategory(category: String) {
        _state.value = _state.value.copy(
            category = category,
            categoryError = if (category.isNotBlank()) null else _state.value.categoryError
        )
        if (_state.value.replacementIntervalMileage.isBlank() && 
            _state.value.replacementIntervalDays.isBlank()) {
            setDefaultIntervalsFromPreferences(category)
        }
    }
    
    fun updateManufacturer(value: String) {
        _state.value = _state.value.copy(manufacturer = value)
    }
    
    fun updateArticleNumber(value: String) {
        _state.value = _state.value.copy(articleNumber = value)
    }
    
    fun updateInstallationMileage(value: String) {
        _state.value = _state.value.copy(
            installationMileage = value,
            installationMileageError = if (value.isNotBlank()) null else _state.value.installationMileageError
        )
    }
    
    fun updateInstallationDate(date: Long) {
        _state.value = _state.value.copy(installationDate = date)
    }
    
    fun updateCost(value: String) {
        _state.value = _state.value.copy(cost = value)
    }
    
    fun updateIsInstalledAtService(value: Boolean) {
        _state.value = _state.value.copy(isInstalledAtService = value)
    }
    
    fun updateServiceCost(value: String) {
        _state.value = _state.value.copy(serviceCost = value)
    }
    
    fun updateVolume(value: String) {
        _state.value = _state.value.copy(volume = value)
    }
    
    fun updateReplacementIntervalMileage(value: String) {
        _state.value = _state.value.copy(replacementIntervalMileage = value)
    }
    
    fun updateReplacementIntervalDays(value: String) {
        _state.value = _state.value.copy(replacementIntervalDays = value)
    }
    
    fun updateNotes(value: String) {
        _state.value = _state.value.copy(notes = value)
    }
    
    fun saveConsumable() {
        val currentState = _state.value

        // Запись ещё не догрузилась — сохранять нельзя: обновлять нечего,
        // а вставка создала бы дубликат
        if (currentState.consumableId != null && currentState.originalConsumable == null) return

        val categoryError = if (currentState.category.isBlank()) "Обязательное поле" else null
        val mileageError = if (currentState.installationMileage.isBlank()) "Обязательное поле" else null
        val costError = if (currentState.cost.isBlank() || currentState.cost.toDoubleOrNull() == null) "Обязательное поле" else null
        
        if (categoryError != null || mileageError != null || costError != null) {
            _state.value = currentState.copy(
                categoryError = categoryError,
                installationMileageError = mileageError,
                costError = costError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()

                val original = currentState.originalConsumable
                if (original != null) {
                    // Обновляем через copy: привязка к ТО, признак активности, пробег/дата
                    // замены и createdAt в форме не редактируются и должны сохраниться —
                    // раньше запись собиралась заново, из-за чего редактирование заменённого
                    // расходника делало его снова активным, а расходник из ТО — «отдельным»
                    val consumable = original.copy(
                        category = currentState.category,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        articleNumber = currentState.articleNumber.ifBlank { null },
                        installationMileage = currentState.installationMileage.toInt(),
                        installationDate = currentState.installationDate,
                        cost = currentState.cost.toDoubleOrNull(),
                        isInstalledAtService = currentState.isInstalledAtService,
                        serviceCost = currentState.serviceCost.toDoubleOrNull(),
                        volume = currentState.volume.toDoubleOrNull(),
                        replacementIntervalMileage = currentState.replacementIntervalMileage.toIntOrNull(),
                        replacementIntervalDays = currentState.replacementIntervalDays.toIntOrNull(),
                        notes = currentState.notes.ifBlank { null },
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(consumable)
                } else {
                    val consumable = Consumable(
                        id = 0,
                        carId = carId,
                        category = currentState.category,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        articleNumber = currentState.articleNumber.ifBlank { null },
                        installationMileage = currentState.installationMileage.toInt(),
                        installationDate = currentState.installationDate,
                        replacementMileage = null,
                        replacementDate = null,
                        cost = currentState.cost.toDoubleOrNull(),
                        isInstalledAtService = currentState.isInstalledAtService,
                        serviceCost = currentState.serviceCost.toDoubleOrNull(),
                        volume = currentState.volume.toDoubleOrNull(),
                        replacementIntervalMileage = currentState.replacementIntervalMileage.toIntOrNull(),
                        replacementIntervalDays = currentState.replacementIntervalDays.toIntOrNull(),
                        isActive = true,
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    // В категории должен остаться один активный расходник: прежние активные
                    // деактивируем как заменённые этой установкой (та же логика, что в потоке ТО
                    // и на экране деталей; раньше прямое добавление давало два активных)
                    val consumablesInCategory = consumableRepository
                        .getConsumablesByCategory(carId, currentState.category)
                        .firstOrNull() ?: emptyList()
                    consumablesInCategory.filter { it.isActive }.forEach { existing ->
                        consumableRepository.updateConsumable(
                            existing.copy(
                                isActive = false,
                                replacementDate = currentState.installationDate,
                                replacementMileage = currentState.installationMileage.toInt(),
                                updatedAt = currentTime
                            )
                        )
                    }
                    consumableRepository.insertConsumable(consumable)
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(carId, currentState.installationMileage.toInt())
                
                // Уведомляем о изменении данных
                dataChangeNotifier.notifyDataChanged()
                
                _state.value = currentState.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _state.value = currentState.copy(isSaving = false, error = e.message)
            }
        }
    }
}
