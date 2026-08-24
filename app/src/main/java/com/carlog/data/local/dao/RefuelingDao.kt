package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Refueling
import kotlinx.coroutines.flow.Flow

@Dao
interface RefuelingDao {
    
    @Query("SELECT * FROM refuelings ORDER BY date DESC")
    suspend fun getAllRefuelingsOnce(): List<Refueling>
    
    @Query("SELECT * FROM refuelings WHERE carId = :carId ORDER BY date DESC, mileage DESC")
    fun getRefuelingsByCarId(carId: Long): Flow<List<Refueling>>
    
    @Query("SELECT * FROM refuelings WHERE id = :refuelingId")
    fun getRefuelingById(refuelingId: Long): Flow<Refueling?>
    
    @Query("""
        SELECT * FROM refuelings 
        WHERE carId = :carId 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC, mileage DESC
    """)
    fun getRefuelingsByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<Refueling>>
    
    @Query("SELECT COUNT(*) FROM refuelings WHERE carId = :carId")
    fun getRefuelingsCountByCarId(carId: Long): Flow<Int>
    
    @Query("SELECT SUM(totalCost) FROM refuelings WHERE carId = :carId AND totalCost IS NOT NULL")
    fun getTotalCostByCarId(carId: Long): Flow<Double?>
    
    @Query("SELECT SUM(liters) FROM refuelings WHERE carId = :carId")
    fun getTotalLitersByCarId(carId: Long): Flow<Double?>
    
    // Средний расход в сводке списка заправок: если назначена точка отсчёта (была пропущена
    // заправка), записи до неё в среднее не идут — иначе список и статистика разошлись бы
    @Query("""
        SELECT AVG(fuelConsumption) FROM refuelings
        WHERE carId = :carId AND fuelConsumption IS NOT NULL
        AND mileage >= COALESCE(
            (SELECT MAX(mileage) FROM refuelings WHERE carId = :carId AND isConsumptionResetPoint = 1),
            0
        )
    """)
    fun getAverageConsumptionByCarId(carId: Long): Flow<Double?>
    
    // Для пересчёта расхода: порядок по пробегу — это ось дистанции,
    // date/id разруливают записи с одинаковым пробегом
    @Query("SELECT * FROM refuelings WHERE carId = :carId ORDER BY mileage ASC, date ASC, id ASC")
    suspend fun getRefuelingsByCarIdSortedByMileageOnce(carId: Long): List<Refueling>

    // Снимает точку отсчёта расхода: средний расход снова считается по всей истории
    @Query("UPDATE refuelings SET isConsumptionResetPoint = 0, updatedAt = :updatedAt WHERE carId = :carId AND isConsumptionResetPoint = 1")
    suspend fun clearConsumptionResetPoints(carId: Long, updatedAt: Long)

    @Query("SELECT DISTINCT carId FROM refuelings")
    suspend fun getCarIdsWithRefuelings(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefueling(refueling: Refueling): Long

    @Update
    suspend fun updateRefueling(refueling: Refueling)

    @Update
    suspend fun updateRefuelings(refuelings: List<Refueling>)
    
    @Delete
    suspend fun deleteRefueling(refueling: Refueling)
    
    @Query("DELETE FROM refuelings WHERE carId = :carId")
    suspend fun deleteRefuelingsByCarId(carId: Long)
    
    @Query("SELECT MAX(mileage) FROM refuelings WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?

    @Query("SELECT COUNT(*) FROM refuelings WHERE carId = :carId AND mileage > :mileage")
    suspend fun getCountAboveMileage(carId: Long, mileage: Int): Int

    // Прижимает пробег записей к новому (уменьшенному) пробегу автомобиля
    @Query("UPDATE refuelings SET mileage = :mileage, updatedAt = :updatedAt WHERE carId = :carId AND mileage > :mileage")
    suspend fun clampMileageTo(carId: Long, mileage: Int, updatedAt: Long)
}
