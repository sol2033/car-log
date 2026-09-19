package com.carlog.presentation.screens.fines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.FineRepository
import com.carlog.domain.model.Fine
import com.carlog.domain.model.FineViolationTypes
import com.carlog.presentation.components.orphanPhotosToDelete
import com.carlog.util.FileHelper
import com.carlog.util.FineStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Форма добавления/редактирования штрафа.
 *
 * Режим определяется наличием `fineId` в аргументах навигации, как у заправок и расходов.
 */
@HiltViewModel
class AddFineViewModel @Inject constructor(
    private val fineRepository: FineRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val fineId: Long? = savedStateHandle.get<String>("fineId")?.toLongOrNull()

    val isEditMode: Boolean get() = fineId != null

    /** Исходная запись: правка идёт через `original.copy(...)` (правило 1 из CLAUDE.md) */
    private val _original = MutableStateFlow<Fine?>(null)

    // === Поля формы ===

    private val _date = MutableStateFlow(System.currentTimeMillis())
    val date: StateFlow<Long> = _date.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _violationType = MutableStateFlow(FineViolationTypes.SPEEDING)
    val violationType: StateFlow<String> = _violationType.asStateFlow()

    private val _customViolation = MutableStateFlow("")
    val customViolation: StateFlow<String> = _customViolation.asStateFlow()

    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location.asStateFlow()

    private val _decisionNumber = MutableStateFlow("")
    val decisionNumber: StateFlow<String> = _decisionNumber.asStateFlow()

    private val _issuer = MutableStateFlow("")
    val issuer: StateFlow<String> = _issuer.asStateFlow()

    private val _discountUntil = MutableStateFlow<Long?>(
        FineStatus.defaultDiscountUntil(System.currentTimeMillis())
    )
    val discountUntil: StateFlow<Long?> = _discountUntil.asStateFlow()

    private val _isPaid = MutableStateFlow(false)
    val isPaid: StateFlow<Boolean> = _isPaid.asStateFlow()

    private val _paidDate = MutableStateFlow<Long?>(null)
    val paidDate: StateFlow<Long?> = _paidDate.asStateFlow()

    private val _paidAmount = MutableStateFlow("")
    val paidAmount: StateFlow<String> = _paidAmount.asStateFlow()

    private val _photosPaths = MutableStateFlow<List<String>>(emptyList())
    val photosPaths: StateFlow<List<String>> = _photosPaths.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    /**
     * Все вложения, выбранные в этот заход: файл копируется в хранилище сразу при выборе,
     * поэтому уход без сохранения обязан их подчистить (см. [orphanPhotosToDelete]).
     */
    private val touchedPhotos = mutableSetOf<String>()

    // === Валидация ===

    private val _amountError = MutableStateFlow<String?>(null)
    val amountError: StateFlow<String?> = _amountError.asStateFlow()

    private val _customViolationError = MutableStateFlow<String?>(null)
    val customViolationError: StateFlow<String?> = _customViolationError.asStateFlow()

    private val _paidAmountError = MutableStateFlow<String?>(null)
    val paidAmountError: StateFlow<String?> = _paidAmountError.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        if (fineId != null) loadFine(fineId)
    }

    private fun loadFine(id: Long) {
        viewModelScope.launch {
            fineRepository.getFineById(id).firstOrNull()?.let { loaded ->
                _original.value = loaded
                _date.value = loaded.date
                _amount.value = loaded.amount.toString()
                _violationType.value = loaded.violationType
                _customViolation.value = loaded.customViolation ?: ""
                _location.value = loaded.location ?: ""
                _decisionNumber.value = loaded.decisionNumber ?: ""
                _issuer.value = loaded.issuer ?: ""
                _discountUntil.value = loaded.discountUntil
                _isPaid.value = loaded.isPaid
                _paidDate.value = loaded.paidDate
                _paidAmount.value = loaded.paidAmount?.toString() ?: ""
                _photosPaths.value = loaded.photosPaths ?: emptyList()
                _notes.value = loaded.notes ?: ""
            }
        }
    }

    // === Обновление полей ===

    /**
     * Дата постановления тянет за собой льготный срок: пока пользователь его не трогал руками,
     * держим стандартные 30 дней от постановления, иначе введённую дату не перетираем.
     */
    fun updateDate(newDate: Long) {
        val wasDefault = _discountUntil.value == FineStatus.defaultDiscountUntil(_date.value)
        _date.value = newDate
        if (wasDefault) _discountUntil.value = FineStatus.defaultDiscountUntil(newDate)
    }

    fun updateAmount(value: String) {
        _amount.value = value
        _amountError.value = null
    }

    fun updateViolationType(type: String) {
        _violationType.value = type
        if (type != FineViolationTypes.OTHER) {
            _customViolation.value = ""
            _customViolationError.value = null
        }
    }

    fun updateCustomViolation(value: String) {
        _customViolation.value = value
        _customViolationError.value = null
    }

    fun updateLocation(value: String) {
        _location.value = value
    }

    fun updateDecisionNumber(value: String) {
        _decisionNumber.value = value
    }

    fun updateIssuer(value: String) {
        _issuer.value = value
    }

    fun updateDiscountUntil(value: Long?) {
        _discountUntil.value = value
    }

    /** Отметка об оплате: дату проставляем сегодняшней, чтобы не заставлять её выбирать */
    fun updateIsPaid(paid: Boolean) {
        _isPaid.value = paid
        if (paid) {
            if (_paidDate.value == null) _paidDate.value = System.currentTimeMillis()
        } else {
            _paidDate.value = null
            _paidAmount.value = ""
            _paidAmountError.value = null
        }
    }

    fun updatePaidDate(value: Long) {
        _paidDate.value = value
    }

    fun updatePaidAmount(value: String) {
        _paidAmount.value = value
        _paidAmountError.value = null
    }

    fun addPhoto(path: String) {
        touchedPhotos.add(path)
        _photosPaths.value = _photosPaths.value + path
    }

    fun removePhoto(path: String) {
        _photosPaths.value = _photosPaths.value - path
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun dismissSaveError() {
        _saveError.value = null
    }

    // === Сохранение ===

    fun saveFine() {
        viewModelScope.launch {
            try {
                _saveError.value = null

                // Запись ещё не догрузилась — вставка создала бы дубликат (правило 1)
                val original = _original.value
                if (isEditMode && original == null) return@launch

                var hasError = false

                val amountValue = _amount.value.toDoubleOrNull()
                if (_amount.value.isBlank()) {
                    _amountError.value = "Обязательное поле"
                    hasError = true
                } else if (amountValue == null || amountValue < 0) {
                    _amountError.value = "Введите корректную сумму"
                    hasError = true
                }

                if (_violationType.value == FineViolationTypes.OTHER && _customViolation.value.isBlank()) {
                    _customViolationError.value = "Укажите нарушение"
                    hasError = true
                }

                val paidAmountValue = _paidAmount.value.toDoubleOrNull()
                if (_paidAmount.value.isNotBlank() && paidAmountValue == null) {
                    _paidAmountError.value = "Введите корректную сумму"
                    hasError = true
                }

                if (hasError) return@launch

                val now = System.currentTimeMillis()
                val photos = _photosPaths.value.ifEmpty { null }

                if (original == null) {
                    fineRepository.insertFine(
                        Fine(
                            carId = carId,
                            date = _date.value,
                            amount = amountValue!!,
                            violationType = _violationType.value,
                            customViolation = _customViolation.value.ifBlank { null },
                            location = _location.value.ifBlank { null },
                            decisionNumber = _decisionNumber.value.ifBlank { null },
                            issuer = _issuer.value.ifBlank { null },
                            discountUntil = _discountUntil.value,
                            isPaid = _isPaid.value,
                            paidDate = if (_isPaid.value) _paidDate.value else null,
                            paidAmount = if (_isPaid.value) paidAmountValue else null,
                            photosPaths = photos,
                            notes = _notes.value.ifBlank { null },
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    // copy, а не сборка заново: createdAt и всё, чего нет в форме, остаётся на месте
                    fineRepository.updateFine(
                        original.copy(
                            date = _date.value,
                            amount = amountValue!!,
                            violationType = _violationType.value,
                            customViolation = _customViolation.value.ifBlank { null },
                            location = _location.value.ifBlank { null },
                            decisionNumber = _decisionNumber.value.ifBlank { null },
                            issuer = _issuer.value.ifBlank { null },
                            discountUntil = _discountUntil.value,
                            isPaid = _isPaid.value,
                            paidDate = if (_isPaid.value) _paidDate.value else null,
                            paidAmount = if (_isPaid.value) paidAmountValue else null,
                            photosPaths = photos,
                            notes = _notes.value.ifBlank { null },
                            updatedAt = now
                        )
                    )
                }

                // Вложения, выбранные и тут же убранные, в записи не останутся — удаляем файлы
                FileHelper.deleteFiles(
                    orphanPhotosToDelete(touchedPhotos, _photosPaths.value.toSet())
                )

                dataChangeNotifier.notifyDataChanged()
                _saveSuccess.value = true
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Не удалось сохранить штраф"
            }
        }
    }

    override fun onCleared() {
        // Ушли без сохранения — всё выбранное в этот заход осиротело
        if (!_saveSuccess.value) {
            FileHelper.deleteFiles(
                orphanPhotosToDelete(touchedPhotos, _original.value?.photosPaths.orEmpty().toSet())
            )
        }
        super.onCleared()
    }
}
