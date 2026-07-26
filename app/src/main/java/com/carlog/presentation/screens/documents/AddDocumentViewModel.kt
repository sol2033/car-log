package com.carlog.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarDocumentRepository
import com.carlog.domain.model.CarDocument
import com.carlog.domain.model.DocumentTypes
import com.carlog.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для добавления/редактирования/продления документа.
 *
 * Режимы (по аргументам навигации):
 * - documentId == null, renewFromId == null — создание нового;
 * - documentId != null — редактирование существующего;
 * - renewFromId != null — продление: форма предзаполняется старым документом,
 *   при сохранении старый деактивируется, создаётся новый активный.
 */
@HiltViewModel
class AddDocumentViewModel @Inject constructor(
    private val documentRepository: CarDocumentRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val documentId: Long? = savedStateHandle.get<String>("documentId")?.toLongOrNull()
    private val renewFromId: Long? = savedStateHandle.get<String>("renewFromId")?.toLongOrNull()
    private val prefilledType: String? = savedStateHandle.get<String>("type")

    val isEditMode: Boolean get() = documentId != null
    val isRenewMode: Boolean get() = renewFromId != null

    private val _document = MutableStateFlow<CarDocument?>(null)

    /** Документ, который продлеваем (для деактивации при сохранении) */
    private var renewSource: CarDocument? = null

    // === Поля формы ===

    private val _type = MutableStateFlow(prefilledType ?: DocumentTypes.OSAGO)
    val type: StateFlow<String> = _type.asStateFlow()

    private val _customName = MutableStateFlow("")
    val customName: StateFlow<String> = _customName.asStateFlow()

    private val _number = MutableStateFlow("")
    val number: StateFlow<String> = _number.asStateFlow()

    private val _organization = MutableStateFlow("")
    val organization: StateFlow<String> = _organization.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    val startDate: StateFlow<Long?> = _startDate.asStateFlow()

    private val _expiryDate = MutableStateFlow<Long?>(null)
    val expiryDate: StateFlow<Long?> = _expiryDate.asStateFlow()

    private val _cost = MutableStateFlow("")
    val cost: StateFlow<String> = _cost.asStateFlow()

    private val _photoPath = MutableStateFlow<String?>(null)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // === Валидация ===

    private val _customNameError = MutableStateFlow<String?>(null)
    val customNameError: StateFlow<String?> = _customNameError.asStateFlow()

    private val _expiryDateError = MutableStateFlow<String?>(null)
    val expiryDateError: StateFlow<String?> = _expiryDateError.asStateFlow()

    private val _costError = MutableStateFlow<String?>(null)
    val costError: StateFlow<String?> = _costError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        when {
            documentId != null -> loadDocument(documentId, forEdit = true)
            renewFromId != null -> loadDocument(renewFromId, forEdit = false)
        }
    }

    private fun loadDocument(id: Long, forEdit: Boolean) {
        viewModelScope.launch {
            documentRepository.getDocumentById(id).firstOrNull()?.let { loaded ->
                if (forEdit) {
                    _document.value = loaded
                } else {
                    renewSource = loaded
                }
                _type.value = loaded.type
                _customName.value = loaded.customName ?: ""
                _number.value = loaded.number ?: ""
                _organization.value = loaded.organization ?: ""
                val isTax = loaded.type == DocumentTypes.VEHICLE_TAX
                if (forEdit) {
                    _startDate.value = loaded.startDate
                    // Для налога в БД лежит дата СЛЕДУЮЩЕГО начисления,
                    // а форма оперирует датой последнего — конвертируем обратно
                    _expiryDate.value =
                        if (isTax) shiftYears(loaded.expiryDate, -1) else loaded.expiryDate
                    _cost.value = loaded.cost?.toString() ?: ""
                    _photoPath.value = loaded.photoPath
                    _notes.value = loaded.notes ?: ""
                } else {
                    // Продление: новый срок начинается там, где закончился старый
                    _startDate.value = loaded.expiryDate
                    if (isTax) {
                        // Хранимая дата следующего начисления и есть «последнее начисление» нового периода
                        _expiryDate.value = loaded.expiryDate
                    }
                }
            }
        }
    }

    // === Обновление полей ===

    fun updateType(newType: String) {
        _type.value = newType
        if (newType != DocumentTypes.OTHER) {
            _customName.value = ""
            _customNameError.value = null
        }
        // У налога нет номера и организации — чистим, чтобы скрытые поля не сохранились
        if (newType == DocumentTypes.VEHICLE_TAX) {
            _number.value = ""
            _organization.value = ""
        }
    }

    fun updateCustomName(name: String) {
        _customName.value = name
        _customNameError.value = null
    }

    fun updateNumber(newNumber: String) {
        _number.value = newNumber
    }

    fun updateOrganization(newOrganization: String) {
        _organization.value = newOrganization
    }

    fun updateStartDate(date: Long?) {
        _startDate.value = date
    }

    fun updateExpiryDate(date: Long) {
        _expiryDate.value = date
        _expiryDateError.value = null
    }

    fun updateCost(newCost: String) {
        _cost.value = newCost
        _costError.value = null
    }

    fun updatePhoto(path: String?) {
        // Пользователь выбрал фото повторно — предыдущий выбранный файл больше никому не нужен
        // (исходное фото документа не трогаем: на него ещё ссылается запись в БД)
        val previous = _photoPath.value
        if (previous != null && previous != path && previous != _document.value?.photoPath) {
            FileHelper.deleteFile(previous)
        }
        _photoPath.value = path
    }

    fun updateNotes(newNotes: String) {
        _notes.value = newNotes
    }

    // === Сохранение ===

    fun saveDocument() {
        viewModelScope.launch {
            var hasError = false

            if (_type.value == DocumentTypes.OTHER && _customName.value.isBlank()) {
                _customNameError.value = "Введите название"
                hasError = true
            }

            if (_expiryDate.value == null) {
                _expiryDateError.value = "Укажите дату окончания"
                hasError = true
            }

            val costValue = _cost.value.toDoubleOrNull()
            if (costValue == null && _cost.value.isNotBlank()) {
                _costError.value = "Введите корректное значение"
                hasError = true
            }

            if (hasError) return@launch

            // Для налога пользователь вводит дату ПОСЛЕДНЕГО начисления,
            // а храним дату следующего (+1 год) — светофор отсчитывает дни до него
            val isTax = _type.value == DocumentTypes.VEHICLE_TAX
            val expiryToStore =
                if (isTax) shiftYears(_expiryDate.value!!, 1) else _expiryDate.value!!

            val documentToSave = CarDocument(
                id = _document.value?.id ?: 0,
                carId = carId,
                type = _type.value,
                customName = _customName.value.ifBlank { null },
                number = _number.value.ifBlank { null },
                organization = _organization.value.ifBlank { null },
                startDate = _startDate.value,
                expiryDate = expiryToStore,
                cost = costValue,
                photoPath = _photoPath.value,
                notes = _notes.value.ifBlank { null },
                isActive = _document.value?.isActive ?: true,
                createdAt = _document.value?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            when {
                isEditMode -> {
                    // Смена типа при редактировании не должна давать два активных документа
                    // одного стандартного типа — существующий уходит в историю (как при продлении)
                    if (documentToSave.isActive && documentToSave.type != DocumentTypes.OTHER) {
                        val other = documentRepository.getActiveDocumentByType(carId, documentToSave.type)
                        if (other != null && other.id != documentToSave.id) {
                            documentRepository.deactivateDocument(other.id)
                        }
                    }
                    documentRepository.updateDocument(documentToSave)
                    // Фото заменили или убрали — старый файл больше не нужен
                    val oldPhoto = _document.value?.photoPath
                    if (oldPhoto != null && oldPhoto != documentToSave.photoPath) {
                        FileHelper.deleteFile(oldPhoto)
                    }
                }
                isRenewMode && renewSource != null ->
                    documentRepository.renewDocument(renewSource!!, documentToSave)
                else -> {
                    // Не даём завести второй активный документ того же стандартного типа:
                    // существующий уходит в историю, как при продлении
                    val existing = if (_type.value != DocumentTypes.OTHER) {
                        documentRepository.getActiveDocumentByType(carId, _type.value)
                    } else null
                    if (existing != null) {
                        documentRepository.renewDocument(existing, documentToSave)
                    } else {
                        documentRepository.insertDocument(documentToSave)
                    }
                }
            }

            // Уведомляем об изменении данных для резервного копирования
            dataChangeNotifier.notifyDataChanged()

            _saveSuccess.value = true
        }
    }

    /** Сдвигает дату на N лет (учитывая длину года по календарю) */
    private fun shiftYears(timestamp: Long, years: Int): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(java.util.Calendar.YEAR, years)
        }.timeInMillis
    }

    /** Удаляет только что выбранное фото из хранилища, если пользователь передумал */
    fun removePhoto() {
        _photoPath.value?.let { path ->
            // При редактировании не удаляем файл сразу — документ ещё ссылается на него
            if (_document.value?.photoPath != path) {
                FileHelper.deleteFile(path)
            }
        }
        _photoPath.value = null
    }

    override fun onCleared() {
        // Пользователь выбрал фото (файл уже скопирован в хранилище), но ушёл без сохранения —
        // чистим файл-сироту. Исходное фото документа не трогаем.
        if (!_saveSuccess.value) {
            val current = _photoPath.value
            if (current != null && current != _document.value?.photoPath) {
                FileHelper.deleteFile(current)
            }
        }
        super.onCleared()
    }
}
