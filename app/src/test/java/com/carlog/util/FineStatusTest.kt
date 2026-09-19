package com.carlog.util

import com.carlog.domain.model.Fine
import com.carlog.domain.model.FineViolationTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Светофор штрафа и правило «в деньги идёт только фактически уплаченное» (§4.8 бизнес-логики).
 *
 * Даты считаются от локальной полуночи через [DateUtils]: тест на границе суток — та самая
 * причина, по которой здесь не вычитаются миллисекунды.
 */
class FineStatusTest {

    private fun millisAt(daysFromToday: Long): Long =
        DateUtils.endOfDayMillis(LocalDate.now().plusDays(daysFromToday))

    private val now = System.currentTimeMillis()

    // === Светофор ===

    @Test
    fun `оплаченный штраф выходит из светофора`() {
        val info = FineStatus.calculateStatus(isPaid = true, discountUntil = millisAt(-30), now = now)

        assertEquals(FineStatus.Status.PAID, info.status)
        assertNull("У оплаченного отсчитывать нечего", info.remainingDays)
    }

    @Test
    fun `без льготного срока штраф зелёный и без счётчика`() {
        val info = FineStatus.calculateStatus(isPaid = false, discountUntil = null, now = now)

        assertEquals(FineStatus.Status.NORMAL, info.status)
        assertNull(info.remainingDays)
    }

    @Test
    fun `за неделю с лишним до конца скидки — зелёный`() {
        val info = FineStatus.calculateStatus(isPaid = false, discountUntil = millisAt(8), now = now)

        assertEquals(FineStatus.Status.NORMAL, info.status)
        assertEquals(8, info.remainingDays)
    }

    @Test
    fun `ровно на пороге предупреждения — жёлтый`() {
        val info = FineStatus.calculateStatus(
            isPaid = false,
            discountUntil = millisAt(FineStatus.WARNING_DAYS.toLong()),
            now = now
        )

        assertEquals(FineStatus.Status.WARNING, info.status)
        assertEquals(FineStatus.WARNING_DAYS, info.remainingDays)
    }

    @Test
    fun `последний день скидки ещё жёлтый, а не красный`() {
        val info = FineStatus.calculateStatus(isPaid = false, discountUntil = millisAt(0), now = now)

        assertEquals(FineStatus.Status.WARNING, info.status)
        assertEquals(0, info.remainingDays)
    }

    @Test
    fun `вчерашний срок — красный с отрицательным счётчиком`() {
        val info = FineStatus.calculateStatus(isPaid = false, discountUntil = millisAt(-1), now = now)

        assertEquals(FineStatus.Status.EXPIRED, info.status)
        assertEquals(-1, info.remainingDays)
    }

    // === Льготный срок по умолчанию ===

    @Test
    fun `срок по умолчанию — 30 дней от даты постановления`() {
        val decision = DateUtils.startOfDayMillis(LocalDate.of(2026, 1, 10))

        val deadline = DateUtils.toLocalDate(FineStatus.defaultDiscountUntil(decision))

        assertEquals(LocalDate.of(2026, 2, 9), deadline)
    }

    @Test
    fun `срок по умолчанию переживает конец года`() {
        val decision = DateUtils.startOfDayMillis(LocalDate.of(2026, 12, 20))

        assertEquals(LocalDate.of(2027, 1, 19), FineStatus.discountDeadline(decision))
    }

    // === Деньги ===

    private fun fine(
        amount: Double,
        isPaid: Boolean = false,
        paidAmount: Double? = null
    ) = Fine(
        carId = 1,
        date = now,
        amount = amount,
        violationType = FineViolationTypes.SPEEDING,
        isPaid = isPaid,
        paidAmount = paidAmount
    )

    @Test
    fun `неоплаченный штраф не считается тратой`() {
        assertEquals(0.0, fine(amount = 5_000.0).paidCost, 0.01)
    }

    @Test
    fun `оплаченный без указания суммы считается полностью`() {
        assertEquals(5_000.0, fine(amount = 5_000.0, isPaid = true).paidCost, 0.01)
    }

    @Test
    fun `оплаченный со скидкой считается по фактической сумме`() {
        val paid = fine(amount = 5_000.0, isPaid = true, paidAmount = 2_500.0)

        assertEquals(2_500.0, paid.paidCost, 0.01)
    }

    @Test
    fun `снятая отметка об оплате обнуляет трату, даже если сумма осталась`() {
        // paidAmount без isPaid встречается у записей, где отметку сняли:
        // деньги не должны воскреснуть в статистике
        val unpaid = fine(amount = 5_000.0, isPaid = false, paidAmount = 2_500.0)

        assertEquals(0.0, unpaid.paidCost, 0.01)
    }

    // === Название нарушения ===

    @Test
    fun `у типа Другое показывается своя формулировка`() {
        val custom = fine(amount = 500.0).copy(
            violationType = FineViolationTypes.OTHER,
            customViolation = "Езда по обочине"
        )

        assertEquals("Езда по обочине", custom.displayViolation)
    }

    @Test
    fun `пустая формулировка у Другого откатывается к самому типу`() {
        val custom = fine(amount = 500.0).copy(
            violationType = FineViolationTypes.OTHER,
            customViolation = "   "
        )

        assertEquals(FineViolationTypes.OTHER, custom.displayViolation)
    }
}
