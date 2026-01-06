package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Breakdown
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakdownDao {
    
    @Query("SELECT * FROM breakdowns WHERE carId = :carId ORDER BY breakdownDate DESC")
    fun getBreakdownsByCarId(carId: Long): Flow<List<Breakdown>>
    
    @Query("SELECT * FROM breakdowns WHERE id = :breakdownId")
    fun getBreakdownById(breakdownId: Long): Flow<Breakdown?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakdown(breakdown: Breakdown): Long
    
    @Update
    suspend fun updateBreakdown(breakdown: Breakdown)
    
    @Delete
    suspend fun deleteBreakdown(breakdown: Breakdown)
    
    @Query("DELETE FROM breakdowns WHERE carId = :carId")
    suspend fun deleteBreakdownsByCarId(carId: Long)
    
    @Query("SELECT COUNT(*) FROM breakdowns WHERE carId = :carId")
    suspend fun getBreakdownsCountByCarId(carId: Long): Int
    
    @Query("SELECT SUM(totalCost) FROM breakdowns WHERE carId = :carId")
    suspend fun getTotalBreakdownsCostByCarId(carId: Long): Double?
    
    @Query("""
        SELECT SUM(totalCost) FROM breakdowns 
        WHERE carId = :carId 
        AND breakdownDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getBreakdownsCostByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Double?
    
    @Query("SELECT MAX(breakdownMileage) FROM breakdowns WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?
}
