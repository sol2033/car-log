package com.carlog.util

import com.carlog.domain.model.Consumable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Светофор расходников (§4.4 бизнес-логики): берётся худший статус из двух интервалов —
 * по пробегу и по времени.
 */
class ConsumableStatusTest {

    private fun consumable(
        installationMileage: Int = 0,
        daysAgo: Long = 0,
        intervalMileage: Int? = null,
        intervalDays: Int? = null,
        isActive: Boolean = true
    ) = Consumable(
        id = 1,
        carId = 1,
        category = "Масло в двигателе",
        manufacturer = null,
        articleNumber = null,
        installationMileage = installationMileage,
        installationDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysAgo),
        replacementMileage = null,
        replacementDate = null,
        cost = 1000.0,
        isInstalledAtService = false,
        serviceCost = null,
        volume = null,
        replacementIntervalMileage = intervalMileage,
        replacementIntervalDays = intervalDays,
        isActive = isActive,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun `свежий расходник — зелёный`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000),
            currentMileage = 102_000
        )

        assertEquals(ConsumableStatus.Status.NORMAL, status.status)
        assertEquals(8_000, status.remainingMileage)
    }

    @Test
    fun `половина ресурса по пробегу — жёлтый`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000),
            currentMileage = 105_000
        )

        assertEquals(ConsumableStatus.Status.WARNING, status.status)
    }

    @Test
    fun `осталось не больше 500 км — красный, даже если ресурс не исчерпан`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000),
            currentMileage = 109_500
        )

        assertEquals(ConsumableStatus.Status.CRITICAL, status.status)
        assertEquals(500, status.remainingMileage)
    }

    @Test
    fun `просроченный по пробегу — красный с отрицательным остатком`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000),
            currentMileage = 112_000
        )

        assertEquals(ConsumableStatus.Status.CRITICAL, status.status)
        assertEquals(-2_000, status.remainingMileage)
    }

    @Test
    fun `берётся худший из интервалов — по пробегу зелёный, по времени красный`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(
                installationMileage = 100_000,
                daysAgo = 360,
                intervalMileage = 10_000,
                intervalDays = 365
            ),
            currentMileage = 100_500
        )

        assertEquals(ConsumableStatus.Status.CRITICAL, status.status)
    }

    @Test
    fun `неактивный расходник статуса не имеет`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000, isActive = false),
            currentMileage = 200_000
        )

        assertEquals(ConsumableStatus.Status.NORMAL, status.status)
        assertNull(status.remainingMileage)
    }

    /** Регрессия: нулевой интервал давал деление на ноль и вечный CRITICAL */
    @Test
    fun `нулевой интервал не задаёт ресурс и не делает расходник просроченным`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 0, intervalDays = 0),
            currentMileage = 150_000
        )

        assertEquals(ConsumableStatus.Status.NORMAL, status.status)
        assertNull(status.remainingMileage)
        assertNull(status.remainingDays)
    }

    @Test
    fun `прогресс не выходит за границы ноль-один`() {
        val status = ConsumableStatus.calculateStatus(
            consumable(installationMileage = 100_000, intervalMileage = 10_000),
            currentMileage = 500_000
        )

        assertEquals(1f, status.progressPercent, 0.001f)
    }
}
