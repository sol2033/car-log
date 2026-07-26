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
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.MaintenanceType
import com.carlog.domain.model.Part
import com.carlog.presentation.components.EventPart
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// Временная структура для расходника до сохранения в БД
data class TemporaryConsumable(
    val category: String,
    val manufacturer: String?,
    val articleNumber: String?,
    val cost: Double?,
    val isInstalledAtService: Boolean,
    val serviceCost: Double?,
    val volume: Double?,
    val replacementIntervalMileage: Int?,
    val replacementIntervalDays: Int?,
    val notes: String?
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
    val serviceName: String = "",
    val serviceAddress: String = "",
    val notes: String = "",
    // Исходная запись при редактировании: привязки к запчастям/расходникам и createdAt
    // в форме не редактируются и берутся отсюда
    val originalBreakdown: Breakdown? = null,
    // Запчасти, привязанные к записи на момент загрузки
    val originalPartIds: List<Long> = emptyList(),
    // Фото запчастей, на которые ссылались сохранённые записи
    val originalPartPhotos: Set<String> = emptySet(),
    // Все фото, прошедшие через экран (для чистки файлов-сирот)
    val touchedPartPhotos: Set<String> = emptySet(),

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
    val calculatedPartsCost: Double
        get() = temporaryConsumables.sumOf { it.cost ?: 0.0 }
    
    val isEditMode: Boolean
        get() = breakdownId != null
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
                    // При редактировании ТО расходники уже в БД, не загружаем их в temporaryConsumables
                    // (редактирование расходников в существующем ТО запрещено)

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
                        partsCost = breakdown.partsCost.toString(),
                        useGeneralPartsCost = breakdown.installedPartIds.isNullOrEmpty(),
                        addedParts = linkedParts.map { part ->
                            EventPart(
                                name = part.name,
                                manufacturer = part.manufacturer ?: "",
                                partNumber = part.partNumber ?: "",
                                price = part.price,
                                notes = part.notes ?: "",
                                photosPaths = part.photosPaths ?: emptyList(),
                                partId = part.id
                            )
                        },
                        originalPartIds = linkedParts.map { it.id },
                        originalPartPhotos = linkedParts.flatMap { it.photosPaths ?: emptyList() }.toSet(),
                        touchedPartPhotos = linkedParts.flatMap { it.photosPaths ?: emptyList() }.toSet(),
                        originalBreakdown = breakdown,
                        serviceCost = breakdown.serviceCost?.toString() ?: "",
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
        if (existingCategories.contains(consumable.category)) {
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
        if (otherCategories.contains(consumable.category)) {
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
            touchedPartPhotos = _state.value.touchedPartPhotos + part.photosPaths
        )
    }

    fun updatePart(index: Int, part: EventPart) {
        val parts = _state.value.addedParts
        if (index !in parts.indices) return
        _state.value = _state.value.copy(
            addedParts = parts.toMutableList().apply { set(index, part) },
            touchedPartPhotos = _state.value.touchedPartPhotos + part.photosPaths
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

    /** Фото убрали в диалоге — запоминаем, чтобы удалить файл вместе с остальными сиротами */
    fun onPartPhotoDiscarded(path: String) {
        _state.value = _state.value.copy(
            touchedPartPhotos = _state.value.touchedPartPhotos + path
        )
    }

    override fun onCleared() {
        // Ушли, не сохранив: выбранные в этот заход файлы никому не принадлежат
        val current = _state.value
        if (!current.isSaved) {
            FileHelper.deleteFiles(
                orphanPhotosToDelete(current.touchedPartPhotos, current.originalPartPhotos)
            )
        }
        super.onCleared()
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun saveBreakdown() {
        val currentState = _state.value

        // Запись ещё не догрузилась — сохранять нельзя: вставка создала бы дубликат
        if (currentState.breakdownId != null && currentState.originalBreakdown == null) return

        val maintenanceTypeError = if (currentState.maintenanceType == null) "Выберите тип обслуживания" else null
        val titleError = if (currentState.title.isBlank()) "Обязательное поле" else null
        val descriptionError = if (currentState.description.isBlank()) "Обязательное поле" else null
        val breakdownMileageError = if (currentState.breakdownMileage.isBlank()) "Обязательное поле" else null

        // Для нового ТО обязателен минимум 1 расходник.
        // При редактировании расходники уже в БД и в форму не загружаются (менять их
        // внутри готового ТО нельзя) — раньше проверка срабатывала и здесь, из-за чего
        // существующее ТО невозможно было сохранить вообще: кнопка добавления отключена
        val consumablesError = if (!currentState.isEditMode &&
            currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE &&
            currentState.temporaryConsumables.isEmpty()
        ) {
            "Для ТО необходимо добавить минимум 1 расходник"
        } else null
        
        val partsCostError = if (currentState.maintenanceType != MaintenanceType.SCHEDULED_SERVICE) {
            if (currentState.useGeneralPartsCost) {
                if (currentState.partsCost.isBlank()) "Обязательное поле" else null
            } else {
                if (currentState.addedParts.isEmpty()) "Добавьте хотя бы одну запчасть" else null
            }
        } else null
        
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
                val breakdownMileage = currentState.breakdownMileage.toInt()
                
                // Для ТО стоимость запчастей рассчитывается из расходников.
                // При редактировании расходники не пересоздаются и в форму не загружаются,
                // поэтому берём сохранённую сумму — иначе она обнулялась бы (сумма по пустому списку)
                val partsCost = if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                    if (currentState.isEditMode) {
                        currentState.originalBreakdown?.partsCost ?: currentState.calculatedPartsCost
                    } else {
                        currentState.calculatedPartsCost
                    }
                } else if (currentState.useGeneralPartsCost) {
                    currentState.partsCost.toDouble()
                } else {
                    currentState.addedParts.sumOf { it.price }
                }
                
                val serviceCost = currentState.serviceCost.toDoubleOrNull()
                val totalCost = partsCost + (serviceCost ?: 0.0)
                
                // Создаем расходники в БД ТОЛЬКО для нового ТО (не при редактировании)
                val linkedConsumableIds = mutableListOf<Long>()
                if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE && 
                    currentState.breakdownId == null) { // Только при создании нового ТО
                    
                    // Перед созданием новых расходников, деактивируем старые в тех же категориях
                    for (tempConsumable in currentState.temporaryConsumables) {
                        // Находим расходники в этой категории
                        val consumablesInCategory = consumableRepository.getConsumablesByCategory(
                            currentState.carId, 
                            tempConsumable.category
                        ).firstOrNull() ?: emptyList()
                        
                        // Деактивируем активный расходник этой категории
                        val activeConsumable = consumablesInCategory.firstOrNull { it.isActive }
                        activeConsumable?.let { existing ->
                            consumableRepository.updateConsumable(
                                existing.copy(
                                    isActive = false,
                                    replacementDate = currentState.breakdownDate,
                                    replacementMileage = breakdownMileage,
                                    updatedAt = currentTime
                                )
                            )
                        }
                    }
                    
                    // Теперь создаем новые расходники
                    for (tempConsumable in currentState.temporaryConsumables) {
                        val consumable = tempConsumable.toConsumable(
                            carId = currentState.carId,
                            installationMileage = breakdownMileage,
                            installationDate = currentState.breakdownDate,
                            linkedMaintenanceId = null // Будет установлен после создания breakdown
                        )
                        val consumableId = consumableRepository.insertConsumable(consumable)
                        linkedConsumableIds.add(consumableId)
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
                                    maintenanceType = currentState.maintenanceType?.name,
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
                                maintenanceType = currentState.maintenanceType?.name,
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

                // Фото, на которые после сохранения никто не ссылается (убрали позицию или
                // отдельное фото внутри неё), в хранилище не оставляем
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(
                        touchedPhotos = currentState.touchedPartPhotos,
                        referencedPhotos = currentState.addedParts.flatMap { it.photosPaths }.toSet()
                    )
                )

                if (currentState.breakdownId != null && currentState.originalBreakdown != null) {
                    // Обновляем через copy: привязка к расходникам и createdAt в форме
                    // не редактируются. Раньше запись собиралась заново, и linkedConsumableIds
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
                        isServiceMaintenance = currentState.isServiceMaintenance,
                        partsCost = partsCost,
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        notes = currentState.notes.ifBlank { null },
                        updatedAt = currentTime
                    )
                    breakdownRepository.updateBreakdown(breakdown)
                    
                    // Обновляем linkedMaintenanceId для новых расходников
                    if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                        linkedConsumableIds.forEach { consumableId ->
                            val consumable = consumableRepository.getConsumableById(consumableId).firstOrNull()
                            consumable?.let {
                                consumableRepository.updateConsumable(
                                    it.copy(linkedMaintenanceId = currentState.breakdownId)
                                )
                            }
                        }
                    }
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
                        serviceCost = serviceCost,
                        totalCost = totalCost,
                        isWarrantyRepair = currentState.isWarrantyRepair,
                        serviceName = currentState.serviceName.ifBlank { null },
                        serviceAddress = currentState.serviceAddress.ifBlank { null },
                        notes = currentState.notes.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                    val newBreakdownId = breakdownRepository.insertBreakdown(breakdown)
                    
                    // Обновляем linkedMaintenanceId для расходников
                    if (currentState.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                        linkedConsumableIds.forEach { consumableId ->
                            val consumable = consumableRepository.getConsumableById(consumableId).firstOrNull()
                            consumable?.let {
                                consumableRepository.updateConsumable(
                                    it.copy(linkedMaintenanceId = newBreakdownId)
                                )
                            }
                        }
                    }
                }
                
                // Обновляем пробег автомобиля до максимального
                carRepository.updateCarMileageIfNeeded(currentState.carId, currentState.breakdownMileage.toInt())

                // Уведомляем об изменении данных для авто-бэкапа
                dataChangeNotifier.notifyDataChanged()
                
                _state.value = currentState.copy(
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }
}
