package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.data.local.entity.CarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    fun getAllCars(): Flow<List<CarEntity>>
    
    @Query("SELECT * FROM cars WHERE id = :carId")
    fun getCarById(carId: Long): Flow<CarEntity?>
    
    @Query("SELECT * FROM cars WHERE id = :carId")
    suspend fun getCarByIdOnce(carId: Long): CarEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: CarEntity): Long
    
    @Update
    suspend fun updateCar(car: CarEntity)
    
    @Delete
    suspend fun deleteCar(car: CarEntity)
    
    @Query("UPDATE cars SET currentMileage = :mileage, updatedAt = :updatedAt WHERE id = :carId")
    suspend fun updateMileage(carId: Long, mileage: Int, updatedAt: Long)
    
    @Query("SELECT COUNT(*) FROM cars")
    suspend fun getCarsCount(): Int
}
