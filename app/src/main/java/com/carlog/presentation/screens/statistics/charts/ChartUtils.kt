package com.carlog.presentation.screens.statistics.charts

import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Компактное форматирование числовых значений для осей и подписей графиков:
 * 1 200 -> "1.2k", 3 400 000 -> "3.4M". Это спасает ось Y от длинных
 * чисел и наложений.
 */
internal fun formatChartValue(value: Float): String {
    val absValue = abs(value)
    return when {
        absValue >= 1_000_000f -> DecimalFormat("#.#").format(value / 1_000_000f) + "M"
        absValue >= 1_000f -> DecimalFormat("#.#").format(value / 1_000f) + "k"
        absValue >= 1f -> DecimalFormat("#").format(value)
        value == 0f -> "0"
        else -> DecimalFormat("#.#").format(value)
    }
}

/**
 * Шаг прореживания подписей на оси X, чтобы влезало не более [maxLabels]
 * подписей и они не накладывались друг на друга.
 */
internal fun labelSpacingStep(itemCount: Int, maxLabels: Int = 7): Int {
    if (itemCount <= maxLabels) return 1
    return max(1, ceil(itemCount / maxLabels.toFloat()).toInt())
}
