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

    /**
     * Пересчитывает расход топлива (л/100км) всех заправок машины по алгоритму
     * «между двумя полными баками»:
     * расход полного бака = (его литры + Σ литров частичных заправок после предыдущего
     * полного бака) / (пробег текущего − пробег предыдущего полного) × 100.
     *
     * У частичных заправок и у первого полного бака (нет точки отсчёта) расход = null.
     * Заправка с `isConsumptionResetPoint` рвёт цепочку: перед ней была пропущенная
     * заправка, поэтому её собственный расход посчитать не из чего, а отсчёт начинается
     * с неё заново (§4.5 бизнес-логики).
     * Вызывается после каждой мутации заправок: это чинит и соседние записи
     * (например, при добавлении заправки задним числом или удалении промежуточной).
     */
    suspend fun recalculateFuelConsumption(carId: Long) {
        val refuelings = refuelingDao.getRefuelingsByCarIdSortedByMileageOnce(carId)
        val updated = mutableListOf<Refueling>()

        var previousFullTank: Refueling? = null
        var partialLitersSinceFullTank = 0.0

        for (refueling in refuelings) {
            if (refueling.isConsumptionResetPoint) {
                // Всё, что было до пропущенной заправки, точкой отсчёта служить не может
                previousFullTank = null
                partialLitersSinceFullTank = 0.0
            }

            if (refueling.isFullTank) {
                var newConsumption: Double? = null
                val previous = previousFullTank
                if (previous != null) {
                    val distance = refueling.mileage - previous.mileage
                    if (distance > 0) {
                        newConsumption =
                            (refueling.liters + partialLitersSinceFullTank) / distance * 100
                    }
                }
                if (newConsumption != refueling.fuelConsumption) {
                    updated.add(refueling.copy(fuelConsumption = newConsumption))
                }
                previousFullTank = refueling
                partialLitersSinceFullTank = 0.0
            } else {
                // Частичная заправка: собственного расхода нет, литры уйдут в следующий полный бак
                if (refueling.fuelConsumption != null) {
                    updated.add(refueling.copy(fuelConsumption = null))
                }
                partialLitersSinceFullTank += refueling.liters
            }
        }

        if (updated.isNotEmpty()) {
            refuelingDao.updateRefuelings(updated)
        }
    }

    /**
     * Убирает точку отсчёта расхода: средний расход снова считается по всей истории.
     * Расход соседних записей после этого меняется — пересчёт вызывает вызывающий код.
     */
    suspend fun clearConsumptionResetPoints(carId: Long) {
        refuelingDao.clearConsumptionResetPoints(carId, System.currentTimeMillis())
    }

    /** Разовый пересчёт по всем машинам (миграция данных на новую формулу) */
    suspend fun recalculateFuelConsumptionForAllCars() {
        refuelingDao.getCarIdsWithRefuelings().forEach { recalculateFuelConsumption(it) }
    }
}
