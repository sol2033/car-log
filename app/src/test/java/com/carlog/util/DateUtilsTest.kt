package com.carlog.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Регрессия на баг с часовым поясом: раньше дата события получалась делением
 * epoch millis на длительность суток, то есть по UTC, тогда как границы периодов
 * статистики считаются от локальной полуночи.
 */
class DateUtilsTest {

    private fun withTimeZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `ночное событие относится к своим локальным суткам, а не к предыдущим по UTC`() {
        withTimeZone("Europe/Moscow") {
            // 1 июля 2026, 01:30 по Москве = 30 июня 22:30 UTC
            val millis = LocalDate.of(2026, 7, 1)
                .atTime(1, 30)
                .atZone(ZoneId.of("Europe/Moscow"))
                .toInstant()
                .toEpochMilli()

            assertEquals(LocalDate.of(2026, 7, 1), DateUtils.toLocalDate(millis))
            // Старая формула millis / 86_400_000 дала бы 30 июня — событие уезжало в прошлый месяц
            assertEquals(LocalDate.of(2026, 6, 30), LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000)))
        }
    }

    @Test
    fun `граница суток попадает в диапазон того же дня`() {
        withTimeZone("Europe/Moscow") {
            val day = LocalDate.of(2026, 7, 1)
            val start = DateUtils.startOfDayMillis(day)
            val end = DateUtils.endOfDayMillis(day)

            assertEquals(day, DateUtils.toLocalDate(start))
            assertEquals(day, DateUtils.toLocalDate(end))
            assert(start < end)
        }
    }

    @Test
    fun `дата, выбранная в календаре, не выпадает из фильтра месяца в западном поясе`() {
        withTimeZone("America/New_York") {
            val firstOfMonth = LocalDate.of(2026, 7, 1)
            val periodStart = DateUtils.startOfDayMillis(firstOfMonth)
            val recordAtNoon = firstOfMonth
                .atTime(12, 0)
                .atZone(ZoneId.of("America/New_York"))
                .toInstant()
                .toEpochMilli()

            assert(recordAtNoon >= periodStart)
            assertEquals(firstOfMonth, DateUtils.toLocalDate(recordAtNoon))
        }
    }
}
