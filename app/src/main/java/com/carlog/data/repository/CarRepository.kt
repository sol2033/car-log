package com.carlog.data.repository

import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.entity.CarEntity
import com.carlog.domain.model.Car
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) {
    fun getAllCars(): Flow<List<Car>> {
        return carDao.getAllCars().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    fun getCarById(carId: Long): Flow<Car?> {
        return carDao.getCarById(carId).map { it?.toDomainModel() }
    }
    
    suspend fun getCarByIdOnce(carId: Long): Car? {
        return carDao.getCarByIdOnce(carId)?.toDomainModel()
    }
    
    suspend fun insertCar(car: Car): Long {
        return carDao.insertCar(car.toEntity())
    }
    
    suspend fun updateCar(car: Car) {
        carDao.updateCar(car.toEntity())
    }
    
    suspend fun deleteCar(car: Car) {
        carDao.deleteCar(car.toEntity())
    }
    
    suspend fun updateMileage(carId: Long, mileage: Int) {
        carDao.updateMileage(carId, mileage, System.currentTimeMillis())
    }
    
    suspend fun getCarsCount(): Int {
        return carDao.getCarsCount()
    }
    
    // Mapper functions
    private fun CarEntity.toDomainModel(): Car {
        return Car(
            id = id,
            brand = brand,
            model = model,
            year = year,
            color = color,
            licensePlate = licensePlate,
            vin = vin,
            engineModel = engineModel,
            engineVolume = engineVolume,
            transmissionType = transmissionType,
            driveType = driveType,
            fuelType = fuelType,
            currentMileage = currentMileage,
            purchaseMileage = purchaseMileage,
            purchaseDate = purchaseDate,
            mainPhotoPath = mainPhotoPath,
            photosPaths = photosPaths,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    private fun Car.toEntity(): CarEntity {
        return CarEntity(
            id = id,
            brand = brand,
            model = model,
            year = year,
            color = color,
            licensePlate = licensePlate,
            vin = vin,
            engineModel = engineModel,
            engineVolume = engineVolume,
            transmissionType = transmissionType,
            driveType = driveType,
            fuelType = fuelType,
            currentMileage = currentMileage,
            purchaseMileage = purchaseMileage,
            purchaseDate = purchaseDate,
            mainPhotoPath = mainPhotoPath,
            photosPaths = photosPaths,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
