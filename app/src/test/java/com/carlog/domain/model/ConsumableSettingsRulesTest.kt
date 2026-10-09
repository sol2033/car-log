package com.carlog.domain.model

import com.carlog.domain.model.ConsumableSettingsRules.Parsed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Разбор полей «Настроек расходников».
 *
 * Регрессия: поля были привязаны к числам — объём пересобирался в «4.0» на каждую клавишу
 * («4,5» стирало поле), а стёртый интервал тут же возвращался к умолчанию, и набранное
 * следом приписывалось к нему: «10000» → стёр → «8» → «100008».
 */
class ConsumableSettingsRulesTest {

    @Test
    fun `текст поля при открытии — сохранённое, иначе умолчание`() {
        assertEquals("8000", ConsumableSettingsRules.intervalText(8_000, 10_000))
        assertEquals("10000", ConsumableSettingsRules.intervalText(null, 10_000))
        assertEquals("", ConsumableSettingsRules.intervalText(null, null))
    }

    @Test
    fun `объём показывается без хвоста «точка ноль»`() {
        assertEquals("4", ConsumableSettingsRules.volumeText(4.0))
        assertEquals("4.5", ConsumableSettingsRules.volumeText(4.5))
        assertEquals("", ConsumableSettingsRules.volumeText(null))
    }

    @Test
    fun `пустой интервал — по умолчанию`() {
        assertEquals(Parsed.Ok<Int>(null), ConsumableSettingsRules.interval("", 10_000))
        assertEquals(Parsed.Ok<Int>(null), ConsumableSettingsRules.interval("  ", 10_000))
    }

    /** Совпавшее с умолчанием не замораживается: поменяется умолчание — поменяется и интервал */
    @Test
    fun `интервал, равный умолчанию, хранится как «по умолчанию»`() {
        assertEquals(Parsed.Ok<Int>(null), ConsumableSettingsRules.interval("10000", 10_000))
        assertEquals(Parsed.Ok(8_000), ConsumableSettingsRules.interval("8000", 10_000))
    }

    @Test
    fun `интервал с пробелами-разделителями разбирается`() {
        assertEquals(Parsed.Ok(15_000), ConsumableSettingsRules.interval("15 000", 10_000))
    }

    @Test
    fun `ноль и мусор в интервале — ошибка`() {
        assertEquals(Parsed.Invalid, ConsumableSettingsRules.interval("0", 10_000))
        assertEquals(Parsed.Invalid, ConsumableSettingsRules.interval("10.000", 10_000))
        assertEquals(Parsed.Invalid, ConsumableSettingsRules.interval("-5", 10_000))
    }

    @Test
    fun `объём понимает запятую`() {
        assertEquals(Parsed.Ok(4.5), ConsumableSettingsRules.volume("4,5"))
        assertEquals(Parsed.Ok(4.0), ConsumableSettingsRules.volume("4"))
        assertEquals(Parsed.Ok<Double>(null), ConsumableSettingsRules.volume(""))
    }

    @Test
    fun `ноль и мусор в объёме — ошибка`() {
        assertEquals(Parsed.Invalid, ConsumableSettingsRules.volume("0"))
        assertEquals(Parsed.Invalid, ConsumableSettingsRules.volume("4,5,1"))
    }

    // === Интервал в форме расходника ===

    @Test
    fun `интервал формы расходника понимает пробелы и отвергает мусор`() {
        assertEquals(10_000, ConsumableFormRules.parseInterval("10 000"))
        assertEquals(null, ConsumableFormRules.parseInterval("10.000"))
        assertEquals(null, ConsumableFormRules.parseInterval("0"))
        assertEquals(null, ConsumableFormRules.intervalError(""))
        assertEquals(null, ConsumableFormRules.intervalError("365"))
        assertEquals("Введите целое число больше нуля", ConsumableFormRules.intervalError("10.000"))
    }
}
