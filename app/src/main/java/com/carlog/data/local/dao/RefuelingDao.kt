package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Refueling
import kotlinx.coroutines.flow.Flow

@Dao
interface RefuelingDao {
    
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
    
    @Query("SELECT AVG(fuelConsumption) FROM refuelings WHERE carId = :carId AND fuelConsumption IS NOT NULL")
    fun getAverageConsumptionByCarId(carId: Long): Flow<Double?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefueling(refueling: Refueling): Long
    
    @Update
    suspend fun updateRefueling(refueling: Refueling)
    
    @Delete
    suspend fun deleteRefueling(refueling: Refueling)
    
    @Query("DELETE FROM refuelings WHERE carId = :carId")
    suspend fun deleteRefuelingsByCarId(carId: Long)
    
    @Query("SELECT MAX(mileage) FROM refuelings WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?
}
