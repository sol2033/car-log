package com.carlog.data.repository

import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Consumable
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Удаление ТО или расходника, после которого категория осталась бы без текущей установки:
 * пользователь выбирает, вернуть ли предыдущие расходники (§4.4 бизнес-логики).
 *
 * Раньше выбора не было: категория просто пустела, а «Проверка данных» такой случай не находит.
 */
class DeleteRestorePredecessorTest {

    private val consumableDao = mockk<ConsumableDao>(relaxed = true)
    private val breakdownDao = mockk<BreakdownDao>(relaxed = true)
    private val partDao = mockk<PartDao>(relaxed = true)

    private fun consumable(
        id: Long,
        isActive: Boolean,
        installationMileage: Int,
        replacementMileage: Int? = null,
        category: String = "Масло в двигателе"
    ) = Consumable(
        id = id, carId = 1, category = category, manufacturer = null, articleNumber = null,
        installationMileage = installationMileage, installationDate = installationMileage.toLong(),
        linkedMaintenanceId = null, replacementMileage = replacementMileage,
        replacementDate = replacementMileage?.toLong(), cost = 1_000.0, isInstalledAtService = false,
        serviceCost = null, volume = null, replacementIntervalMileage = null,
        replacementIntervalDays = null, isActive = isActive, notes = null, createdAt = 0, updatedAt = 0
    )

    private val oldOil = consumable(id = 1, isActive = false, installationMileage = 10_000, replacementMileage = 20_000)
    private val newOil = consumable(id = 2, isActive = true, installationMileage = 20_000).copy(linkedMaintenanceId = 7)

    private val maintenance = Breakdown(
        id = 7, carId = 1, title = "ТО", description = "", breakdownDate = 20_000,
        breakdownMileage = 20_000, maintenanceType = "SCHEDULED_SERVICE",
        linkedConsumableIds = listOf(2L), partsCost = 1_000.0, totalCost = 1_000.0
    )

    init {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(listOf(oldOil, newOil))
    }

    @Test
    fun `план удаления ТО называет число расходников и кого можно вернуть`() = runTest {
        val plan = BreakdownRepository(breakdownDao, consumableDao, partDao).deletePlan(maintenance)

        assertEquals(1, plan.removedCount)
        assertEquals(listOf(1L), plan.restorable.map { it.predecessor.id })
    }

    @Test
    fun `удаление ТО с возвратом делает предыдущее масло текущим`() = runTest {
        BreakdownRepository(breakdownDao, consumableDao, partDao).deleteBreakdown(maintenance, restorePredecessors = true)

        coVerify { consumableDao.deleteConsumablesByIds(listOf(2L)) }
        coVerify {
            consumableDao.updateConsumable(match {
                it.id == 1L && it.isActive && it.replacementMileage == null && it.replacementDate == null
            })
        }
        coVerify { breakdownDao.deleteBreakdown(maintenance) }
    }

    @Test
    fun `удаление ТО без возврата оставляет категорию пустой`() = runTest {
        BreakdownRepository(breakdownDao, consumableDao, partDao).deleteBreakdown(maintenance, restorePredecessors = false)

        coVerify { consumableDao.deleteConsumablesByIds(listOf(2L)) }
        coVerify(exactly = 0) { consumableDao.updateConsumable(any()) }
    }

    @Test
    fun `удаление расходника с его экрана с возвратом`() = runTest {
        ConsumableRepository(consumableDao).deleteConsumable(newOil, restorePredecessor = true)

        coVerify { consumableDao.deleteConsumable(newOil) }
        coVerify { consumableDao.updateConsumable(match { it.id == 1L && it.isActive }) }
    }

    @Test
    fun `удаление расходника без возврата`() = runTest {
        ConsumableRepository(consumableDao).deleteConsumable(newOil, restorePredecessor = false)

        coVerify { consumableDao.deleteConsumable(newOil) }
        coVerify(exactly = 0) { consumableDao.updateConsumable(any()) }
    }
}
