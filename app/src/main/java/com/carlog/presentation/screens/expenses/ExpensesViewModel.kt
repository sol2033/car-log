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
 * ViewModel для списка прочих расходов
 */
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val carRepository: CarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L

    val car: StateFlow<Car?> = carRepository.getCarById(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // === Сортировка ===

    private val _sortType = MutableStateFlow(SortType.DATE_DESC)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val expenses: StateFlow<List<Expense>> = _sortType.flatMapLatest { sort ->
        when (sort) {
            SortType.DATE_DESC -> expenseRepository.getExpensesSortedByDateDesc(carId)
            SortType.DATE_ASC -> expenseRepository.getExpensesSortedByDateAsc(carId)
            SortType.MILEAGE_DESC -> expenseRepository.getExpensesSortedByMileageDesc(carId)
            SortType.MILEAGE_ASC -> expenseRepository.getExpensesSortedByMileageAsc(carId)
            SortType.COST_DESC -> expenseRepository.getExpensesSortedByCostDesc(carId)
            SortType.COST_ASC -> expenseRepository.getExpensesSortedByCostAsc(carId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
    }

    // === Статистика ===

    val expensesCount: StateFlow<Int> = expenseRepository.getExpensesCount(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCost: StateFlow<Double?> = expenseRepository.getTotalCost(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val averageCost: StateFlow<Double?> = expenseRepository.getAverageCost(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // === Удаление ===

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
            // Обновляем пробег автомобиля после удаления
            carRepository.updateCarMileageAfterDelete(expense.carId, expense.mileage)
        }
    }

    enum class SortType {
        DATE_DESC,
        DATE_ASC,
        MILEAGE_DESC,
        MILEAGE_ASC,
        COST_DESC,
        COST_ASC
    }
}
