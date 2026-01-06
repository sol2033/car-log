package com.carlog.data.repository

import com.carlog.data.local.dao.BreakdownDao
import com.carlog.domain.model.Breakdown
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakdownRepository @Inject constructor(
    private val breakdownDao: BreakdownDao
) {
    fun getBreakdownsByCarId(carId: Long): Flow<List<Breakdown>> =
        breakdownDao.getBreakdownsByCarId(carId)
    
    fun getBreakdownById(breakdownId: Long): Flow<Breakdown?> =
        breakdownDao.getBreakdownById(breakdownId)
    
    suspend fun insertBreakdown(breakdown: Breakdown): Long =
        breakdownDao.insertBreakdown(breakdown)
    
    suspend fun updateBreakdown(breakdown: Breakdown) =
        breakdownDao.updateBreakdown(breakdown)
    
    suspend fun deleteBreakdown(breakdown: Breakdown) =
        breakdownDao.deleteBreakdown(breakdown)
    
    suspend fun deleteBreakdownsByCarId(carId: Long) =
        breakdownDao.deleteBreakdownsByCarId(carId)
    
    suspend fun getBreakdownsCountByCarId(carId: Long): Int =
        breakdownDao.getBreakdownsCountByCarId(carId)
    
    suspend fun getTotalBreakdownsCostByCarId(carId: Long): Double =
        breakdownDao.getTotalBreakdownsCostByCarId(carId) ?: 0.0
    
    suspend fun getBreakdownsCostByPeriod(
        carId: Long,
        startDate: Long,
        endDate: Long
    ): Double =
        breakdownDao.getBreakdownsCostByPeriod(carId, startDate, endDate) ?: 0.0
}
