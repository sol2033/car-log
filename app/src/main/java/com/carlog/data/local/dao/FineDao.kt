package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Fine
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы со штрафами.
 *
 * Пробега у штрафа нет, поэтому здесь нет ни `getMaxMileage`, ни `clampMileageTo` —
 * в синхронизации пробега автомобиля модуль не участвует (см. [Fine]).
 */
@Dao
interface FineDao {

    // === CRUD операции ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFine(fine: Fine): Long

    @Update
    suspend fun updateFine(fine: Fine)

    @Delete
    suspend fun deleteFine(fine: Fine)

    @Query("SELECT * FROM fines WHERE id = :fineId")
    fun getFineById(fineId: Long): Flow<Fine?>

    @Query("SELECT * FROM fines WHERE carId = :carId ORDER BY date DESC")
    fun getFinesByCarId(carId: Long): Flow<List<Fine>>

    // === Сводка для экрана списка ===

    @Query("SELECT COUNT(*) FROM fines WHERE carId = :carId")
    fun getFinesCount(carId: Long): Flow<Int>

    /**
     * Фактически уплаченные деньги. `paidAmount` может быть не заполнен (платили без скидки) —
     * тогда в счёт идёт сумма постановления, как и в [Fine.paidCost].
     */
    @Query("SELECT SUM(COALESCE(paidAmount, amount)) FROM fines WHERE carId = :carId AND isPaid = 1")
    fun getTotalPaid(carId: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM fines WHERE carId = :carId AND isPaid = 0")
    fun getTotalUnpaid(carId: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM fines WHERE carId = :carId AND isPaid = 0")
    fun getUnpaidCount(carId: Long): Flow<Int>

    @Query("""
        SELECT * FROM fines
        WHERE carId = :carId AND date >= :startDate AND date <= :endDate
        ORDER BY date DESC
    """)
    fun getFinesByPeriod(carId: Long, startDate: Long, endDate: Long): Flow<List<Fine>>
}
