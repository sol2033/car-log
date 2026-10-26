package com.carlog.presentation.screens.consumables

import com.carlog.domain.model.CarFormRules
import com.carlog.util.moneyInputError
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.ConsumableChainRepair
import com.carlog.domain.model.ConsumableFormRules
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.domain.model.OilLevelDropInput
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull
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

    // Новое моторное масло закрывает старое: ждём ответа об уровне старого
    val oilLevelQuestion: OilLevelQuestion? = null,

    val categoryError: String? = null,
    val installationMileageError: String? = null,
    val costError: String? = null,
    // Необязательные поля: раньше неразобранный ввод молча сохранялся как «не указано»
    val serviceCostError: String? = null,
    val volumeError: String? = null,
    val intervalMileageError: String? = null,
    val intervalDaysError: String? = null,
    
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
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val consumableId: Long? = savedStateHandle.get<Long>("consumableId")
    private val preSelectedCategory: String? = savedStateHandle.get<String>("category")
    
    private val _state = MutableStateFlow(AddConsumableState())

    // Ответ об уровне закрываемого масла: id этого масла и сам ответ
    private var oilLevelAnswer: Pair<Long, OilLevelDropInput>? = null
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
            // Объём — из прошлой карточки той же категории этой машины, затем из настроек.
            // Уже введённое не трогаем: подстановка асинхронная и не должна затирать ввод
            val volume = ConsumableFormRules.defaultVolume(
                category = category,
                sameCategoryHistory = consumableRepository
                    .getConsumablesByCategory(carId, category).firstOrNull().orEmpty(),
                settingsVolume = preferences.getVolume(category).firstOrNull()
            )
            
            _state.value = _state.value.copy(
                replacementIntervalMileage = intervalMileage?.toString() ?: "",
                replacementIntervalDays = intervalDays?.toString() ?: "",
                volume = if (!ConsumableFormRules.requiresVolume(category)) ""
                    else _state.value.volume.ifBlank { volume?.let(::formatLiters) ?: "" }
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
        _state.value = _state.value.copy(serviceCost = value, serviceCostError = null)
    }
    
    fun updateVolume(value: String) {
        _state.value = _state.value.copy(volume = value, volumeError = null)
    }
    
    fun updateReplacementIntervalMileage(value: String) {
        _state.value = _state.value.copy(replacementIntervalMileage = value, intervalMileageError = null)
    }
    
    fun updateReplacementIntervalDays(value: String) {
        _state.value = _state.value.copy(replacementIntervalDays = value, intervalDaysError = null)
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
        val mileageError = BreakdownFormRules.mileageError(currentState.installationMileage)
        // Отрицательная стоимость раньше принималась и уменьшала расходы в статистике
        val costError = when {
            currentState.cost.isBlank() || currentState.cost.toDecimalOrNull() == null -> "Обязательное поле"
            currentState.cost.toDecimalOrNull()!! < 0 -> "Стоимость не может быть отрицательной"
            else -> null
        }
        
        val serviceCostError = if (currentState.isInstalledAtService) moneyInputError(currentState.serviceCost) else null
        val volumeError = if (ConsumableCategories.FLUID_CATEGORIES.contains(currentState.category)) {
            CarFormRules.volumeError(currentState.volume, required = false)
        } else null
        val intervalMileageError = ConsumableFormRules.intervalError(currentState.replacementIntervalMileage)
        val intervalDaysError = ConsumableFormRules.intervalError(currentState.replacementIntervalDays)

        if (categoryError != null || mileageError != null || costError != null ||
            serviceCostError != null || volumeError != null ||
            intervalMileageError != null || intervalDaysError != null
        ) {
            _state.value = currentState.copy(
                categoryError = categoryError,
                installationMileageError = mileageError,
                costError = costError,
                serviceCostError = serviceCostError,
                volumeError = volumeError,
                intervalMileageError = intervalMileageError,
                intervalDaysError = intervalDaysError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val installationMileage = currentState.installationMileage.toMileageOrNull() ?: 0

                val original = currentState.originalConsumable
                if (original != null) {
                    // Обновляем через copy: привязка к ТО, признак активности, пробег/дата
                    // замены и createdAt в форме не редактируются и должны сохраниться —
                    // раньше запись собиралась заново, из-за чего редактирование заменённого
                    // расходника делало его снова активным, а расходник из ТО — «отдельным»
                    val consumable = original.copy(
                        // Категория сохранённого расходника не меняется — на ней держится
                        // цепочка замен (так же в форме ТО). Раньше её можно было сменить,
                        // и в новой категории оказывалось два активных расходника
                        category = original.category,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        articleNumber = currentState.articleNumber.ifBlank { null },
                        installationMileage = installationMileage,
                        installationDate = currentState.installationDate,
                        cost = currentState.cost.toDecimalOrNull(),
                        isInstalledAtService = currentState.isInstalledAtService,
                        serviceCost = currentState.serviceCost.toDecimalOrNull(),
                        volume = currentState.volume.toDecimalOrNull(),
                        replacementIntervalMileage = ConsumableFormRules.parseInterval(currentState.replacementIntervalMileage),
                        replacementIntervalDays = ConsumableFormRules.parseInterval(currentState.replacementIntervalDays),
                        notes = currentState.notes.ifBlank { null },
                        updatedAt = currentTime
                    )
                    consumableRepository.updateConsumable(consumable)

                    // Исправили дату или пробег установки — у предшественника едет дата
                    // и пробег замены, иначе связь «кто кого заменил» рвётся
                    // (тот же приём, что при правке даты ТО)
                    if (original.installationDate != consumable.installationDate ||
                        original.installationMileage != consumable.installationMileage
                    ) {
                        val carConsumables = consumableRepository
                            .getConsumablesByCarId(original.carId).firstOrNull() ?: emptyList()
                        ConsumableChainRepair.predecessorToShift(carConsumables, original)
                            ?.let { predecessor ->
                                consumableRepository.updateConsumable(
                                    predecessor.copy(
                                        replacementDate = consumable.installationDate,
                                        replacementMileage = consumable.installationMileage,
                                        updatedAt = currentTime
                                    )
                                )
                            }
                    }
                } else {
                    // В категории активен ровно один расходник — самый свежий по пробегу.
                    // Установку задним числом (вспомнили про пропущенную замену) активной
                    // делать нельзя: иначе ресурс считался бы от неё, и всё становилось
                    // красным. План вставки решает это одинаково для всех потоков
                    val plan = ConsumableChainRepair.insertPlan(
                        sameCategory = consumableRepository
                            .getConsumablesByCategory(carId, currentState.category)
                            .firstOrNull() ?: emptyList(),
                        category = currentState.category,
                        installationMileage = installationMileage,
                        installationDate = currentState.installationDate
                    )

                    // Закрывается моторное масло — уровень старого спрашиваем так же,
                    // как при замене с его экрана. Ответ приходит в answerOilLevel()
                    val closedOil = OilConsumptionCalculator.oilClosedBy(plan.replaced)
                    val answer = oilLevelAnswer?.takeIf { it.first == closedOil?.id }?.second
                    if (closedOil != null && answer == null &&
                        appPreferences.oilConsumptionEnabled.first()
                    ) {
                        val car = carRepository.getCarByIdOnce(carId)
                        if (car != null) {
                            _state.value = _state.value.copy(
                                isSaving = false,
                                oilLevelQuestion = OilLevelQuestion(closedOil, car)
                            )
                            return@launch
                        }
                    }

                    plan.replaced.forEach { replaced ->
                        val base = if (answer != null && replaced.id == closedOil?.id) {
                            OilConsumptionCalculator.withLevelDrop(replaced, answer)
                        } else replaced
                        consumableRepository.updateConsumable(
                            base.copy(
                                isActive = false,
                                replacementDate = currentState.installationDate,
                                replacementMileage = installationMileage,
                                updatedAt = currentTime
                            )
                        )
                    }

                    val consumable = Consumable(
                        id = 0,
                        carId = carId,
                        category = currentState.category,
                        manufacturer = currentState.manufacturer.ifBlank { null },
                        articleNumber = currentState.articleNumber.ifBlank { null },
                        installationMileage = installationMileage,
                        installationDate = currentState.installationDate,
                        replacementMileage = plan.replacementMileage,
                        replacementDate = plan.replacementDate,
                        cost = currentState.cost.toDecimalOrNull(),
                        isInstalledAtService = currentState.isInstalledAtService,
                        serviceCost = currentState.serviceCost.toDecimalOrNull(),
                        volume = currentState.volume.toDecimalOrNull(),
                        replacementIntervalMileage = ConsumableFormRules.parseInterval(currentState.replacementIntervalMileage),
                        replacementIntervalDays = ConsumableFormRules.parseInterval(currentState.replacementIntervalDays),
                        isActive = plan.isActive,
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    consumableRepository.insertConsumable(consumable)
                }
                
                // Обновляем пробег автомобиля до максимального
                val editedFrom = currentState.originalConsumable?.installationMileage
                if (editedFrom != null) {
                    carRepository.updateCarMileageAfterEdit(carId, editedFrom, installationMileage)
                } else {
                    carRepository.updateCarMileageIfNeeded(carId, installationMileage)
                }
                
                // Уведомляем о изменении данных
                dataChangeNotifier.notifyDataChanged()
                
                // Снимок currentState сделан до сохранения: набранное за это время
                // он бы откатил, а поля ввода во время сохранения не блокируются
                oilLevelAnswer = null
                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                // Сообщения у исключения может не быть — без подстановки экран замирал молча
                _state.value = currentState.copy(
                    isSaving = false,
                    error = e.message ?: "Не удалось сохранить расходник"
                )
            }
        }
    }

    /** Ответ об уровне старого масла: запоминаем и сохраняем форму заново */
    fun answerOilLevel(input: OilLevelDropInput, dipstickVolume: Pair<Double, Boolean>?) {
        val question = _state.value.oilLevelQuestion ?: return
        viewModelScope.launch {
            dipstickVolume?.let { (volume, guess) ->
                carRepository.setOilDipstickVolume(question.car.id, volume, guess)
            }
            oilLevelAnswer = question.oldOil.id to input
            _state.value = _state.value.copy(oilLevelQuestion = null)
            saveConsumable()
        }
    }

    /** Отмена — форма остаётся открытой, ничего не сохранено */
    fun dismissOilLevelQuestion() {
        _state.value = _state.value.copy(oilLevelQuestion = null)
    }
}

/** 4.0 → «4», 4.2 → «4.2»: без хвостового «.0» в поле формы */
private fun formatLiters(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
