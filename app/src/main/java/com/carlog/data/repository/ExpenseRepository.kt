package com.carlog.data.repository

import com.carlog.data.local.dao.ExpenseDao
import com.carlog.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository для работы с прочими расходами
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    // === CRUD операции ===

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    fun getExpenseById(expenseId: Long): Flow<Expense?> {
        return expenseDao.getExpenseById(expenseId)
    }

    fun getExpensesByCarId(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesByCarId(carId)
    }

    // === Сортировка ===

    fun getExpensesSortedByDateDesc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByDateDesc(carId)
    }

    fun getExpensesSortedByDateAsc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByDateAsc(carId)
    }

    fun getExpensesSortedByMileageDesc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByMileageDesc(carId)
    }

    fun getExpensesSortedByMileageAsc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByMileageAsc(carId)
    }

    fun getExpensesSortedByCostDesc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByCostDesc(carId)
    }

    fun getExpensesSortedByCostAsc(carId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesSortedByCostAsc(carId)
    }

    // === Статистика ===

    fun getExpensesCount(carId: Long): Flow<Int> {
        return expenseDao.getExpensesCount(carId)
    }

    fun getTotalCost(carId: Long): Flow<Double?> {
        return expenseDao.getTotalCost(carId)
    }

    fun getAverageCost(carId: Long): Flow<Double?> {
        return expenseDao.getAverageCost(carId)
    }

    suspend fun getMaxMileage(carId: Long): Int? {
        return expenseDao.getMaxMileage(carId)
    }

    fun getCostByCategory(carId: Long): Flow<Map<String, Double>> {
        return expenseDao.getCostByCategoryList(carId).map { list ->
            list.associate { it.category to it.totalCost }
        }
    }

    fun getMonthlyCosts(carId: Long): Flow<List<com.carlog.data.local.dao.MonthCostResult>> {
        return expenseDao.getMonthlyCosts(carId)
    }

    fun getExpensesByPeriod(carId: Long, startDate: Long, endDate: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesByPeriod(carId, startDate, endDate)
    }
}
