package com.carlog.presentation.screens.breakdowns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.BreakdownRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.PartRepository
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableChainRepair
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.ConsumableFormRules
import com.carlog.domain.model.EventComposition
import com.carlog.domain.model.MaintenanceType
import com.carlog.domain.model.Part
import com.carlog.domain.model.WorkItem
import com.carlog.domain.model.totalCost
import com.carlog.presentation.components.EventPart
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.presentation.components.referencedEventPhotos
import com.carlog.util.FileHelper
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Расходник в форме обслуживания.
 *
 * У нового ТО это ещё не существующая запись, у сохранённого — загруженная из БД
 * ([consumableId] не null). Как и у запчасти события ([EventPart.partId]), id нужен,
 * чтобы при сохранении обновить исходную строку, а не вставить дубликат.
 */
data class TemporaryConsumable(
    val category: String,
    // Название позиции категории «Другое»; у обычных категорий null
    val customName: String? = null,
    val manufacturer: String?,
    val articleNumber: String?,
    val cost: Double?,
    val isInstalledAtService: Boolean,
    val serviceCost: Double?,
    val volume: Double?,
    val replacementIntervalMileage: Int?,
    val replacementIntervalDays: Int?,
    val notes: String?,
    /** id уже сохранённой записи; null — расходник добавлен в этот заход */
    val consumableId: Long? = null
) {
    fun toConsumable(
        carId: Long,
        installationMileage: Int,
        installationDate: Long,
        linkedMaintenanceId: Long?
    ): Consumable {
        return Consumable(
            carId = carId,
            category = category,
            customName = customName,
            manufacturer = manufacturer,
            articleNumber = articleNumber,
            installationMileage = installationMileage,
            installationDate = installationDate,
            linkedMaintenanceId = linkedMaintenanceId,
            replacementMileage = null,
            replacementDate = null,
            cost = cost,
            isInstalledAtService = isInstalledAtService,
            serviceCost = serviceCost,
            volume = volume,
            replacementIntervalMileage = replacementIntervalMileage,
            replacementIntervalDays = replacementIntervalDays,
            isActive = true,
            notes = notes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/** Сохранённый расходник — обратно в форму обслуживания */
private fun Consumable.toTemporary() = TemporaryConsumable(
    category = category,
    customName = customName,
    manufacturer = manufacturer,
    articleNumber = articleNumber,
    cost = cost,
    isInstalledAtService = isInstalledAtService,
    serviceCost = serviceCost,
    volume = volume,
    replacementIntervalMileage = replacementIntervalMileage,
    replacementIntervalDays = replacementIntervalDays,
    notes = notes,
    consumableId = id
)

/**
 * Все вложения, на которые ссылались сохранённые записи: файлы самого обслуживания
 * плюс файлы его запчастей. По разнице с «затронутыми» чистятся файлы-сироты.
 */
private fun Breakdown.attachmentsWith(parts: List<Part>): Set<String> =
    (photosPaths.orEmpty() + parts.flatMap { it.photosPaths.orEmpty() }).toSet()

/** Подстановки для диалога расходника ТО: интервалы и объём выбранной категории */
data class ConsumableDefaults(
    val category: String,
    val intervalMileage: Int?,
    val intervalDays: Int?,
    val volume: Double?
)

data class AddBreakdownState(
    val carId: Long = 0,
    val breakdownId: Long? = null,
    val maintenanceType: MaintenanceType? = MaintenanceType.REPAIR,
    val title: String = "",
    val description: String = "",
    val breakdownDate: Long = System.currentTimeMillis(),
    val breakdownMileage: String = "",
    val brokenPartName: String = "",
    val isWarrantyRepair: Boolean = false,
    val isServiceMaintenance: Boolean = false,
    val useGeneralPartsCost: Boolean = true,
    val partsCost: String = "",
    val addedParts: List<EventPart> = emptyList(),
    val temporaryConsumables: List<TemporaryConsumable> = emptyList(),
    val serviceCost: String = "",
    // Работы сервиса списком: false — стоимость работ введена одной суммой
    val useGeneralServiceCost: Boolean = true,
    val workItems: List<WorkItem> = emptyList(),
    val serviceName: String = "",
    val serviceAddress: String = "",
    val notes: String = "",
    // Вложения самого обслуживания: фотографии и PDF
    val photosPaths: List<String> = emptyList(),
    // Исходная запись при редактировании: createdAt и поля, которых нет в форме,
    // берутся отсюда
    val originalBreakdown: Breakdown? = null,
    // Запчасти, привязанные к записи на момент загрузки
    val originalPartIds: List<Long> = emptyList(),
    // Расходники, привязанные к записи на момент загрузки
    val originalConsumableIds: List<Long> = emptyList(),
    // Вложения, на которые ссылались сохранённые записи (события и его запчастей)
    val originalPhotos: Set<String> = emptySet(),
    // Все вложения, прошедшие через экран (для чистки файлов-сирот)
    val touchedPhotos: Set<String> = emptySet(),

    val maintenanceTypeError: String? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val breakdownMileageError: String? = null,
    val partsCostError: String? = null,
    val consumablesError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    /** Стоимость расходников события — это его ТО-часть */
    val consumablesCost: Double
        get() = temporaryConsumables.sumOf { it.cost ?: 0.0 }

    /** Стоимость запчастей: списком — сумма позиций, иначе введённая общая сумма */
    val partsListCost: Double
        get() = if (useGeneralPartsCost) {
            partsCost.toDecimalOrNull() ?: 0.0
        } else {
            addedParts.sumOf { it.price }
        }

    /**
     * Стоимость запчастей события целиком: расходники плюс запчасти.
     * Одно событие может быть сразу ТО, ремонтом и тюнингом, поэтому слагаемых два.
     */
    val calculatedPartsCost: Double
        get() = consumablesCost + partsListCost

    /**
     * Типизированные позиции — те, по которым видно, что это за событие.
     * Пока их нет (диагностика, развал, запчасти общей суммой), тип выбирается руками.
     */
    val hasTypedPositions: Boolean
        get() = temporaryConsumables.isNotEmpty() || (!useGeneralPartsCost && addedParts.isNotEmpty())

    /** Типы события — по его содержимому; порядок фиксированный (ТО → Ремонт → Тюнинг) */
    val eventTypes: List<MaintenanceType>
        get() = EventComposition.typesOf(
            fallbackType = maintenanceType,
            hasConsumables = temporaryConsumables.isNotEmpty(),
            partTypes = if (useGeneralPartsCost) emptyList() else addedParts.map { it.maintenanceType }
        )

    /**
     * Стоимость работ: в режиме списка её источник — сами работы, а не поле ввода.
     *
     * В режиме общей суммы поведение ровно прежнее — введённое значение сохраняется как есть,
     * даже если галочка «в сервисе» снята. Иначе редактирование старой записи, у которой
     * стоимость работ проставлена без этой галочки, молча обнулило бы её стоимость.
     */
    val calculatedServiceCost: Double?
        get() = if (!useGeneralServiceCost) {
            workItems.totalCost()
        } else {
            serviceCost.toDecimalOrNull()
        }
    
    val isEditMode: Boolean
        get() = breakdownId != null

    /**
     * Есть ли подсвеченные поля — для подсказки у кнопки сохранения.
     *
     * Форма длинная, и поле с ошибкой обычно оказывается за краем экрана: нажатие
     * на «Сохранить» выглядело как «ничего не происходит». Признак вычисляемый,
     * поэтому подсказка исчезает сама, как только поле исправили.
     */
    val hasFieldErrors: Boolean
        get() = maintenanceTypeError != null || titleError != null || descriptionError != null ||
            breakdownMileageError != null || partsCostError != null || consumablesError != null
}

@HiltViewModel
class AddBreakdownViewModel @Inject constructor(
    private val breakdownRepository: BreakdownRepository,
    private val carRepository: CarRepository,
    private val partRepository: PartRepository,
    private val consumableRepository: ConsumableRepository,
    private val consumablePreferences: ConsumablePreferences,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddBreakdownState())
    val state: StateFlow<AddBreakdownState> = _state.asStateFlow()
    
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()

    private val _consumableDefaults = MutableStateFlow<ConsumableDefaults?>(null)
    val consumableDefaults: StateFlow<ConsumableDefaults?> = _consumableDefaults.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val breakdownId = savedStateHandle.get<Long>("breakdownId")
        
        _state.value = _state.value.copy(carId = carId)
        
        loadAvailableCategories()
        
        // Пробег машины подставляем ТОЛЬКО для новой записи: у существующей он свой,
        // а обе загрузки асинхронные — раньше подстановка могла выиграть гонку и затереть
        // загруженный пробег текущим пробегом машины (при сохранении он уезжал в запчасти)
        if (breakdownId != null && breakdownId != -1L) {
            loadBreakdown(breakdownId)
        } else {
            viewModelScope.launch {
                val car = carRepository.getCarById(carId).firstOrNull()
                car?.let {
                    _state.value = _state.value.copy(
                        breakdownMileage = it.currentMileage.toString()
                    )
                }
            }
        }
    }
    
    private fun loadAvailableCategories() {
        viewModelScope.launch {
            consumablePreferences.selectedCategories.collect { selected ->
                // Единый источник категорий — ConsumableCategories: стандартные + включённые
                // пользователем дополнительные (ранее здесь был расходившийся со справочником
                // захардкоженный список, из-за чего расходники ТО могли попадать в категории,
                // которых нет на экране расходников)
                _availableCategories.value =
                    (ConsumableCategories.STANDARD_CATEGORIES + selected).distinct()
            }
        }
    }
    
    private fun loadBreakdown(breakdownId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val breakdown = breakdownRepository.getBreakdownById(breakdownId).firstOrNull()
                if (breakdown != null) {
                    // Расходники ТО грузим в форму: без этого их нельзя было ни исправить,
                    // ни дополнить, а сохранение брало стоимость запчастей из старой записи.
                    // Идём по linkedConsumableIds, а не по linkedMaintenanceId: связь
                    // хранится в обе стороны, но авторитетна именно она (её чинит миграция 21)
                    val linkedConsumables = breakdown.linkedConsumableIds.orEmpty()
                        .mapNotNull { consumableRepository.getConsumableById(it).firstOrNull() }

                    // Список запчастей восстанавливаем: раньше он не загружался, и сохранение
                    // отредактированной записи отвязывало запчасти (installedPartIds = null),
                    // после чего их стоимость начинала считаться в статистике второй раз
                    val linkedParts = breakdown.installedPartIds.orEmpty().mapNotNull { partId ->
                        partRepository.getPartById(partId).firstOrNull()
                    }

                    _state.value = AddBreakdownState(
                        carId = breakdown.carId,
                        breakdownId = breakdown.id,
                        maintenanceType = breakdown.maintenanceType?.let { MaintenanceType.fromString(it) },
                        title = breakdown.title,
                        description = breakdown.description,
                        breakdownDate = breakdown.breakdownDate,
                        breakdownMileage = breakdown.breakdownMileage.toString(),
                        brokenPartName = breakdown.brokenPartName ?: "",
                        isWarrantyRepair = breakdown.isWarrantyRepair,
                        isServiceMaintenance = breakdown.isServiceMaintenance,
                        // Общая сумма — это та часть стоимости запчастей, которая не разложена
                        // по позициям. У обычного ТО она нулевая (всё в расходниках), у старого
                        // ТО без привязок — вся сохранённая сумма, и потерять её нельзя
                        partsCost = formatResidualPartsCost(
                            breakdown.partsCost - linkedConsumables.sumOf { it.cost ?: 0.0 }
                        ),
                        useGeneralPartsCost = !BreakdownFormRules.usesPartsList(linkedParts),
                        addedParts = linkedParts.map { part ->
                            EventPart(
                                name = part.name,
                                manufacturer = part.manufacturer ?: "",
                                partNumber = part.partNumber ?: "",
                                price = part.price,
                                notes = part.notes ?: "",
                                photosPaths = part.photosPaths ?: emptyList(),
                                showInPartsList = part.showInPartsList,
                                maintenanceType = EventComposition.typeOfPart(part.maintenanceType),
                                partId = part.id
                            )
                        },
                        temporaryConsumables = linkedConsumables.map { it.toTemporary() },
                        originalPartIds = linkedParts.map { it.id },
                        originalConsumableIds = linkedConsumables.map { it.id },
                        photosPaths = breakdown.photosPaths ?: emptyList(),
                        originalPhotos = breakdown.attachmentsWith(linkedParts),
                        touchedPhotos = breakdown.attachmentsWith(linkedParts),
                        originalBreakdown = breakdown,
                        serviceCost = breakdown.serviceCost?.toString() ?: "",
                        workItems = breakdown.workItems ?: emptyList(),
                        useGeneralServiceCost = breakdown.workItems.isNullOrEmpty(),
                        serviceName = breakdown.serviceName ?: "",
                        serviceAddress = breakdown.serviceAddress ?: "",
                        notes = breakdown.notes ?: "",
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Поломка не найдена"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun updateMaintenanceType(type: MaintenanceType?) {
        _state.value = _state.value.copy(
            maintenanceType = type,
            maintenanceTypeError = if (type != null) null else _state.value.maintenanceTypeError,
            // При смене типа на ТО сбрасываем ошибку расходников
            consumablesError = if (type == MaintenanceType.SCHEDULED_SERVICE && _state.value.temporaryConsumables.isNotEmpty()) null else _state.value.consumablesError
        )
    }
    
    fun updateTitle(title: String) {
        _state.value = _state.value.copy(
            title = title,
            titleError = if (title.isNotBlank()) null else _state.value.titleError
        )
    }
    
    fun updateDescription(description: String) {
        _state.value = _state.value.copy(
            description = description,
            descriptionError = if (description.isNotBlank()) null else _state.value.descriptionError
        )
    }
    
    fun updateBreakdownDate(date: Long) {
        _state.value = _state.value.copy(breakdownDate = date)
    }
    
    fun updateBreakdownMileage(mileage: String) {
        _state.value = _state.value.copy(
            breakdownMileage = mileage,
            breakdownMileageError = if (mileage.isNotBlank()) null else _state.value.breakdownMileageError
        )
    }
    
    fun updateBrokenPartName(name: String) {
        _state.value = _state.value.copy(brokenPartName = name)
    }
    
    fun updatePartsCost(cost: String) {
        _state.value = _state.value.copy(
            partsCost = cost,
            partsCostError = if (cost.isNotBlank()) null else _state.value.partsCostError
        )
    }
    
    fun toggleUseGeneralServiceCost(useGeneral: Boolean) {
        _state.value = _state.value.copy(useGeneralServiceCost = useGeneral)
    }

    fun addWorkItem(item: WorkItem) {
        _state.value = _state.value.copy(workItems = _state.value.workItems + item)
    }

    fun updateWorkItem(index: Int, item: WorkItem) {
        val items = _state.value.workItems.toMutableList()
        if (index !in items.indices) return
        items[index] = item
        _state.value = _state.value.copy(workItems = items)
    }

    fun removeWorkItem(index: Int) {
        val items = _state.value.workItems.toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        _state.value = _state.value.copy(workItems = items)
    }

    fun updateServiceCost(cost: String) {
        _state.value = _state.value.copy(serviceCost = cost)
    }
    
    fun updateServiceName(name: String) {
        _state.value = _state.value.copy(serviceName = name)
    }
    
    fun updateServiceAddress(address: String) {
        _state.value = _state.value.copy(serviceAddress = address)
    }
    
    fun toggleWarrantyRepair(isWarranty: Boolean) {
        _state.value = _state.value.copy(isWarrantyRepair = isWarranty)
    }
    
    fun toggleServiceMaintenance(isService: Boolean) {
        _state.value = _state.value.copy(isServiceMaintenance = isService)
    }
    
    fun toggleUseGeneralPartsCost(useGeneral: Boolean) {
        _state.value = _state.value.copy(useGeneralPartsCost = useGeneral)
    }
    
    // Добавить временный расходник (из callback после AddConsumableScreen)
    fun addTemporaryConsumable(consumable: TemporaryConsumable): Boolean {
        // Валидация: проверка уникальности категории
        val existingCategories = _state.value.temporaryConsumables.map { it.category }
        if (ConsumableFormRules.isDuplicateCategory(existingCategories, consumable.category)) {
            _state.value = _state.value.copy(
                consumablesError = "Расходник категории '${consumable.category}' уже добавлен"
            )
            return false
        }
        
        val updatedConsumables = _state.value.temporaryConsumables + consumable
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
        return true
    }
    
    // Обновить временный расходник
    fun updateTemporaryConsumable(index: Int, consumable: TemporaryConsumable): Boolean {
        val currentConsumables = _state.value.temporaryConsumables
        if (index !in currentConsumables.indices) return false
        
        // Проверка уникальности категории (кроме текущего)
        val otherCategories = currentConsumables.filterIndexed { i, _ -> i != index }.map { it.category }
        if (ConsumableFormRules.isDuplicateCategory(otherCategories, consumable.category)) {
            _state.value = _state.value.copy(
                consumablesError = "Расходник категории '${consumable.category}' уже добавлен"
            )
            return false
        }
        
        val updatedConsumables = currentConsumables.toMutableList().apply {
            set(index, consumable)
        }
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
        return true
    }
    
    fun removeTemporaryConsumable(index: Int) {
        val updatedConsumables = _state.value.temporaryConsumables.toMutableList().apply {
            removeAt(index)
        }
        _state.value = _state.value.copy(
            temporaryConsumables = updatedConsumables,
            consumablesError = null
        )
    }
    
    fun addPart(part: EventPart) {
        _state.value = _state.value.copy(
            addedParts = _state.value.addedParts + part,
            touchedPhotos = _state.value.touchedPhotos + part.photosPaths
        )
    }

    fun updatePart(index: Int, part: EventPart) {
        val parts = _state.value.addedParts
        if (index !in parts.indices) return
        _state.value = _state.value.copy(
            addedParts = parts.toMutableList().apply { set(index, part) },
            touchedPhotos = _state.value.touchedPhotos + part.photosPaths
        )
    }

    fun removePart(index: Int) {
        val parts = _state.value.addedParts
        if (index !in parts.indices) return
        // Файлы удалённой позиции подчистятся при сохранении или уходе с экрана
        _state.value = _state.value.copy(
            addedParts = parts.toMutableList().apply { removeAt(index) }
        )
    }

    /** Вложение обслуживания: файл уже в хранилище, поэтому попадает в «затронутые» */
    fun addPhoto(path: String) {
        _state.value = _state.value.copy(
            photosPaths = _state.value.photosPaths + path,
            touchedPhotos = _state.value.touchedPhotos + path
        )
    }

    fun removePhoto(path: String) {
        // Файл не удаляем здесь: при отказе от правки запись осталась бы со ссылкой
        // на удалённый файл. Сироты чистятся одним местом — при сохранении и в onCleared
        _state.value = _state.value.copy(photosPaths = _state.value.photosPaths - path)
    }

    /** Фото убрали в диалоге — запоминаем, чтобы удалить файл вместе с остальными сиротами */
    fun onPartPhotoDiscarded(path: String) {
        _state.value = _state.value.copy(
            touchedPhotos = _state.value.touchedPhotos + path
        )
    }

    override fun onCleared() {
        // Ушли, не сохранив: выбранные в этот заход файлы никому не принадлежат
        val current = _state.value
        if (!current.isSaved) {
            FileHelper.deleteFiles(
                orphanPhotosToDelete(current.touchedPhotos, current.originalPhotos)
            )
        }
        super.onCleared()
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    /**
     * Интервалы и объём для выбранной категории: сначала настройки пользователя,
     * затем дефолты категории. Раньше в диалоге ТО их приходилось вбивать руками,
     * хотя в отдельной форме расходника подстановка уже работала.
     */
    fun loadConsumableDefaults(category: String) {
        viewModelScope.launch {
            if (!ConsumableFormRules.supportsReminders(category)) {
                _consumableDefaults.value = ConsumableDefaults(category, null, null, null)
                return@launch
            }
            val intervalMileage = consumablePreferences.getIntervalMileage(category).firstOrNull()
                ?: ConsumableCategories.DEFAULT_INTERVALS[category]?.first
            val intervalDays = consumablePreferences.getIntervalDays(category).firstOrNull()
                ?: ConsumableCategories.DEFAULT_INTERVALS[category]?.second
            val volume = if (ConsumableFormRules.requiresVolume(category)) {
                consumablePreferences.getVolume(category).firstOrNull()
            } else {
                null
            }
            _consumableDefaults.value =
                ConsumableDefaults(category, intervalMileage, intervalDays, volume)
        }
    }

    fun saveBreakdown() {
        val currentState = _state.value

        // Запись ещё не догрузилась — сохранять нельзя: вставка создала бы дубликат
        if (currentState.breakdownId != null && currentState.originalBreakdown == null) return

        // Тип спрашиваем, только когда вывести его неоткуда: нет ни расходников,
        // ни списка запчастей. Как только появилась первая позиция, тип считается сам
        val maintenanceTypeError =
            if (!currentState.hasTypedPositions && currentState.maintenanceType == null) {
                "Выберите тип обслуживания"
            } else {
                null
            }
        val titleError = if (currentState.title.isBlank()) "Обязательное поле" else null
        val descriptionError = if (currentState.description.isBlank()) "Обязательное поле" else null
        val breakdownMileageError = BreakdownFormRules.mileageError(currentState.breakdownMileage)

        // Правило «для ТО обязателен минимум 1 расходник» больше не нужно: ТО — это и есть
        // наличие расходников. Событие без расходников просто не будет считаться ТО
        val consumablesError: String? = null
        
        // В режиме списка список не должен быть пустым — иначе непонятно, зачем режим включён.
        // Общая сумма необязательна: у события с расходниками или из одних работ запчастей нет
        val partsCostError =
            if (!currentState.useGeneralPartsCost && currentState.addedParts.isEmpty()) {
                "Добавьте хотя бы одну запчасть"
            } else {
                null
            }
        
        if (maintenanceTypeError != null || titleError != null || descriptionError != null || 
            breakdownMileageError != null || partsCostError != null || consumablesError != null) {
            _state.value = currentState.copy(
                maintenanceTypeError = maintenanceTypeError,
                titleError = titleError,
                descriptionError = descriptionError,
                breakdownMileageError = breakdownMileageError,
                partsCostError = partsCostError,
                consumablesError = consumablesError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                val breakdownMileage = currentState.breakdownMileage.toMileageOrNull() ?: 0
                
                // Расходники и запчасти складываются: событие может быть и ТО, и ремонтом сразу.
                // В статистике эта сумма раскладывается обратно по позициям (EventComposition)
                val partsCost = currentState.calculatedPartsCost
                
                // В режиме списка стоимость работ — это их сумма; так «Услуги сервисов»
                // в статистике продолжают считаться по serviceCost и задвоиться не могут
                val serviceCost = currentState.calculatedServiceCost
                val workItems = if (!currentState.useGeneralServiceCost) {
                    currentState.workItems.ifEmpty { null }
                } else {
                    null
                }
                val totalCost = partsCost + (serviceCost ?: 0.0)
                
                // Расходники ТО: обновляем сохранённые, создаём добавленные, удаляем убранные.
                // До 1.2.4 сюда попадало только создание нового ТО — расходники готового
                // не редактировались вовсе (кнопка добавления была отключена)
                val linkedConsumableIds = mutableListOf<Long>()
                if (currentState.temporaryConsumables.isNotEmpty() ||
                    currentState.originalConsumableIds.isNotEmpty()
                ) {
                    // Снимок до изменений: по нему ищутся предшественники в цепочке замен
                    val carConsumables = consumableRepository
                        .getConsumablesByCarId(currentState.carId).firstOrNull() ?: emptyList()

                    for (tempConsumable in currentState.temporaryConsumables) {
                        val existing = tempConsumable.consumableId
                            ?.let { id -> carConsumables.firstOrNull { it.id == id } }

                        if (existing != null) {
                            // Категория сохранённой позиции в форме не меняется — на ней
                            // держится цепочка замен, поэтому берём её из исходной записи
                            val updated = existing.copy(
                                customName = tempConsumable.customName,
                                manufacturer = tempConsumable.manufacturer,
                                articleNumber = tempConsumable.articleNumber,
                                cost = tempConsumable.cost,
                                isInstalledAtService = tempConsumable.isInstalledAtService,
                                serviceCost = tempConsumable.serviceCost,
                                volume = tempConsumable.volume,
                                replacementIntervalMileage = tempConsumable.replacementIntervalMileage,
                                replacementIntervalDays = tempConsumable.replacementIntervalDays,
                                notes = tempConsumable.notes,
                                // Дата и пробег установки следуют событию — как у его запчастей
                                installationMileage = breakdownMileage,
                                installationDate = currentState.breakdownDate,
                                // Связь держится в обе стороны: по linkedMaintenanceId
                                // расходник попадает в карточку события и в его ТО-часть
                                // в статистике (иначе те же деньги считаются ещё и «отдельными»)
                                linkedMaintenanceId = currentState.breakdownId
                                    ?: existing.linkedMaintenanceId,
                                updatedAt = currentTime
                            )
                            consumableRepository.updateConsumable(updated)

                            // Дату ТО исправили — у предшественника едет дата замены,
                            // иначе связь «кто кого заменил» рвётся
                            if (existing.installationDate != updated.installationDate ||
                                existing.installationMileage != updated.installationMileage
                            ) {
                                ConsumableChainRepair.predecessorToShift(carConsumables, existing)
                                    ?.let { predecessor ->
                                        consumableRepository.updateConsumable(
                                            predecessor.copy(
                                                replacementDate = updated.installationDate,
                                                replacementMileage = updated.installationMileage,
                                                updatedAt = currentTime
                                            )
                                        )
                                    }
                            }

                            linkedConsumableIds.add(existing.id)
                        } else {
                            // Куда новая позиция встаёт в цепочке категории, решает её пробег:
                            // событие может быть и давним (заполняют пропущенное ТО), и тогда
                            // активной остаётся более свежая установка. Разовые позиции
                            // «Другое» цепочки не образуют — их план всегда «просто активна»
                            val plan = ConsumableChainRepair.insertPlan(
                                sameCategory = consumableRepository
                                    .getConsumablesByCategory(
                                        currentState.carId,
                                        tempConsumable.category
                                    ).firstOrNull() ?: emptyList(),
                                category = tempConsumable.category,
                                installationMileage = breakdownMileage,
                                installationDate = currentState.breakdownDate
                            )

                            plan.replaced.forEach { replaced ->
                                consumableRepository.updateConsumable(
                                    replaced.copy(
                                        isActive = false,
                                        replacementDate = currentState.breakdownDate,
                                        replacementMileage = breakdownMileage,
                                        updatedAt = currentTime
                                    )
                                )
                            }

                            val consumable = tempConsumable.toConsumable(
                                carId = currentState.carId,
                                installationMileage = breakdownMileage,
                                installationDate = currentState.breakdownDate,
                                // Для нового ТО id ещё нет — проставляется после вставки записи
                                linkedMaintenanceId = currentState.breakdownId
                            ).copy(
                                isActive = plan.isActive,
                                replacementMileage = plan.replacementMileage,
                                replacementDate = plan.replacementDate
                            )
                            linkedConsumableIds.add(consumableRepository.insertConsumable(consumable))
                        }
                    }

                    // Убранные из формы позиции удаляются из модуля «Расходники»;
                    // предшественник возвращается в активные, если после этого
                    // в категории не осталось ни одного активного.
                    // Снимок берём заново: если в этот же заход позицию заменили новой в той же
                    // категории, активна уже она — и воскрешать предшественника нельзя
                    val consumablesAfterUpsert = consumableRepository
                        .getConsumablesByCarId(currentState.carId).firstOrNull() ?: emptyList()
                    currentState.originalConsumableIds
                        .filterNot { linkedConsumableIds.contains(it) }
                        .mapNotNull { id -> carConsumables.firstOrNull { it.id == id } }
                        .forEach { removed ->
                            consumableRepository.deleteConsumable(removed)
                            ConsumableChainRepair
                                .reactivationTarget(
                                    consumablesAfterUpsert.filterNot { it.id == removed.id },
                                    removed
                                )
                                ?.let { predecessor ->
                                    consumableRepository.updateConsumable(
                                        predecessor.copy(
                                            isActive = true,
                                            replacementDate = null,
                                            replacementMileage = null,
                                            updatedAt = currentTime
                                        )
                                    )
                                }
                        }
                }
                
                // Если используются конкретные запчасти, добавляем их в БД.
                // Уже сохранённые (с partId) обновляются, а не вставляются заново —
                // иначе редактирование плодило бы дубликаты в модуле «Запчасти»
                val addedPartIds = mutableListOf<Long>()
                // Способ установки следует галочке «В сервисе» у самой записи: раньше здесь
                // было жёстко «Сервис», и запчасть из работы, сделанной своими руками,
                // всё равно числилась установленной в сервисе
                val partInstallationType =
                    if (currentState.isServiceMaintenance) "Сервис" else "Самостоятельно"
                if (!currentState.useGeneralPartsCost) {
                    for (addedPart in currentState.addedParts) {
                        val existing = addedPart.partId?.let { partRepository.getPartById(it).firstOrNull() }
                        if (existing != null) {
                            partRepository.updatePart(
                                existing.copy(
                                    name = addedPart.name,
                                    manufacturer = addedPart.manufacturer.ifBlank { null },
                                    partNumber = addedPart.partNumber.ifBlank { null },
                                    installDate = currentState.breakdownDate,
                                    installMileage = breakdownMileage,
                                    price = addedPart.price,
                                    installationType = partInstallationType,
                                    photosPaths = addedPart.photosPaths.ifEmpty { null },
                                    notes = addedPart.notes.ifBlank { null },
                                    // Тип берётся у самой позиции, а не у события
                                    maintenanceType = addedPart.maintenanceType.name,
                                    showInPartsList = addedPart.showInPartsList,
                                    updatedAt = currentTime
                                )
                            )
                            addedPartIds.add(existing.id)
                        } else {
                            val part = Part(
                                carId = currentState.carId,
                                name = addedPart.name,
                                manufacturer = addedPart.manufacturer.ifBlank { null },
                                partNumber = addedPart.partNumber.ifBlank { null },
                                installDate = currentState.breakdownDate,
                                installMileage = breakdownMileage,
                                installationType = partInstallationType,
                                price = addedPart.price,
                                servicePrice = null,
                                photosPaths = addedPart.photosPaths.ifEmpty { null },
                                isBroken = false,
                                notes = addedPart.notes.ifBlank { null },
                                maintenanceType = addedPart.maintenanceType.name,
                                showInPartsList = addedPart.showInPartsList,
                                createdAt = currentTime,
                                updatedAt = currentTime
                            )
                            addedPartIds.add(partRepository.insertPart(part))
                        }
                    }
                }

                // Запчасти, убранные из списка (или весь список — при переходе на общую сумму),
                // больше не относятся к записи и удаляются из модуля «Запчасти»
                currentState.originalPartIds.filterNot { addedPartIds.contains(it) }.forEach { partId ->
                    partRepository.getPartById(partId).firstOrNull()?.let { partRepository.deletePart(it) }
                }

                // Вложения, на которые после сохранения никто не ссылается (убрали позицию,
                // отдельный файл внутри неё или файл самого обслуживания), не оставляем
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(
                        touchedPhotos = currentState.touchedPhotos,
                        referencedPhotos = referencedEventPhotos(
                            eventPhotos = currentState.photosPaths,
                            parts = currentState.addedParts,
                            // В режиме общей суммы запчасти в БД не сохраняются, а прежние
                            // удалены выше — их фото больше никому не принадлежат
                            keepsParts = !currentState.useGeneralPartsCost
                        )
                    )
                )

                if (currentState.breakdownId != null && currentState.originalBreakdown != null) {
                    // Обновляем через copy: createdAt и поля, которых нет в форме, берутся
                    // из исходной записи. Раньше её собирали заново, и linkedConsumableIds
                    // обнулялись — после чего удаление ТО переставало удалять его расходники
                    val breakdown = currentState.originalBreakdown.copy(
                        carId = currentState.carId,
                        maintenanceType = currentState.maintenanceType?.name,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        // Расходники редактируются вместе с записью и больше не зависят от
                        // типа события, поэтому привязка всегда идёт из формы
                        linkedConsumableIds = linkedConsumableIds.ifEmpty { null },
                        isServiceMaintenance = currentState.isServiceMaintenance,
                        partsCost = partsCost,
                        workItems = workItems,
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        updatedAt = currentTime
                    )
                    breakdownRepository.updateBreakdown(breakdown)
                    // linkedMaintenanceId проставлен выше, при записи самих расходников:
                    // id события известен с самого начала
                } else {
                    val breakdown = Breakdown(
                        carId = currentState.carId,
                        maintenanceType = currentState.maintenanceType?.name,
                        title = currentState.title,
                        description = currentState.description,
                        breakdownDate = currentState.breakdownDate,
                        breakdownMileage = breakdownMileage,
                        brokenPartName = currentState.brokenPartName.ifBlank { null },
                        installedPartIds = if (addedPartIds.isNotEmpty()) addedPartIds else null,
                        linkedConsumableIds = if (linkedConsumableIds.isNotEmpty()) linkedConsumableIds else null,
                        isServiceMaintenance = currentState.isServiceMaintenance,
                        partsCost = partsCost,
                        workItems = workItems,
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        photosPaths = currentState.photosPaths.ifEmpty { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    val newBreakdownId = breakdownRepository.insertBreakdown(breakdown)

                    // Привязываем расходники к событию. От типа события это больше не зависит:
                    // с 1.2.4 тип выводится из содержимого и в форме остаётся «Ремонт», а
                    // непривязанный расходник выпадал из карточки события и считался в
                    // статистике «отдельным» — то есть вторым разом
                    linkedConsumableIds.forEach { consumableId ->
                        val consumable = consumableRepository.getConsumableById(consumableId).firstOrNull()
                        consumable?.let {
                            consumableRepository.updateConsumable(
                                it.copy(linkedMaintenanceId = newBreakdownId)
                            )
                        }
                    }
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(currentState.carId, breakdownMileage)

                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                
                // Снимок currentState сделан до сохранения: набранное за это время
                // он бы откатил, а поля ввода во время сохранения не блокируются
                _state.value = _state.value.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                // Сообщения у исключения может не быть — тогда экран просто замирал
                // без единого следа, и правка выглядела потерянной
                _state.value = currentState.copy(
                    isSaving = false,
                    error = e.message ?: "Не удалось сохранить запись"
                )
            }
        }
    }
}

/**
 * Часть стоимости запчастей, не разложенная по позициям, — для поля «одной суммой».
 * Нулевую разницу показываем пустым полем, иначе у обычного ТО в нём висел бы ноль.
 */
private fun formatResidualPartsCost(residual: Double): String =
    if (kotlin.math.abs(residual) < 0.005) "" else residual.toString()
