package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.OilTopUp
import kotlinx.coroutines.flow.Flow

/** Доливы моторного масла (см. [OilTopUp]) */
@Dao
interface OilTopUpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopUp(topUp: OilTopUp): Long

    @Update
    suspend fun updateTopUp(topUp: OilTopUp)

    @Delete
    suspend fun deleteTopUp(topUp: OilTopUp)

    @Query("SELECT * FROM oil_top_ups WHERE carId = :carId ORDER BY mileage DESC, date DESC")
    fun getTopUpsByCarId(carId: Long): Flow<List<OilTopUp>>

    // Пробег автомобиля — максимум по всем записям, доливы в нём участвуют (§3.2)
    @Query("SELECT MAX(mileage) FROM oil_top_ups WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?

    @Query("SELECT COUNT(*) FROM oil_top_ups WHERE carId = :carId AND mileage > :mileage")
    suspend fun getCountAboveMileage(carId: Long, mileage: Int): Int

    @Query("UPDATE oil_top_ups SET mileage = :mileage, updatedAt = :updatedAt WHERE carId = :carId AND mileage > :mileage")
    suspend fun clampMileageTo(carId: Long, mileage: Int, updatedAt: Long)
}
