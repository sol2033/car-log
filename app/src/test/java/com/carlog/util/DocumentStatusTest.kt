package com.carlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Светофор документов (§4.7 бизнес-логики): 30 дней — жёлтый, 7 дней или просрочка — красный */
class DocumentStatusTest {

    /** «Сейчас» — полдень: граница суток не должна влиять на тест */
    private val now = DateUtils.startOfDayMillis(LocalDate.of(2026, 6, 15)) + TimeUnit.HOURS.toMillis(12)

    private fun inDays(days: Long) = DateUtils.startOfDayMillis(LocalDate.of(2026, 6, 15).plusDays(days)) +
        TimeUnit.HOURS.toMillis(3)

    @Test
    fun `больше 30 дней — зелёный`() {
        assertEquals(DocumentStatus.Status.NORMAL, DocumentStatus.calculateStatus(inDays(60), now).status)
    }

    @Test
    fun `ровно 30 дней — жёлтый`() {
        assertEquals(DocumentStatus.Status.WARNING, DocumentStatus.calculateStatus(inDays(30), now).status)
    }

    @Test
    fun `ровно 7 дней — красный`() {
        assertEquals(DocumentStatus.Status.CRITICAL, DocumentStatus.calculateStatus(inDays(7), now).status)
    }

    @Test
    fun `просроченный документ — красный с отрицательным остатком`() {
        val status = DocumentStatus.calculateStatus(inDays(-10), now)

        assertEquals(DocumentStatus.Status.CRITICAL, status.status)
        assertTrue("остаток должен быть отрицательным", status.remainingDays < 0)
    }

    /**
     * Регрессия: дни считались вычитанием миллисекунд. Документ, истекающий завтра в 03:00
     * (так сохранял дату календарь), при «сейчас» = полдень показывал «осталось 0 дней».
     */
    @Test
    fun `дни считаются календарные, а не по 24 часа`() {
        assertEquals(1, DocumentStatus.calculateStatus(inDays(1), now).remainingDays)
        assertEquals(0, DocumentStatus.calculateStatus(inDays(0), now).remainingDays)
        assertEquals(-1, DocumentStatus.calculateStatus(inDays(-1), now).remainingDays)
    }
}
