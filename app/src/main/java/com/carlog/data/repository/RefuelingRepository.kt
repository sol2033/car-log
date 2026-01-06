package com.carlog.data.repository

import com.carlog.data.local.dao.RefuelingDao
import com.carlog.domain.model.Refueling
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefuelingRepository @Inject constructor(
    private val refuelingDao: RefuelingDao
) {
    
    fun getRefuelingsByCarId(carId: Long): Flow<List<Refueling>> {
        return refuelingDao.getRefuelingsByCarId(carId)
    }
    
    fun getRefuelingById(refuelingId: Long): Flow<Refueling?> {
        return refuelingDao.getRefuelingById(refuelingId)
    }
    
    fun getRefuelingsByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<Refueling>> {
        return refuelingDao.getRefuelingsByPeriod(carId, startDate, endDate)
    }
    
    fun getRefuelingsCountByCarId(carId: Long): Flow<Int> {
        return refuelingDao.getRefuelingsCountByCarId(carId)
    }
    
    fun getTotalCostByCarId(carId: Long): Flow<Double?> {
        return refuelingDao.getTotalCostByCarId(carId)
    }
    
    fun getTotalLitersByCarId(carId: Long): Flow<Double?> {
        return refuelingDao.getTotalLitersByCarId(carId)
    }
    
    fun getAverageConsumptionByCarId(carId: Long): Flow<Double?> {
        return refuelingDao.getAverageConsumptionByCarId(carId)
    }
    
    suspend fun insertRefueling(refueling: Refueling): Long {
        return refuelingDao.insertRefueling(refueling)
    }
    
    suspend fun updateRefueling(refueling: Refueling) {
        refuelingDao.updateRefueling(refueling)
    }
    
    suspend fun deleteRefueling(refueling: Refueling) {
        refuelingDao.deleteRefueling(refueling)
    }
    
    suspend fun deleteRefuelingsByCarId(carId: Long) {
        refuelingDao.deleteRefuelingsByCarId(carId)
    }
}
