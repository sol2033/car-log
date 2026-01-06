package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Accident
import kotlinx.coroutines.flow.Flow

@Dao
interface AccidentDao {
    
    @Query("SELECT * FROM accidents WHERE carId = :carId ORDER BY date DESC")
    fun getAccidentsByCarId(carId: Long): Flow<List<Accident>>
    
    @Query("SELECT * FROM accidents WHERE id = :accidentId")
    fun getAccidentById(accidentId: Long): Flow<Accident?>
    
    @Query("SELECT COUNT(*) FROM accidents WHERE carId = :carId")
    fun getAccidentsCountByCarId(carId: Long): Flow<Int>
    
    @Query("""
        SELECT SUM(repairCost) FROM accidents 
        WHERE carId = :carId AND repairCost IS NOT NULL
    """)
    fun getTotalRepairCostByCarId(carId: Long): Flow<Double?>
    
    @Query("""
        SELECT SUM(
            COALESCE(osagoPayout, 0) + 
            COALESCE(kaskoPayout, 0) + 
            COALESCE(culpritPayout, 0)
        ) FROM accidents 
        WHERE carId = :carId
    """)
    fun getTotalPayoutsByCarId(carId: Long): Flow<Double?>
    
    @Query("""
        SELECT * FROM accidents 
        WHERE carId = :carId 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC
    """)
    fun getAccidentsByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<Accident>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccident(accident: Accident): Long
    
    @Query("SELECT MAX(mileage) FROM accidents WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?
    
    @Update
    suspend fun updateAccident(accident: Accident)
    
    @Delete
    suspend fun deleteAccident(accident: Accident)
    
    @Query("DELETE FROM accidents WHERE carId = :carId")
    suspend fun deleteAccidentsByCarId(carId: Long)
}
