package com.carlog.presentation.screens.accidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.AccidentRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Accident
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

data class AddAccidentState(
    val carId: Long = 0,
    val accidentId: Long? = null,
    
    val date: Long = System.currentTimeMillis(),
    val mileage: String = "",
    val location: String = "",
    val damageDescription: String = "",
    val severity: String = "Средняя",
    val isUserAtFault: Boolean = false,
    
    // Выплаты
    val hasOsagoPayout: Boolean = false,
    val osagoPayout: String = "",
    val hasKaskoPayout: Boolean = false,
    val kaskoPayout: String = "",
    val hasCulpritPayout: Boolean = false,
    val culpritPayout: String = "",
    
    // Ремонт
    val usePartsForRepair: Boolean = true, // true = добавлять запчасти, false = общая стоимость
    val addedParts: List<EventPart> = emptyList(),
    val serviceCost: String = "",
    val totalRepairCost: String = "",
    
    // Медиа
    val photosPaths: List<String> = emptyList(),
    val documentPath: String? = null,
    // PDF, на который ссылается запись в БД (для чистки заменённых/несохранённых файлов)
    val originalDocumentPath: String? = null,

    val notes: String = "",

    // Сохраняется при редактировании, чтобы не затирать исходное время создания записи
    val createdAt: Long = 0L,
    // Запчасти, привязанные к записи на момент загрузки: удалённые из списка нужно
    // удалить и из модуля «Запчасти»
    val originalPartIds: List<Long> = emptyList(),
    // Фото запчастей: исходные и все прошедшие через экран (для чистки файлов-сирот)
    val originalPartPhotos: Set<String> = emptySet(),
    val touchedPartPhotos: Set<String> = emptySet(),
    // Загружено ли редактируемое ДТП (до загрузки сохранять нельзя — будет дубликат)
    val isLoaded: Boolean = false,

    // Validation errors
    val mileageError: String? = null,
    val damageDescriptionError: String? = null,
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddAccidentViewModel @Inject constructor(
    private val accidentRepository: AccidentRepository,
    private val partRepository: PartRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddAccidentState())
    val state: StateFlow<AddAccidentState> = _state.asStateFlow()
    
    init {
        val carId = savedStateHandle.get<Long>("carId") ?: 0L
        val accidentId = savedStateHandle.get<Long>("accidentId")
        
        _state.value = _state.value.copy(carId = carId)
        
        // Пробег машины подставляем только для новой записи: иначе асинхронная подстановка
        // может выиграть гонку у загрузки и затереть пробег существующего ДТП
        if (accidentId != null && accidentId != -1L) {
            loadAccident(accidentId)
        } else {
            viewModelScope.launch {
                val car = carRepository.getCarById(carId).firstOrNull()
                car?.let {
                    _state.value = _state.value.copy(mileage = it.currentMileage.toString())
                }
            }
        }
    }
    
    private fun loadAccident(accidentId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val accident = accidentRepository.getAccidentById(accidentId).firstOrNull()
                if (accident != null) {
                    // Восстанавливаем список запчастей: раньше он не загружался, и сохранение
                    // отредактированного ДТП обнуляло стоимость ремонта и отвязывало запчасти
                    val linkedParts = accident.installedPartIds.orEmpty().mapNotNull { partId ->
                        partRepository.getPartById(partId).firstOrNull()
                    }
                    val addedParts = linkedParts.map { part ->
                        EventPart(
                            name = part.name,
                            manufacturer = part.manufacturer ?: "",
                            partNumber = part.partNumber ?: "",
                            price = part.price,
                            notes = part.notes ?: "",
                            photosPaths = part.photosPaths ?: emptyList(),
                            partId = part.id
                        )
                    }
                    val linkedPartPhotos = linkedParts.flatMap { it.photosPaths ?: emptyList() }.toSet()
                    // Стоимость работ отдельно не хранится — она входит в repairCost
                    // вместе с запчастями, поэтому выделяем её обратно вычитанием
                    val partsSum = addedParts.sumOf { it.price }
                    val serviceCost = accident.repairCost?.minus(partsSum)?.takeIf { it > 0.0 }

                    _state.value = AddAccidentState(
                        carId = accident.carId,
                        accidentId = accident.id,
                        date = accident.date,
                        mileage = accident.mileage.toString(),
                        location = accident.location ?: "",
                        damageDescription = accident.damageDescription,
                        severity = accident.severity,
                        isUserAtFault = accident.isUserAtFault,
                        hasOsagoPayout = accident.osagoPayout != null,
                        osagoPayout = accident.osagoPayout?.toString() ?: "",
                        hasKaskoPayout = accident.kaskoPayout != null,
                        kaskoPayout = accident.kaskoPayout?.toString() ?: "",
                        hasCulpritPayout = accident.culpritPayout != null,
                        culpritPayout = accident.culpritPayout?.toString() ?: "",
                        usePartsForRepair = accident.installedPartIds != null,
                        addedParts = addedParts,
                        serviceCost = serviceCost?.toString() ?: "",
                        originalPartIds = linkedParts.map { it.id },
                        originalPartPhotos = linkedPartPhotos,
                        touchedPartPhotos = linkedPartPhotos,
                        totalRepairCost = accident.repairCost?.toString() ?: "",
                        photosPaths = accident.photosPaths ?: emptyList(),
                        documentPath = accident.documentPath,
                        originalDocumentPath = accident.documentPath,
                        notes = accident.notes ?: "",
                        createdAt = accident.createdAt,
                        isLoaded = true,
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ДТП не найдено"
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
    
    fun updateDate(date: Long) {
        _state.value = _state.value.copy(date = date)
    }
    
    fun updateMileage(mileage: String) {
        _state.value = _state.value.copy(
            mileage = mileage,
            mileageError = if (mileage.isNotBlank()) null else _state.value.mileageError
        )
    }
    
    fun updateLocation(location: String) {
        _state.value = _state.value.copy(location = location)
    }
    
    fun updateDamageDescription(description: String) {
        _state.value = _state.value.copy(
            damageDescription = description,
            damageDescriptionError = if (description.isNotBlank()) null else _state.value.damageDescriptionError
        )
    }
    
    fun updateSeverity(severity: String) {
        _state.value = _state.value.copy(severity = severity)
    }
    
    fun toggleUserAtFault(isAtFault: Boolean) {
        val currentState = _state.value
        _state.value = currentState.copy(
            isUserAtFault = isAtFault,
            // Если пользователь виновник, убираем ОСАГО и выплату от виновника
            hasOsagoPayout = if (isAtFault) false else currentState.hasOsagoPayout,
            osagoPayout = if (isAtFault) "" else currentState.osagoPayout,
            hasCulpritPayout = if (isAtFault) false else currentState.hasCulpritPayout,
            culpritPayout = if (isAtFault) "" else currentState.culpritPayout
        )
    }
    
    fun toggleOsagoPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasOsagoPayout = has,
            osagoPayout = if (!has) "" else _state.value.osagoPayout
        )
    }
    
    fun updateOsagoPayout(amount: String) {
        _state.value = _state.value.copy(osagoPayout = amount)
    }
    
    fun toggleKaskoPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasKaskoPayout = has,
            kaskoPayout = if (!has) "" else _state.value.kaskoPayout
        )
    }
    
    fun updateKaskoPayout(amount: String) {
        _state.value = _state.value.copy(kaskoPayout = amount)
    }
    
    fun toggleCulpritPayout(has: Boolean) {
        _state.value = _state.value.copy(
            hasCulpritPayout = has,
            culpritPayout = if (!has) "" else _state.value.culpritPayout
        )
    }
    
    fun updateCulpritPayout(amount: String) {
        _state.value = _state.value.copy(culpritPayout = amount)
    }
    
    fun toggleRepairMethod(usePartsForRepair: Boolean) {
        _state.value = _state.value.copy(usePartsForRepair = usePartsForRepair)
    }
    
    fun addPart(part: EventPart) {
        val currentState = _state.value
        _state.value = currentState.copy(
            addedParts = currentState.addedParts + part,
            touchedPartPhotos = currentState.touchedPartPhotos + part.photosPaths
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
    
    fun updateServiceCost(cost: String) {
        _state.value = _state.value.copy(serviceCost = cost)
    }
    
    fun updateTotalRepairCost(cost: String) {
        _state.value = _state.value.copy(totalRepairCost = cost)
    }
    
    fun addPhoto(photoPath: String) {
        val currentState = _state.value
        _state.value = currentState.copy(
            photosPaths = currentState.photosPaths + photoPath
        )
    }
    
    fun removePhoto(photoPath: String) {
        val currentState = _state.value
        _state.value = currentState.copy(
            photosPaths = currentState.photosPaths - photoPath
        )
    }
    
    fun updateDocumentPath(path: String?) {
        // Пользователь выбрал PDF повторно — предыдущий выбранный файл больше никому не нужен
        // (файл, на который ссылается запись в БД, не трогаем до сохранения)
        val previous = _state.value.documentPath
        if (previous != null && previous != path &&
            previous != _state.value.originalDocumentPath && isLocalFile(previous)
        ) {
            FileHelper.deleteFile(previous)
        }
        _state.value = _state.value.copy(documentPath = path)
    }

    /** Старые записи хранят сырой content:// URI — такие файлы нам не принадлежат */
    private fun isLocalFile(path: String) = !path.startsWith("content://")

    override fun onCleared() {
        // Пользователь выбрал PDF (файл уже скопирован в хранилище), но ушёл без сохранения —
        // чистим файл-сироту. Файл, на который ссылается запись в БД, не трогаем.
        val currentState = _state.value
        if (!currentState.isSaved) {
            val current = currentState.documentPath
            if (current != null && current != currentState.originalDocumentPath && isLocalFile(current)) {
                FileHelper.deleteFile(current)
            }
            // Фото запчастей, выбранные в этот заход, тоже никому не принадлежат
            FileHelper.deleteFiles(
                orphanPhotosToDelete(
                    touchedPhotos = currentState.touchedPartPhotos,
                    referencedPhotos = currentState.originalPartPhotos
                )
            )
        }
        super.onCleared()
    }
    
    fun updateNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }
    
    fun saveAccident() {
        val currentState = _state.value

        // Запись ещё не догрузилась — сохранять нельзя: вставка создала бы дубликат
        if (currentState.accidentId != null && !currentState.isLoaded) return

        // Validation
        val mileageError = if (currentState.mileage.isBlank()) "Обязательное поле" else null
        val damageDescriptionError = if (currentState.damageDescription.isBlank()) "Обязательное поле" else null
        
        if (mileageError != null || damageDescriptionError != null) {
            _state.value = currentState.copy(
                mileageError = mileageError,
                damageDescriptionError = damageDescriptionError
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _state.value = currentState.copy(isSaving = true, error = null)
                
                val currentTime = System.currentTimeMillis()
                
                // Рассчитываем стоимость ремонта
                val repairCost = if (currentState.usePartsForRepair) {
                    val partsCost = currentState.addedParts.sumOf { it.price }
                    val serviceCostValue = currentState.serviceCost.toDoubleOrNull() ?: 0.0
                    partsCost + serviceCostValue
                } else {
                    currentState.totalRepairCost.toDoubleOrNull()
                }
                
                // Создаем или обновляем запчасти.
                // Уже сохранённые (с partId) обновляются, а не вставляются заново —
                // иначе каждое редактирование плодило бы дубликаты в модуле «Запчасти»
                val installedPartIds = if (currentState.usePartsForRepair && currentState.addedParts.isNotEmpty()) {
                    currentState.addedParts.map { addedPart ->
                        val existing = addedPart.partId?.let { partRepository.getPartById(it).firstOrNull() }
                        if (existing != null) {
                            partRepository.updatePart(
                                existing.copy(
                                    name = addedPart.name,
                                    manufacturer = addedPart.manufacturer.ifBlank { null },
                                    partNumber = addedPart.partNumber.ifBlank { null },
                                    installDate = currentState.date,
                                    installMileage = currentState.mileage.toInt(),
                                    price = addedPart.price,
                                    photosPaths = addedPart.photosPaths.ifEmpty { null },
                                    notes = addedPart.notes.ifBlank {
                                        "Установлена после ДТП ${formatDate(currentState.date)}"
                                    },
                                    updatedAt = currentTime
                                )
                            )
                            existing.id
                        } else {
                            partRepository.insertPart(
                                Part(
                                    carId = currentState.carId,
                                    name = addedPart.name,
                                    manufacturer = addedPart.manufacturer.ifBlank { null },
                                    partNumber = addedPart.partNumber.ifBlank { null },
                                    installDate = currentState.date,
                                    installMileage = currentState.mileage.toInt(),
                                    installationType = "ДТП",
                                    price = addedPart.price,
                                    servicePrice = null,
                                    photosPaths = addedPart.photosPaths.ifEmpty { null },
                                    notes = addedPart.notes.ifBlank {
                                        "Установлена после ДТП ${formatDate(currentState.date)}"
                                    },
                                    createdAt = currentTime,
                                    updatedAt = currentTime
                                )
                            )
                        }
                    }
                } else null

                // Запчасти, убранные из списка (или весь список — при переходе на общую сумму),
                // больше не относятся к ДТП и удаляются из модуля «Запчасти»
                val keptPartIds = installedPartIds?.toSet() ?: emptySet()
                currentState.originalPartIds.filterNot { keptPartIds.contains(it) }.forEach { partId ->
                    partRepository.getPartById(partId).firstOrNull()?.let { partRepository.deletePart(it) }
                }

                // Фото, на которые после сохранения никто не ссылается
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(
                        touchedPhotos = currentState.touchedPartPhotos,
                        referencedPhotos = currentState.addedParts.flatMap { it.photosPaths }.toSet()
                    )
                )

                val accident = Accident(
                    id = currentState.accidentId ?: 0,
                    carId = currentState.carId,
                    date = currentState.date,
                    mileage = currentState.mileage.toInt(),
                    location = currentState.location.ifBlank { null },
                    damageDescription = currentState.damageDescription,
                    severity = currentState.severity,
                    isUserAtFault = currentState.isUserAtFault,
                    osagoPayout = if (currentState.hasOsagoPayout) currentState.osagoPayout.toDoubleOrNull() else null,
                    kaskoPayout = if (currentState.hasKaskoPayout) currentState.kaskoPayout.toDoubleOrNull() else null,
                    culpritPayout = if (currentState.hasCulpritPayout) currentState.culpritPayout.toDoubleOrNull() else null,
                    installedPartIds = installedPartIds,
                    repairCost = repairCost,
                    photosPaths = currentState.photosPaths.ifEmpty { null },
                    documentPath = currentState.documentPath,
                    notes = currentState.notes.ifBlank { null },
                    createdAt = if (currentState.accidentId != null) currentState.createdAt else currentTime,
                    updatedAt = currentTime
                )
                
                if (currentState.accidentId != null) {
                    accidentRepository.updateAccident(accident)
                } else {
                    accidentRepository.insertAccident(accident)
                }
                
                carRepository.updateCarMileageIfNeeded(currentState.carId, currentState.mileage.toInt())

                // PDF заменили — файл, на который ссылалась старая версия записи, больше не нужен
                val originalDocument = currentState.originalDocumentPath
                if (originalDocument != null && originalDocument != currentState.documentPath &&
                    isLocalFile(originalDocument)
                ) {
                    FileHelper.deleteFile(originalDocument)
                }

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
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
