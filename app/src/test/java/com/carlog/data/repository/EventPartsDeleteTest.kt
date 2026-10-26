package com.carlog.data.repository

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Part
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Удаление обслуживания или ДТП удаляет и созданные ими запчасти.
 *
 * Регрессия: запчасти оставались в модуле «Запчасти», а в статистике, потеряв событие,
 * считались «отдельными» — деньги удалённой записи возвращались в «Ремонты».
 */
class EventPartsDeleteTest {

    private val partDao = mockk<PartDao>(relaxed = true)
    private val breakdownDao = mockk<BreakdownDao>(relaxed = true)
    private val consumableDao = mockk<ConsumableDao>(relaxed = true)
    private val accidentDao = mockk<AccidentDao>(relaxed = true)

    private fun part(id: Long, carId: Long = 1) = Part(
        id = id, carId = carId, name = "Деталь $id", installDate = 0, installMileage = 50_000,
        installationType = "Сервис", price = 1_000.0, createdAt = 0, updatedAt = 0
    )

    private val breakdown = Breakdown(
        id = 7, carId = 1, title = "Ремонт", description = "", breakdownDate = 0,
        breakdownMileage = 50_000, installedPartIds = listOf(10L, 11L),
        partsCost = 2_000.0, totalCost = 2_000.0, createdAt = 0, updatedAt = 0
    )

    private val accident = Accident(
        id = 3, carId = 1, date = 0, mileage = 50_000, location = null,
        damageDescription = "Бампер", severity = "Незначительная", isUserAtFault = false,
        osagoPayout = null, kaskoPayout = null, culpritPayout = null,
        installedPartIds = listOf(20L), repairCost = 5_000.0,
        photosPaths = null, documentPath = null, notes = null, createdAt = 0, updatedAt = 0
    )

    @Test
    fun `удаление обслуживания удаляет его запчасти`() = runTest {
        coEvery { partDao.getPartsByIds(listOf(10L, 11L)) } returns listOf(part(10), part(11))

        BreakdownRepository(breakdownDao, consumableDao, partDao).deleteBreakdown(breakdown)

        coVerify { partDao.deletePart(part(10)) }
        coVerify { partDao.deletePart(part(11)) }
        coVerify { breakdownDao.deleteBreakdown(breakdown) }
    }

    @Test
    fun `запчасть другой машины по повисшей ссылке не трогается`() = runTest {
        coEvery { partDao.getPartsByIds(listOf(10L, 11L)) } returns listOf(part(10), part(11, carId = 2))

        BreakdownRepository(breakdownDao, consumableDao, partDao).deleteBreakdown(breakdown)

        coVerify { partDao.deletePart(part(10)) }
        coVerify(exactly = 0) { partDao.deletePart(part(11, carId = 2)) }
    }

    @Test
    fun `событие без запчастей списком запчасти не ищет`() = runTest {
        BreakdownRepository(breakdownDao, consumableDao, partDao)
            .deleteBreakdown(breakdown.copy(installedPartIds = null))

        coVerify(exactly = 0) { partDao.getPartsByIds(any()) }
        coVerify(exactly = 0) { partDao.deletePart(any()) }
    }

    @Test
    fun `удаление ДТП удаляет его запчасти`() = runTest {
        coEvery { partDao.getPartsByIds(listOf(20L)) } returns listOf(part(20))

        AccidentRepository(accidentDao, partDao).deleteAccident(accident)

        coVerify { partDao.deletePart(part(20)) }
        coVerify { accidentDao.deleteAccident(accident) }
    }
}
