package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с прочими расходами
 */
@Dao
interface ExpenseDao {

    // === CRUD операции ===
    
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpensesOnce(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    fun getExpenseById(expenseId: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    fun getExpensesByCarId(carId: Long): Flow<List<Expense>>

    // === Сортировка ===

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    fun getExpensesSortedByDateDesc(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date ASC")
    fun getExpensesSortedByDateAsc(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY mileage DESC")
    fun getExpensesSortedByMileageDesc(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY mileage ASC")
    fun getExpensesSortedByMileageAsc(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY cost DESC")
    fun getExpensesSortedByCostDesc(carId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY cost ASC")
    fun getExpensesSortedByCostAsc(carId: Long): Flow<List<Expense>>

    // === Статистика ===

    @Query("SELECT COUNT(*) FROM expenses WHERE carId = :carId")
    fun getExpensesCount(carId: Long): Flow<Int>

    @Query("SELECT SUM(cost) FROM expenses WHERE carId = :carId")
    fun getTotalCost(carId: Long): Flow<Double?>

    @Query("SELECT AVG(cost) FROM expenses WHERE carId = :carId")
    fun getAverageCost(carId: Long): Flow<Double?>

    @Query("SELECT MAX(mileage) FROM expenses WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?

    @Query("""
        SELECT category, SUM(cost) as totalCost 
        FROM expenses 
        WHERE carId = :carId 
        GROUP BY category 
        ORDER BY totalCost DESC
    """)
    fun getCostByCategoryList(carId: Long): Flow<List<CategoryCostResult>>

    @Query("""
        SELECT strftime('%Y-%m', date/1000, 'unixepoch') as month, SUM(cost) as totalCost 
        FROM expenses 
        WHERE carId = :carId 
        GROUP BY month 
        ORDER BY month
    """)
    fun getMonthlyCosts(carId: Long): Flow<List<MonthCostResult>>

    @Query("""
        SELECT * FROM expenses 
        WHERE carId = :carId AND date >= :startDate AND date <= :endDate
        ORDER BY date DESC
    """)
    fun getExpensesByPeriod(carId: Long, startDate: Long, endDate: Long): Flow<List<Expense>>
}

/**
 * Вспомогательный класс для результатов агрегирующих запросов
 */
data class MonthCostResult(
    val month: String,
    val totalCost: Double
)

/**
 * Результат суммы расходов по категории
 */
data class CategoryCostResult(
    val category: String,
    val totalCost: Double
)
