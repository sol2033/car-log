package com.carlog.data.repository

import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.domain.model.Breakdown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakdownRepository @Inject constructor(
    private val breakdownDao: BreakdownDao,
    private val consumableDao: ConsumableDao
) {
    fun getBreakdownsByCarId(carId: Long): Flow<List<Breakdown>> =
        breakdownDao.getBreakdownsByCarId(carId)
    
    fun getBreakdownById(breakdownId: Long): Flow<Breakdown?> =
        breakdownDao.getBreakdownById(breakdownId)
    
    suspend fun insertBreakdown(breakdown: Breakdown): Long =
        breakdownDao.insertBreakdown(breakdown)
    
    suspend fun updateBreakdown(breakdown: Breakdown) =
        breakdownDao.updateBreakdown(breakdown)
    
    suspend fun deleteBreakdown(breakdown: Breakdown) {
        // CASCADE delete: удаляем все связанные расходники
        breakdown.linkedConsumableIds?.forEach { consumableId ->
            consumableDao.getConsumableById(consumableId).firstOrNull()?.let { consumable ->
                consumableDao.deleteConsumable(consumable)
            }
        }
        breakdownDao.deleteBreakdown(breakdown)
    }
    
    suspend fun deleteBreakdownsByCarId(carId: Long) {
        // Получаем все breakdowns для удаления связанных расходников
        val breakdowns = breakdownDao.getBreakdownsByCarId(carId).firstOrNull() ?: emptyList()
        breakdowns.forEach { breakdown ->
            breakdown.linkedConsumableIds?.forEach { consumableId ->
                consumableDao.getConsumableById(consumableId).firstOrNull()?.let { consumable ->
                    consumableDao.deleteConsumable(consumable)
                }
            }
        }
        breakdownDao.deleteBreakdownsByCarId(carId)
    }
    
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
