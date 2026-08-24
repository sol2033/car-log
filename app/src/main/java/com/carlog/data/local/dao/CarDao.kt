package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    fun getAllCars(): Flow<List<Car>>
    
    @Query("SELECT * FROM cars ORDER BY updatedAt DESC")
    suspend fun getAllCarsOnce(): List<Car>
    
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

    // Пробег при покупке не может оказаться больше текущего (при уменьшении пробега вручную)
    @Query("UPDATE cars SET purchaseMileage = :mileage, updatedAt = :updatedAt WHERE id = :carId AND purchaseMileage > :mileage")
    suspend fun clampPurchaseMileage(carId: Long, mileage: Int, updatedAt: Long)
    
    // Запрос нового отсчёта расхода топлива. updatedAt не трогаем: по нему сортируется
    // список машин, а служебный флаг не должен переставлять машину в начало гаража
    @Query("UPDATE cars SET fuelResetPending = :pending WHERE id = :carId")
    suspend fun setFuelResetPending(carId: Long, pending: Boolean)

    @Query("SELECT COUNT(*) FROM cars")
    suspend fun getCarsCount(): Int
}
