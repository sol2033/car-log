package com.carlog.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Перевод дат между записями и календарём Material3, который работает в UTC.
 *
 * Регрессия: значения передавались как есть. Запись, сделанная с 00:00 до 03:00 по Москве,
 * открывалась в календаре на предыдущем дне, а выбранный день сохранялся как 03:00 по Москве
 * (в западных поясах — как вечер предыдущего дня).
 */
class PickerDatesTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun useZone(id: String) = TimeZone.setDefault(TimeZone.getTimeZone(id))

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun utcMidnight(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `ночная запись по Москве открывается в календаре на своём дне`() {
        useZone("Europe/Moscow")
        val createdAtNight = local(2026, 10, 10, 1, 30)

        assertEquals(utcMidnight(2026, 10, 10), PickerDates.toPicker(createdAtNight))
    }

    @Test
    fun `выбранный день сохраняется этим днём с прежним временем суток`() {
        useZone("Europe/Moscow")
        val previous = local(2026, 10, 10, 14, 45)

        val saved = PickerDates.fromPicker(utcMidnight(2026, 10, 15), previous)

        assertEquals(local(2026, 10, 15, 14, 45), saved)
        assertEquals(LocalDate.of(2026, 10, 15), DateUtils.toLocalDate(saved))
    }

    /** В западных поясах полночь UTC — это вечер предыдущего дня: так раньше и сохранялось */
    @Test
    fun `в западном поясе выбранный день не уезжает на предыдущий`() {
        useZone("America/New_York")
        val previous = local(2026, 3, 1, 9, 0)

        val saved = PickerDates.fromPicker(utcMidnight(2026, 3, 20), previous)

        assertEquals(LocalDate.of(2026, 3, 20), DateUtils.toLocalDate(saved))
        assertEquals(utcMidnight(2026, 3, 20), PickerDates.toPicker(saved))
    }

    /** Старые записи, сохранённые прежним календарём (03:00 по Москве), показываются на своём дне */
    @Test
    fun `дата, сохранённая прежним календарём, открывается на том же дне`() {
        useZone("Europe/Moscow")
        val oldStyle = utcMidnight(2026, 5, 9) // = 03:00 9 мая по Москве

        assertEquals(LocalDate.of(2026, 5, 9), DateUtils.toLocalDate(oldStyle))
        assertEquals(utcMidnight(2026, 5, 9), PickerDates.toPicker(oldStyle))
    }

    /** Открыли календарь и подтвердили тот же день — запись не меняется */
    @Test
    fun `подтверждение того же дня не меняет значение`() {
        useZone("Asia/Vladivostok")
        val stored = local(2026, 1, 1, 0, 15)

        assertEquals(stored, PickerDates.fromPicker(PickerDates.toPicker(stored), stored))
    }
}
