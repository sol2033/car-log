package com.carlog.presentation.screens.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ExpenseRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Expense
import com.carlog.domain.model.ExpenseCategories
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

    private val _category = MutableStateFlow(ExpenseCategories.SELF_WASH)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _cost = MutableStateFlow("")
    val cost: StateFlow<String> = _cost.asStateFlow()

    private val _serviceName = MutableStateFlow("")
    val serviceName: StateFlow<String> = _serviceName.asStateFlow()

    private val _serviceAddress = MutableStateFlow("")
    val serviceAddress: StateFlow<String> = _serviceAddress.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // === Валидация ===

    private val _dateError = MutableStateFlow<String?>(null)
    val dateError: StateFlow<String?> = _dateError.asStateFlow()

    private val _mileageError = MutableStateFlow<String?>(null)
    val mileageError: StateFlow<String?> = _mileageError.asStateFlow()

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

    fun updateNotes(newNotes: String) {
        _notes.value = newNotes
    }

    // === Сохранение ===

    fun saveExpense() {
        viewModelScope.launch {
            // Валидация
            var hasError = false

            if (_mileage.value.isBlank()) {
                _mileageError.value = "Введите пробег"
                hasError = true
            }

            val mileageValue = _mileage.value.toIntOrNull()
            if (mileageValue == null && _mileage.value.isNotBlank()) {
                _mileageError.value = "Введите корректное значение"
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

            val costValue = _cost.value.toDoubleOrNull()
            if (costValue == null && _cost.value.isNotBlank()) {
                _costError.value = "Введите корректное значение"
                hasError = true
            }

            if (hasError) return@launch

            // Сохранение
            val expenseToSave = if (isEditMode) {
                _expense.value!!.copy(
                    date = _date.value,
                    mileage = mileageValue!!,
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
                    category = _category.value,
                    cost = costValue!!,
                    serviceName = _serviceName.value.ifBlank { null },
                    serviceAddress = _serviceAddress.value.ifBlank { null },
                    notes = _notes.value.ifBlank { null }
                )
            }

            if (isEditMode) {
                expenseRepository.updateExpense(expenseToSave)
            } else {
                expenseRepository.insertExpense(expenseToSave)
            }

            // Обновляем пробег автомобиля
            carRepository.updateCarMileageIfNeeded(carId, mileageValue!!)

            _saveSuccess.value = true
        }
    }
}
