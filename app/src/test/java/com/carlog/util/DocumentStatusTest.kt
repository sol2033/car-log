package com.carlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Светофор документов (§4.7 бизнес-логики): 30 дней — жёлтый, 7 дней или просрочка — красный */
class DocumentStatusTest {

    private fun inDays(days: Long) =
        System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days) + TimeUnit.HOURS.toMillis(1)

    @Test
    fun `больше 30 дней — зелёный`() {
        assertEquals(DocumentStatus.Status.NORMAL, DocumentStatus.calculateStatus(inDays(60)).status)
    }

    @Test
    fun `ровно 30 дней — жёлтый`() {
        assertEquals(DocumentStatus.Status.WARNING, DocumentStatus.calculateStatus(inDays(30)).status)
    }

    @Test
    fun `ровно 7 дней — красный`() {
        assertEquals(DocumentStatus.Status.CRITICAL, DocumentStatus.calculateStatus(inDays(7)).status)
    }

    @Test
    fun `просроченный документ — красный с отрицательным остатком`() {
        val status = DocumentStatus.calculateStatus(inDays(-10))

        assertEquals(DocumentStatus.Status.CRITICAL, status.status)
        assertTrue("остаток должен быть отрицательным", status.remainingDays < 0)
    }
}
