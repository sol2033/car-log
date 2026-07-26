package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Part
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    
    @Query("SELECT * FROM parts ORDER BY installDate DESC")
    suspend fun getAllPartsOnce(): List<Part>
    
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

    @Query("SELECT MAX(breakdownMileage) FROM parts WHERE carId = :carId AND breakdownMileage IS NOT NULL")
    suspend fun getMaxBreakdownMileage(carId: Long): Int?

    @Query("SELECT COUNT(*) FROM parts WHERE carId = :carId AND (installMileage > :mileage OR breakdownMileage > :mileage)")
    suspend fun getCountAboveMileage(carId: Long, mileage: Int): Int

    // Прижимает пробег установки/поломки к новому (уменьшенному) пробегу автомобиля
    @Query("UPDATE parts SET installMileage = :mileage, updatedAt = :updatedAt WHERE carId = :carId AND installMileage > :mileage")
    suspend fun clampInstallMileageTo(carId: Long, mileage: Int, updatedAt: Long)

    @Query("UPDATE parts SET breakdownMileage = :mileage, updatedAt = :updatedAt WHERE carId = :carId AND breakdownMileage > :mileage")
    suspend fun clampBreakdownMileageTo(carId: Long, mileage: Int, updatedAt: Long)

    // Пересчитывает пройденный ресурс после изменения пробега установки/поломки
    @Query("""
        UPDATE parts SET mileageDriven = breakdownMileage - installMileage, updatedAt = :updatedAt
        WHERE carId = :carId AND breakdownMileage IS NOT NULL AND mileageDriven IS NOT NULL
        AND mileageDriven != breakdownMileage - installMileage
    """)
    suspend fun refreshMileageDriven(carId: Long, updatedAt: Long)
}
