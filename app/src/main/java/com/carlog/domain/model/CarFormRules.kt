package com.carlog.domain.model

import com.carlog.util.toDecimalOrNull

/**
 * Правила формы автомобиля. Чистые функции без IO (правило 9).
 */
object CarFormRules {

    /**
     * Ошибка поля объёма (бак, баллон, батарея).
     *
     * Обязательно только при добавлении машины: у машин, заведённых до 1.4.0, объёма нет,
     * и требовать его при любой правке карточки (сменили цвет — введи бак) незачем —
     * статистика сама попросит его указать.
     */
    fun volumeError(raw: String, required: Boolean): String? {
        if (raw.isBlank()) return if (required) "Обязательное поле" else null
        val value = raw.toDecimalOrNull()
        return if (value == null || value <= 0) "Введите положительное число" else null
    }
}
