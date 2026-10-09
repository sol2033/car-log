package com.carlog.domain.model

import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull

/**
 * Разбор полей экрана «Настройки расходников» — чистые функции, тесты `ConsumableSettingsRulesTest`.
 *
 * Раньше поля были привязаны прямо к числам: объём пересобирался в «4.0» на каждую клавишу
 * (а «4,5» стирало поле), а стёртый интервал тут же возвращался к значению по умолчанию, и
 * набранные следом цифры приписывались к нему («10000» → стёр → «8» → «100008»). Теперь
 * поля держат текст как есть, а в число он превращается только при сохранении.
 *
 * Хранимое `null` значит «как по умолчанию»: так значение, совпавшее с умолчанием, не
 * замораживается в настройках и следует за ним, если умолчание в будущей версии поменяется.
 */
object ConsumableSettingsRules {

    sealed interface Parsed<out T> {
        /** Что сохранить; null — убрать из настроек (действует умолчание) */
        data class Ok<T>(val stored: T?) : Parsed<T>
        object Invalid : Parsed<Nothing>
    }

    /** Текст поля интервала при открытии экрана: сохранённое, иначе умолчание категории */
    fun intervalText(stored: Int?, default: Int?): String = (stored ?: default)?.toString() ?: ""

    /** Текст поля объёма при открытии: «4» и «4.5», а не «4.0» */
    fun volumeText(stored: Double?): String = when {
        stored == null -> ""
        stored % 1.0 == 0.0 -> stored.toLong().toString()
        else -> stored.toString()
    }

    /**
     * Интервал (км или дни). Пусто — по умолчанию; пробелы-разделители («10 000») допустимы;
     * ноль и мусор — ошибка: интервал «0» напоминал бы о замене сразу после неё.
     */
    fun interval(text: String, default: Int?): Parsed<Int> {
        if (text.isBlank()) return Parsed.Ok(null)
        val value = text.toMileageOrNull()?.takeIf { it > 0 } ?: return Parsed.Invalid
        return Parsed.Ok(if (value == default) null else value)
    }

    /** Объём жидкости, л. Пусто — не задан; запятая допустима; ноль и мусор — ошибка */
    fun volume(text: String): Parsed<Double> {
        if (text.isBlank()) return Parsed.Ok(null)
        val value = text.toDecimalOrNull()?.takeIf { it > 0 } ?: return Parsed.Invalid
        return Parsed.Ok(value)
    }
}
