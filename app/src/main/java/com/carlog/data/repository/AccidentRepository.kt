package com.carlog.data.repository

import com.carlog.data.local.dao.AccidentDao
import com.carlog.domain.model.Accident
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccidentRepository @Inject constructor(
    private val accidentDao: AccidentDao
) {
    
    fun getAccidentsByCarId(carId: Long): Flow<List<Accident>> =
        accidentDao.getAccidentsByCarId(carId)
    
    fun getAccidentById(accidentId: Long): Flow<Accident?> =
        accidentDao.getAccidentById(accidentId)
    
    fun getAccidentsCountByCarId(carId: Long): Flow<Int> =
        accidentDao.getAccidentsCountByCarId(carId)
    
    fun getTotalRepairCostByCarId(carId: Long): Flow<Double?> =
        accidentDao.getTotalRepairCostByCarId(carId)
    
    fun getTotalPayoutsByCarId(carId: Long): Flow<Double?> =
        accidentDao.getTotalPayoutsByCarId(carId)
    
    fun getAccidentsByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<Accident>> =
        accidentDao.getAccidentsByPeriod(carId, startDate, endDate)
    
    suspend fun insertAccident(accident: Accident): Long =
        accidentDao.insertAccident(accident)
    
    suspend fun updateAccident(accident: Accident) =
        accidentDao.updateAccident(accident)
    
    suspend fun deleteAccident(accident: Accident) =
        accidentDao.deleteAccident(accident)
    
    suspend fun deleteAccidentsByCarId(carId: Long) =
        accidentDao.deleteAccidentsByCarId(carId)
}
