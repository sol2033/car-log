package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Во сколько ДТП обошлось владельцу. Эта формула — единственный источник суммы, которой
 * авария попадает в статистику, поэтому её границы проверяются отдельно.
 */
class AccidentCostTest {

    private fun accident(
        repairCost: Double? = null,
        compensationPaid: Double? = null,
        osagoPayout: Double? = null,
        kaskoPayout: Double? = null,
        culpritPayout: Double? = null
    ) = Accident(
        id = 1,
        carId = 1,
        date = 0L,
        mileage = 90_000,
        location = null,
        damageDescription = "Бампер",
        severity = "Средняя",
        isUserAtFault = false,
        osagoPayout = osagoPayout,
        kaskoPayout = kaskoPayout,
        culpritPayout = culpritPayout,
        installedPartIds = null,
        repairCost = repairCost,
        compensationPaid = compensationPaid,
        photosPaths = null,
        documentPath = null,
        notes = null
    )

    @Test
    fun `без выплат в расход идёт стоимость ремонта`() {
        assertEquals(50_000.0, accident(repairCost = 50_000.0).netCost, 0.001)
    }

    @Test
    fun `компенсация пострадавшему складывается с ремонтом`() {
        val value = accident(repairCost = 20_000.0, compensationPaid = 30_000.0).netCost

        assertEquals(50_000.0, value, 0.001)
    }

    @Test
    fun `полученные выплаты вычитаются`() {
        val value = accident(
            repairCost = 100_000.0,
            osagoPayout = 40_000.0,
            kaskoPayout = 10_000.0,
            culpritPayout = 5_000.0
        ).netCost

        assertEquals(45_000.0, value, 0.001)
    }

    /**
     * Иначе одно щедро закрытое ДТП уменьшало бы стоимость заправок и обслуживания —
     * авария стала бы источником дохода.
     */
    @Test
    fun `выплата больше ремонта даёт ноль, а не минус`() {
        val value = accident(repairCost = 20_000.0, kaskoPayout = 100_000.0).netCost

        assertEquals(0.0, value, 0.001)
    }

    /** Ограничение действует на каждую аварию отдельно */
    @Test
    fun `щедрая выплата не съедает соседнюю аварию`() {
        val total = listOf(
            accident(repairCost = 20_000.0, kaskoPayout = 100_000.0),
            accident(repairCost = 30_000.0)
        ).sumOf { it.netCost }

        assertEquals(30_000.0, total, 0.001)
    }

    /**
     * Запись, заведённая до появления поля: компенсация там null, и расход обязан
     * остаться ровно таким, каким был до обновления.
     */
    @Test
    fun `запись без компенсации считается как раньше`() {
        assertEquals(50_000.0, accident(repairCost = 50_000.0, compensationPaid = null).netCost, 0.001)
    }

    @Test
    fun `пустое ДТП ничего не стоит`() {
        assertEquals(0.0, accident().netCost, 0.001)
        assertEquals(0.0, accident().receivedPayouts, 0.001)
    }
}
