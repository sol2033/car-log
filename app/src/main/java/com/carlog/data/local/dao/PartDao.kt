package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Part
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    
    @Query("SELECT * FROM parts WHERE carId = :carId ORDER BY installDate DESC")
    fun getPartsByCarId(carId: Long): Flow<List<Part>>
    
    @Query("SELECT * FROM parts WHERE carId = :carId AND isBroken = 0 ORDER BY installDate DESC")
    fun getActivePartsByCarId(carId: Long): Flow<List<Part>>
    
    @Query("SELECT * FROM parts WHERE carId = :carId AND isBroken = 1 ORDER BY breakdownDate DESC")
    fun getBrokenPartsByCarId(carId: Long): Flow<List<Part>>
    
    @Query("SELECT * FROM parts WHERE id = :partId")
    fun getPartById(partId: Long): Flow<Part?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: Part): Long
    
    @Update
    suspend fun updatePart(part: Part)
    
    @Delete
    suspend fun deletePart(part: Part)
    
    @Query("DELETE FROM parts WHERE carId = :carId")
    suspend fun deletePartsByCarId(carId: Long)
    
    @Query("SELECT COUNT(*) FROM parts WHERE carId = :carId")
    suspend fun getPartsCountByCarId(carId: Long): Int
    
    @Query("SELECT SUM(price + COALESCE(servicePrice, 0)) FROM parts WHERE carId = :carId")
    suspend fun getTotalPartsCostByCarId(carId: Long): Double?
    
    @Query("SELECT MAX(installMileage) FROM parts WHERE carId = :carId")
    suspend fun getMaxMileage(carId: Long): Int?
}
