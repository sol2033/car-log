package com.carlog.domain.model

import com.carlog.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Помесячный расчёт платных дорог (§4.9 бизнес-логики): группировка по локальным месяцам
 * и среднее, в делитель которого входят и месяцы без проездов.
 */
class TollPaymentSummaryTest {

    private val today = LocalDate.of(2026, 10, 9)

    private fun payment(id: Long, date: LocalDate, amount: Double, hour: Int = 12) = TollPayment(
        id = id,
        carId = 1,
        date = DateUtils.startOfDayMillis(date) + hour * 3_600_000L,
        amount = amount
    )

    @Test
    fun `пустой список — нулевая сводка`() {
        val summary = TollPaymentSummary.summarize(emptyList(), today)

        assertEquals(0, summary.count)
        assertEquals(0.0, summary.total, 0.001)
        assertTrue(summary.months.isEmpty())
        assertEquals(0, summary.monthsSpan)
        assertEquals(0.0, summary.averagePerMonth, 0.001)
    }

    @Test
    fun `записи группируются по месяцам, новые месяцы и записи сверху`() {
        val summary = TollPaymentSummary.summarize(
            listOf(
                payment(1, LocalDate.of(2026, 9, 3), 300.0),
                payment(2, LocalDate.of(2026, 10, 1), 500.0),
                payment(3, LocalDate.of(2026, 10, 5), 250.0),
                payment(4, LocalDate.of(2026, 9, 20), 200.0)
            ),
            today
        )

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), summary.months.map { it.month })
        val october = summary.months[0]
        assertEquals(750.0, october.total, 0.001)
        assertEquals(2, october.count)
        assertEquals(listOf(3L, 2L), october.payments.map { it.id })
        assertEquals(500.0, summary.months[1].total, 0.001)
        assertEquals(4, summary.count)
        assertEquals(1_250.0, summary.total, 0.001)
    }

    /** Ночной проезд первого числа — уже новый месяц: месяц берётся по локальной дате */
    @Test
    fun `ночная запись первого числа попадает в свой месяц`() {
        val summary = TollPaymentSummary.summarize(
            listOf(payment(1, LocalDate.of(2026, 10, 1), 400.0, hour = 0)),
            today
        )

        assertEquals(YearMonth.of(2026, 10), summary.months.single().month)
    }

    @Test
    fun `проезды только в этом месяце — среднее равно итогу`() {
        val summary = TollPaymentSummary.summarize(
            listOf(payment(1, LocalDate.of(2026, 10, 2), 600.0)),
            today
        )

        assertEquals(1, summary.monthsSpan)
        assertEquals(600.0, summary.averagePerMonth, 0.001)
    }

    /**
     * Первый проезд в августе, сейчас октябрь: три месяца, хоть сентябрь и пустой.
     * Иначе редкие поездки выглядели бы ежемесячной тратой.
     */
    @Test
    fun `месяцы без проездов входят в делитель среднего`() {
        val summary = TollPaymentSummary.summarize(
            listOf(
                payment(1, LocalDate.of(2026, 8, 15), 900.0),
                payment(2, LocalDate.of(2026, 10, 2), 300.0)
            ),
            today
        )

        assertEquals(3, summary.monthsSpan)
        assertEquals(400.0, summary.averagePerMonth, 0.001)
        assertEquals("в списке только месяцы с проездами", 2, summary.months.size)
    }

    @Test
    fun `делитель считается до текущего месяца, даже если последний проезд давно`() {
        val summary = TollPaymentSummary.summarize(
            listOf(payment(1, LocalDate.of(2025, 11, 10), 1_200.0)),
            today
        )

        // Ноябрь 2025 — октябрь 2026 включительно
        assertEquals(12, summary.monthsSpan)
        assertEquals(100.0, summary.averagePerMonth, 0.001)
    }

    @Test
    fun `проезд будущей датой не сжимает делитель`() {
        val summary = TollPaymentSummary.summarize(
            listOf(
                payment(1, LocalDate.of(2026, 10, 1), 100.0),
                payment(2, LocalDate.of(2026, 12, 1), 200.0)
            ),
            today
        )

        assertEquals(3, summary.monthsSpan)
        assertEquals(100.0, summary.averagePerMonth, 0.001)
    }

    @Test
    fun `переход через год`() {
        val summary = TollPaymentSummary.summarize(
            listOf(payment(1, LocalDate.of(2025, 12, 31), 200.0)),
            LocalDate.of(2026, 1, 5)
        )

        assertEquals(2, summary.monthsSpan)
        assertEquals(100.0, summary.averagePerMonth, 0.001)
    }
}
