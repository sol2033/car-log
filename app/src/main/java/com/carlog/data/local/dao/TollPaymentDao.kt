package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.TollPayment
import kotlinx.coroutines.flow.Flow

/**
 * DAO для оплат платных дорог.
 *
 * Пробега у записи нет, поэтому здесь нет ни `getMaxMileage`, ни `clampMileageTo` —
 * в синхронизации пробега автомобиля модуль не участвует (см. [TollPayment]).
 */
@Dao
interface TollPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTollPayment(payment: TollPayment): Long

    @Update
    suspend fun updateTollPayment(payment: TollPayment)

    @Delete
    suspend fun deleteTollPayment(payment: TollPayment)

    @Query("SELECT * FROM toll_payments WHERE id = :id")
    fun getTollPaymentById(id: Long): Flow<TollPayment?>

    @Query("SELECT * FROM toll_payments WHERE carId = :carId ORDER BY date DESC")
    fun getTollPaymentsByCarId(carId: Long): Flow<List<TollPayment>>

    /**
     * Названия дорог для подсказок, самые недавние первыми. По всем машинам: дорога
     * не зависит от автомобиля, а ездит по ней обычно один и тот же человек.
     */
    @Query("""
        SELECT roadName FROM toll_payments
        WHERE roadName IS NOT NULL AND TRIM(roadName) != ''
        GROUP BY roadName
        ORDER BY MAX(date) DESC
    """)
    fun getRoadNames(): Flow<List<String>>
}
