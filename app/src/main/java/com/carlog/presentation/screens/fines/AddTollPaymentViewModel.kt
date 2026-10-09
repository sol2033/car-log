package com.carlog.presentation.screens.fines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.TollPaymentRepository
import com.carlog.domain.model.TollPayment
import com.carlog.domain.model.NameSuggestions
import com.carlog.util.toDecimalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Форма добавления/редактирования оплаты платной дороги.
 *
 * Режим определяется наличием `tollPaymentId` в аргументах навигации, как у штрафов.
 */
@HiltViewModel
class AddTollPaymentViewModel @Inject constructor(
    private val tollPaymentRepository: TollPaymentRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val paymentId: Long? = savedStateHandle.get<String>("tollPaymentId")?.toLongOrNull()

    val isEditMode: Boolean get() = paymentId != null

    /** Исходная запись: правка идёт через `original.copy(...)` (правило 1 из CLAUDE.md) */
    private val _original = MutableStateFlow<TollPayment?>(null)

    // === Поля формы ===

    private val _date = MutableStateFlow(System.currentTimeMillis())
    val date: StateFlow<Long> = _date.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _roadName = MutableStateFlow("")
    val roadName: StateFlow<String> = _roadName.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    /** Подсказки к полю дороги: из названий, введённых раньше (по всем машинам) */
    val roadSuggestions: StateFlow<List<String>> = combine(
        tollPaymentRepository.getRoadNames(),
        _roadName
    ) { known, input -> NameSuggestions.suggest(known, input) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === Валидация ===

    private val _amountError = MutableStateFlow<String?>(null)
    val amountError: StateFlow<String?> = _amountError.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /**
     * Идёт сохранение. Ставится синхронно, до запуска корутины: два быстрых тапа (галочка
     * в шапке и кнопка внизу) иначе оба проходили проверки и вставляли две записи.
     * После успеха не сбрасывается — экран вот-вот закроется.
     */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        if (paymentId != null) loadPayment(paymentId)
    }

    private fun loadPayment(id: Long) {
        viewModelScope.launch {
            tollPaymentRepository.getTollPaymentById(id).firstOrNull()?.let { loaded ->
                _original.value = loaded
                _date.value = loaded.date
                _amount.value = loaded.amount.toString()
                _roadName.value = loaded.roadName ?: ""
                _notes.value = loaded.notes ?: ""
            }
        }
    }

    // === Обновление полей ===

    fun updateDate(value: Long) {
        _date.value = value
    }

    fun updateAmount(value: String) {
        _amount.value = value
        _amountError.value = null
    }

    fun updateRoadName(value: String) {
        _roadName.value = value
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun dismissSaveError() {
        _saveError.value = null
    }

    // === Сохранение ===

    fun save() {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                _saveError.value = null

                // Запись ещё не догрузилась — вставка создала бы дубликат (правило 1)
                val original = _original.value
                if (isEditMode && original == null) return@launch

                val amountValue = _amount.value.toDecimalOrNull()
                if (_amount.value.isBlank()) {
                    _amountError.value = "Обязательное поле"
                    return@launch
                }
                if (amountValue == null || amountValue < 0) {
                    _amountError.value = "Введите корректную сумму"
                    return@launch
                }

                val now = System.currentTimeMillis()
                val road = _roadName.value.trim().ifBlank { null }
                val notes = _notes.value.ifBlank { null }

                if (original == null) {
                    tollPaymentRepository.insertTollPayment(
                        TollPayment(
                            carId = carId,
                            date = _date.value,
                            amount = amountValue,
                            roadName = road,
                            notes = notes,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    // copy, а не сборка заново: createdAt и всё, чего нет в форме, остаётся на месте
                    tollPaymentRepository.updateTollPayment(
                        original.copy(
                            date = _date.value,
                            amount = amountValue,
                            roadName = road,
                            notes = notes,
                            updatedAt = now
                        )
                    )
                }

                dataChangeNotifier.notifyDataChanged()
                _saveSuccess.value = true
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Не удалось сохранить оплату"
            } finally {
                if (!_saveSuccess.value) _isSaving.value = false
            }
        }
    }
}
