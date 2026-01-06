package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    fun getAllCars(): Flow<List<Car>>
    
    @Query("SELECT * FROM cars WHERE id = :carId")
    fun getCarById(carId: Long): Flow<Car?>
    
    @Query("SELECT * FROM cars WHERE id = :carId")
    suspend fun getCarByIdOnce(carId: Long): Car?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: Car): Long
    
    @Update
    suspend fun updateCar(car: Car)
    
    @Delete
    suspend fun deleteCar(car: Car)
    
    @Query("UPDATE cars SET currentMileage = :mileage, updatedAt = :updatedAt WHERE id = :carId")
    suspend fun updateMileage(carId: Long, mileage: Int, updatedAt: Long)
    
    @Query("SELECT COUNT(*) FROM cars")
    suspend fun getCarsCount(): Int
}
