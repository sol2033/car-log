package com.carlog.data.repository

import kotlinx.coroutines.flow.firstOrNull
import com.carlog.domain.model.ConsumableChainRepair
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

    /** Что спросить перед удалением расходника с его экрана: можно ли вернуть предыдущий */
    suspend fun deletePlan(consumable: Consumable): ConsumableDeletePlan {
        val all = consumableDao.getConsumablesByCarId(consumable.carId).firstOrNull().orEmpty()
        return ConsumableDeletePlan(
            removedCount = 1,
            restorable = ConsumableChainRepair.restorableOnDelete(all, listOf(consumable))
        )
    }

    /**
     * Удаление с экрана расходника. [restorePredecessor] — вернуть в активные предыдущую
     * установку категории, если без удаляемого она осталась бы пустой.
     */
    suspend fun deleteConsumable(consumable: Consumable, restorePredecessor: Boolean) {
        val toRestore = if (restorePredecessor) deletePlan(consumable).restorable else emptyList()
        consumableDao.deleteConsumable(consumable)
        val now = System.currentTimeMillis()
        toRestore.forEach { consumableDao.updateConsumable(ConsumableChainRepair.reactivated(it.predecessor, now)) }
    }
    
    suspend fun deleteConsumablesByCarId(carId: Long) {
        consumableDao.deleteConsumablesByCarId(carId)
    }
}
