package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Consumable
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumableDao {
    
    @Query("SELECT * FROM consumables ORDER BY installationDate DESC")
    suspend fun getAllConsumablesOnce(): List<Consumable>
    
    @Query("SELECT * FROM consumables WHERE carId = :carId ORDER BY installationDate DESC")
    fun getConsumablesByCarId(carId: Long): Flow<List<Consumable>>
    
    @Query("SELECT * FROM consumables WHERE carId = :carId AND isActive = 1 ORDER BY category")
    fun getActiveConsumablesByCarId(carId: Long): Flow<List<Consumable>>
    
    @Query("SELECT * FROM consumables WHERE carId = :carId AND category = :category ORDER BY installationDate DESC")
    fun getConsumablesByCategory(carId: Long, category: String): Flow<List<Consumable>>
    
    @Query("SELECT * FROM consumables WHERE id = :consumableId")
    fun getConsumableById(consumableId: Long): Flow<Consumable?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumable(consumable: Consumable): Long
    
    @Update
    suspend fun updateConsumable(consumable: Consumable)
    
    @Delete
    suspend fun deleteConsumable(consumable: Consumable)
    
    @Query("DELETE FROM consumables WHERE carId = :carId")
    suspend fun deleteConsumablesByCarId(carId: Long)
    
    @Query("SELECT MAX(installationMileage) FROM consumables WHERE carId = :carId")
    suspend fun getMaxInstallationMileage(carId: Long): Int?
    
    @Query("SELECT MAX(replacementMileage) FROM consumables WHERE carId = :carId AND replacementMileage IS NOT NULL")
    suspend fun getMaxReplacementMileage(carId: Long): Int?
}
