package com.carlog.presentation.screens.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ExpenseRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для детальной информации о прочем расходе
 */
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long = savedStateHandle.get<Long>("expenseId") ?: 0L

    val expense: StateFlow<Expense?> = expenseRepository.getExpenseById(expenseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val car: StateFlow<Car?> = expense.filterNotNull().flatMapLatest { expense ->
        carRepository.getCarById(expense.carId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    fun deleteExpense() {
        viewModelScope.launch {
            val currentExpense = expense.value ?: return@launch
            expenseRepository.deleteExpense(currentExpense)
            // Обновляем пробег автомобиля после удаления
            carRepository.updateCarMileageAfterDelete(currentExpense.carId, currentExpense.mileage)
            _deleteSuccess.value = true
        }
    }
}
