package com.carlog.data.repository

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Part
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Удаление запчасти обязано убрать ссылку на неё из событий: внешнего ключа у
 * `installedPartIds` нет, и повисшая ссылка отравляла форму обслуживания — список
 * запчастей включался по ней, выходил пустым, и запись переставала сохраняться.
 */
class PartRepositoryTest {

    private val partDao = mockk<PartDao>(relaxed = true)
    private val breakdownDao = mockk<BreakdownDao>(relaxed = true)
    private val accidentDao = mockk<AccidentDao>(relaxed = true)

    private val repository = PartRepository(partDao, breakdownDao, accidentDao)

    private val part = Part(
        id = 7,
        carId = 1,
        name = "Рычаг",
        installDate = 1_000,
        installMileage = 40_000,
        installationType = "Сервис",
        price = 5_000.0,
        photosPaths = null
    )

    private fun breakdown(id: Long, partIds: List<Long>?) = Breakdown(
        id = id,
        carId = 1,
        maintenanceType = "REPAIR",
        title = "Замена рычага",
        description = "стук спереди",
        breakdownDate = 1_000,
        breakdownMileage = 40_000,
        installedPartIds = partIds,
        isServiceMaintenance = true,
        partsCost = 5_000.0,
        totalCost = 5_000.0,
        isWarrantyRepair = false
    )

    private fun accident(id: Long, partIds: List<Long>?) = Accident(
        id = id,
        carId = 1,
        date = 1_000,
        mileage = 40_000,
        location = null,
        damageDescription = "бампер",
        severity = "Незначительная",
        isUserAtFault = false,
        osagoPayout = null,
        kaskoPayout = null,
        culpritPayout = null,
        installedPartIds = partIds,
        repairCost = 12_000.0,
        photosPaths = null,
        documentPath = null,
        notes = null
    )

    @Test
    fun `удаление запчасти убирает ссылку на неё из обслуживания`() = runTest {
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(id = 10, partIds = listOf(7, 8))))
        every { accidentDao.getAccidentsByCarId(1) } returns flowOf(emptyList())

        val saved = slot<Breakdown>()
        repository.deletePart(part)

        coVerify { breakdownDao.updateBreakdown(capture(saved)) }
        assertEquals(listOf(8L), saved.captured.installedPartIds)
    }

    @Test
    fun `событие без оставшихся запчастей переходит на общую сумму`() = runTest {
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(id = 10, partIds = listOf(7))))
        every { accidentDao.getAccidentsByCarId(1) } returns flowOf(emptyList())

        val saved = slot<Breakdown>()
        repository.deletePart(part)

        coVerify { breakdownDao.updateBreakdown(capture(saved)) }
        assertNull("пустой список — это режим «общая сумма», а не пустой список позиций",
            saved.captured.installedPartIds)
        assertEquals("деньги события не трогаем", 5_000.0, saved.captured.partsCost, 0.0001)
    }

    @Test
    fun `ссылка убирается и из ДТП`() = runTest {
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(emptyList())
        every { accidentDao.getAccidentsByCarId(1) } returns
            flowOf(listOf(accident(id = 20, partIds = listOf(7, 9))))

        val saved = slot<Accident>()
        repository.deletePart(part)

        coVerify { accidentDao.updateAccident(capture(saved)) }
        assertEquals(listOf(9L), saved.captured.installedPartIds)
    }

    @Test
    fun `чужие события не переписываются`() = runTest {
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(id = 10, partIds = listOf(8, 9)), breakdown(id = 11, partIds = null)))
        every { accidentDao.getAccidentsByCarId(1) } returns flowOf(emptyList())

        repository.deletePart(part)

        coVerify(exactly = 0) { breakdownDao.updateBreakdown(any()) }
        coVerify { partDao.deletePart(part) }
    }
}
