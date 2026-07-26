package com.carlog.data.repository

import com.carlog.data.local.dao.RefuelingDao
import com.carlog.domain.model.Refueling
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Расход топлива считается между двумя полными баками с учётом частичных заправок
 * между ними (§4.5 бизнес-логики, алгоритм как у Fuelio).
 */
class FuelConsumptionTest {

    private val dao = mockk<RefuelingDao>(relaxed = true)
    private val repository = RefuelingRepository(dao)

    private fun refueling(
        id: Long,
        mileage: Int,
        liters: Double,
        isFullTank: Boolean,
        consumption: Double? = null
    ) = Refueling(
        id = id,
        carId = 1,
        date = mileage.toLong(), // порядок дат совпадает с порядком пробега
        mileage = mileage,
        liters = liters,
        fuelType = "АИ-95",
        isFullTank = isFullTank,
        fuelConsumption = consumption
    )

    private suspend fun recalculate(refuelings: List<Refueling>): List<Refueling> {
        val updated = slot<List<Refueling>>()
        coEvery { dao.getRefuelingsByCarIdSortedByMileageOnce(1) } returns refuelings
        coEvery { dao.updateRefuelings(capture(updated)) } just Runs

        repository.recalculateFuelConsumption(1)

        return if (updated.isCaptured) updated.captured else emptyList()
    }

    @Test
    fun `у первого полного бака расхода нет — нет точки отсчёта`() = runTest {
        val updated = recalculate(
            listOf(refueling(1, 100_000, 40.0, isFullTank = true))
        )

        assertTrue("нечего обновлять", updated.isEmpty())
    }

    @Test
    fun `расход между двумя полными баками`() = runTest {
        val updated = recalculate(
            listOf(
                refueling(1, 100_000, 40.0, isFullTank = true),
                refueling(2, 100_500, 45.0, isFullTank = true)
            )
        )

        // 45 л на 500 км = 9 л/100км
        val second = updated.single { it.id == 2L }
        assertEquals(9.0, second.fuelConsumption!!, 0.0001)
    }

    @Test
    fun `литры частичных заправок уходят в следующий полный бак`() = runTest {
        val updated = recalculate(
            listOf(
                refueling(1, 100_000, 40.0, isFullTank = true),
                refueling(2, 100_200, 10.0, isFullTank = false),
                refueling(3, 100_500, 35.0, isFullTank = true)
            )
        )

        // (35 + 10) л на 500 км = 9 л/100км
        val third = updated.single { it.id == 3L }
        assertEquals(9.0, third.fuelConsumption!!, 0.0001)
    }

    @Test
    fun `у частичной заправки собственного расхода нет`() = runTest {
        val updated = recalculate(
            listOf(
                refueling(1, 100_000, 40.0, isFullTank = true),
                refueling(2, 100_200, 10.0, isFullTank = false, consumption = 7.5),
                refueling(3, 100_500, 35.0, isFullTank = true)
            )
        )

        val partial = updated.single { it.id == 2L }
        assertNull("у частичной заправки расход должен обнулиться", partial.fuelConsumption)
    }

    @Test
    fun `нулевая дистанция между полными баками не даёт расход`() = runTest {
        val updated = recalculate(
            listOf(
                refueling(1, 100_000, 40.0, isFullTank = true),
                refueling(2, 100_000, 20.0, isFullTank = true, consumption = 5.0)
            )
        )

        val second = updated.single { it.id == 2L }
        assertNull("деления на ноль быть не должно", second.fuelConsumption)
    }

    @Test
    fun `уже посчитанные записи не переписываются`() = runTest {
        val updated = recalculate(
            listOf(
                refueling(1, 100_000, 40.0, isFullTank = true),
                refueling(2, 100_500, 45.0, isFullTank = true, consumption = 9.0)
            )
        )

        assertTrue("значение не изменилось — обновлять нечего", updated.isEmpty())
    }
}
