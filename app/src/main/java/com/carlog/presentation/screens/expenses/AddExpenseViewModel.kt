package com.carlog.presentation.screens.expenses

import kotlinx.coroutines.CancellationException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.NameSuggestionRepository
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ExpenseRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Expense
import com.carlog.domain.model.ExpenseCategories
import com.carlog.util.toMileageOrNull
import com.carlog.util.toDecimalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для добавления/редактирования прочего расхода
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val carRepository: CarRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    private val nameSuggestionRepository: NameSuggestionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    private val expenseId: Long? = savedStateHandle.get<String>("expenseId")?.toLongOrNull()

    val car: StateFlow<Car?> = carRepository.getCarById(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense.asStateFlow()

    val isEditMode: Boolean get() = expenseId != null

    // === Поля формы ===

    private val _date = MutableStateFlow(System.currentTimeMillis())
    val date: StateFlow<Long> = _date.asStateFlow()

    private val _mileage = MutableStateFlow("")
    val mileage: StateFlow<String> = _mileage.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _category = MutableStateFlow(ExpenseCategories.SELF_WASH)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _cost = MutableStateFlow("")
    val cost: StateFlow<String> = _cost.asStateFlow()

    private val _serviceName = MutableStateFlow("")
    val serviceName: StateFlow<String> = _serviceName.asStateFlow()

    /** Подсказки к полю «Название сервиса» — сервисы из прошлых записей, см. [NameSuggestionRepository] */
    val serviceNameSuggestions: StateFlow<List<String>> = nameSuggestionRepository
        .serviceNameSuggestions(_serviceName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serviceAddress = MutableStateFlow("")
    val serviceAddress: StateFlow<String> = _serviceAddress.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // === Валидация ===

    private val _dateError = MutableStateFlow<String?>(null)
    val dateError: StateFlow<String?> = _dateError.asStateFlow()

    private val _mileageError = MutableStateFlow<String?>(null)
    val mileageError: StateFlow<String?> = _mileageError.asStateFlow()

    private val _titleError = MutableStateFlow<String?>(null)
    val titleError: StateFlow<String?> = _titleError.asStateFlow()

    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError.asStateFlow()

    private val _costError = MutableStateFlow<String?>(null)
    val costError: StateFlow<String?> = _costError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        if (expenseId != null) {
            loadExpense(expenseId)
        } else {
            // Подставляем текущий пробег автомобиля
            viewModelScope.launch {
                car.collect { currentCar ->
                    if (currentCar != null && _mileage.value.isEmpty()) {
                        _mileage.value = currentCar.currentMileage.toString()
                    }
                }
            }
        }
    }

    private fun loadExpense(id: Long) {
        viewModelScope.launch {
            expenseRepository.getExpenseById(id).collect { loadedExpense ->
                if (loadedExpense != null) {
                    _expense.value = loadedExpense
                    _date.value = loadedExpense.date
                    _mileage.value = loadedExpense.mileage.toString()
                    _title.value = loadedExpense.title ?: ""
                    _category.value = loadedExpense.category
                    _cost.value = loadedExpense.cost.toString()
                    _serviceName.value = loadedExpense.serviceName ?: ""
                    _serviceAddress.value = loadedExpense.serviceAddress ?: ""
                    _notes.value = loadedExpense.notes ?: ""
                }
            }
        }
    }

    // === Обновление полей ===

    fun updateDate(newDate: Long) {
        _date.value = newDate
        _dateError.value = null
    }

    fun updateMileage(newMileage: String) {
        _mileage.value = newMileage
        _mileageError.value = null
    }

    fun updateTitle(newTitle: String) {
        _title.value = newTitle
        _titleError.value = null
    }

    fun updateCategory(newCategory: String) {
        _category.value = newCategory
        _categoryError.value = null
    }

    fun updateCost(newCost: String) {
        _cost.value = newCost
        _costError.value = null
    }

    fun updateServiceName(newName: String) {
        _serviceName.value = newName
    }

    fun updateServiceAddress(newAddress: String) {
        _serviceAddress.value = newAddress
    }

    /**
     * Сервис выбран из подсказок: подставляем и его адрес из прошлой записи — но только
     * в пустое поле, введённый адрес не перетираем. Пока адрес ищется, пользователь мог
     * начать его набирать, поэтому пустота проверяется ещё раз перед записью.
     */
    fun pickServiceNameSuggestion(name: String) {
        updateServiceName(name)
        if (_serviceAddress.value.isNotBlank()) return
        viewModelScope.launch {
            val address = nameSuggestionRepository.lastServiceAddress(name) ?: return@launch
            if (_serviceAddress.value.isBlank() && _serviceName.value == name) {
                _serviceAddress.value = address
            }
        }
    }

    fun updateNotes(newNotes: String) {
        _notes.value = newNotes
    }

    // === Сохранение ===

    /**
     * Идёт сохранение. Ставится синхронно, до запуска корутины: два быстрых тапа (галочка
     * в шапке и кнопка внизу) иначе оба проходили проверки и вставляли две записи.
     * После успеха не сбрасывается — экран вот-вот закроется.
     */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * Ошибка сохранения (сбой базы). Раньше исключение уходило из корутины необработанным,
     * и приложение падало вместе с набранной формой. Теперь форма остаётся на месте,
     * а пользователь видит, что запись не сохранена, и может попробовать ещё раз.
     */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun dismissSaveError() {
        _saveError.value = null
    }

    fun saveExpense() {
        if (_isSaving.value) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                saveExpenseInternal()
            } catch (e: CancellationException) {
                // Уход с экрана во время сохранения — не ошибка, отмену не глотаем
                throw e
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Не удалось сохранить расход"
            } finally {
                if (!_saveSuccess.value) _isSaving.value = false
            }
        }
    }

    private suspend fun saveExpenseInternal() {
        run {
            // Запись ещё не догрузилась — сохранять нельзя: ниже идёт `_expense.value!!`,
            // и нажатие «Сохранить» до конца загрузки роняло приложение по NPE
            if (isEditMode && _expense.value == null) return

            // Валидация
            var hasError = false

            if (_mileage.value.isBlank()) {
                _mileageError.value = "Введите пробег"
                hasError = true
            }

            val mileageValue = _mileage.value.toMileageOrNull()
            if (mileageValue == null && _mileage.value.isNotBlank()) {
                _mileageError.value = "Введите корректное значение"
                hasError = true
            }

            if (_title.value.isBlank()) {
                _titleError.value = "Введите название"
                hasError = true
            }

            if (_category.value.isBlank()) {
                _categoryError.value = "Выберите категорию"
                hasError = true
            }

            if (_cost.value.isBlank()) {
                _costError.value = "Введите стоимость"
                hasError = true
            }

            val costValue = _cost.value.toDecimalOrNull()
            // Отрицательная стоимость раньше принималась и уменьшала расходы в статистике
            if ((costValue == null || costValue < 0) && _cost.value.isNotBlank()) {
                _costError.value = "Введите корректное значение"
                hasError = true
            }

            if (hasError) return

            // Сохранение
            val expenseToSave = if (isEditMode) {
                _expense.value!!.copy(
                    date = _date.value,
                    mileage = mileageValue!!,
                    title = _title.value.ifBlank { null },
                    category = _category.value,
                    cost = costValue!!,
                    serviceName = _serviceName.value.ifBlank { null },
                    serviceAddress = _serviceAddress.value.ifBlank { null },
                    notes = _notes.value.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                Expense(
                    carId = carId,
                    date = _date.value,
                    mileage = mileageValue!!,
                    title = _title.value.ifBlank { null },
                    category = _category.value,
                    cost = costValue!!,
                    serviceName = _serviceName.value.ifBlank { null },
                    serviceAddress = _serviceAddress.value.ifBlank { null },
                    notes = _notes.value.ifBlank { null }
                )
            }

            // Пробег записи до правки — снимок до сохранения: поток записи после него обновится
            val originalMileage = _expense.value?.mileage
            if (isEditMode) {
                expenseRepository.updateExpense(expenseToSave)
            } else {
                expenseRepository.insertExpense(expenseToSave)
            }

            // Обновляем пробег автомобиля; уменьшенный при правке пробег тоже учитываем
            if (originalMileage != null) {
                carRepository.updateCarMileageAfterEdit(carId, originalMileage, mileageValue)
            } else {
                carRepository.updateCarMileageIfNeeded(carId, mileageValue)
            }
            
            // Уведомляем о изменении данных для резервного копирования
            dataChangeNotifier.notifyDataChanged()

            _saveSuccess.value = true
        }
    }
}
