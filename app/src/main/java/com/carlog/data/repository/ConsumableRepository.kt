package com.carlog.data.repository

import com.carlog.data.local.dao.ConsumableDao
import com.carlog.domain.model.Consumable
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsumableRepository @Inject constructor(
    private val consumableDao: ConsumableDao
) {
    
    fun getConsumablesByCarId(carId: Long): Flow<List<Consumable>> {
        return consumableDao.getConsumablesByCarId(carId)
    }
    
    fun getActiveConsumablesByCarId(carId: Long): Flow<List<Consumable>> {
        return consumableDao.getActiveConsumablesByCarId(carId)
    }
    
    fun getConsumablesByCategory(carId: Long, category: String): Flow<List<Consumable>> {
        return consumableDao.getConsumablesByCategory(carId, category)
    }
    
    fun getConsumableById(consumableId: Long): Flow<Consumable?> {
        return consumableDao.getConsumableById(consumableId)
    }
    
    suspend fun insertConsumable(consumable: Consumable): Long {
        return consumableDao.insertConsumable(consumable)
    }
    
    suspend fun updateConsumable(consumable: Consumable) {
        consumableDao.updateConsumable(consumable)
    }
    
    suspend fun deleteConsumable(consumable: Consumable) {
        consumableDao.deleteConsumable(consumable)
    }
    
    suspend fun deleteConsumablesByCarId(carId: Long) {
        consumableDao.deleteConsumablesByCarId(carId)
    }
}
