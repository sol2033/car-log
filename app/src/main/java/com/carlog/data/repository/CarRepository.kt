package com.carlog.data.repository

import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.domain.model.Car
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao,
    private val breakdownDao: BreakdownDao,
    private val refuelingDao: RefuelingDao,
    private val consumableDao: ConsumableDao,
    private val partDao: PartDao,
    private val accidentDao: AccidentDao,
    private val expenseDao: ExpenseDao
) {
    fun getAllCars(): Flow<List<Car>> {
        return carDao.getAllCars()
    }
    
    fun getCarById(carId: Long): Flow<Car?> {
        return carDao.getCarById(carId)
    }
    
    suspend fun getCarByIdOnce(carId: Long): Car? {
        return carDao.getCarByIdOnce(carId)
    }
    
    suspend fun insertCar(car: Car): Long {
        return carDao.insertCar(car)
    }
    
    suspend fun updateCar(car: Car) {
        carDao.updateCar(car)
    }
    
    suspend fun deleteCar(car: Car) {
        carDao.deleteCar(car)
    }
    
    suspend fun updateMileage(carId: Long, mileage: Int) {
        carDao.updateMileage(carId, mileage, System.currentTimeMillis())
    }
    
    suspend fun getCarsCount(): Int {
        return carDao.getCarsCount()
    }
    
    // Оптимизированное обновление пробега при добавлении/редактировании записи
    suspend fun updateCarMileageIfNeeded(carId: Long, newMileage: Int) {
        val currentCar = carDao.getCarById(carId).first() ?: return
        
        // Если новый пробег не больше текущего - ничего не делаем
        if (newMileage <= currentCar.currentMileage) {
            return
        }
        
        // Обновляем пробег на новое значение
        updateMileage(carId, newMileage)
    }
    
    // Оптимизированное обновление пробега после удаления записи
    suspend fun updateCarMileageAfterDelete(carId: Long, deletedMileage: Int) {
        val currentCar = carDao.getCarById(carId).first() ?: return
        
        // Если удаленный пробег меньше текущего - ничего не делаем
        // Если равен или больше - пересчитываем максимум
        if (deletedMileage < currentCar.currentMileage) {
            return
        }
        
        // Ищем новый максимальный пробег через SQL MAX() - быстро!
        val maxMileages = listOfNotNull(
            breakdownDao.getMaxMileage(carId),
            refuelingDao.getMaxMileage(carId),
            consumableDao.getMaxInstallationMileage(carId),
            consumableDao.getMaxReplacementMileage(carId),
            partDao.getMaxMileage(carId),
            accidentDao.getMaxMileage(carId),
            expenseDao.getMaxMileage(carId)
        )
        
        val maxMileage = maxMileages.maxOrNull() ?: 0
        updateMileage(carId, maxMileage)
    }
    
    // Синхронизация пробега для всех автомобилей (для миграции существующих данных)
    suspend fun syncAllCarsMileage() {
        val cars = getAllCars().first()
        cars.forEach { car ->
            val maxMileages = listOfNotNull(
                breakdownDao.getMaxMileage(car.id),
                refuelingDao.getMaxMileage(car.id),
                consumableDao.getMaxInstallationMileage(car.id),
                consumableDao.getMaxReplacementMileage(car.id),
                partDao.getMaxMileage(car.id),
                accidentDao.getMaxMileage(car.id),
                expenseDao.getMaxMileage(car.id)
            )
            
            val maxMileage = maxMileages.maxOrNull()
            if (maxMileage != null && maxMileage > car.currentMileage) {
                updateMileage(car.id, maxMileage)
            }
        }
    }
}
