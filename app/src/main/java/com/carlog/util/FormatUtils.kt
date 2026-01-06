package com.carlog.util

import androidx.compose.runtime.Composable
import com.carlog.data.preferences.Currency
import com.carlog.presentation.util.LocalCurrency
import java.text.DecimalFormat

/**
 * Форматирование валюты с учетом выбранной валюты пользователя
 */
fun formatCurrency(value: Double, currency: Currency): String {
    val formatted = DecimalFormat("#,##0.00").format(value)
    return "$formatted ${currency.symbol}"
}

/**
 * Форматирование валюты с использованием текущей валюты из LocalCurrency
 */
@Composable
fun formatCurrency(value: Double): String {
    val currency = LocalCurrency.current
    return formatCurrency(value, currency)
}

/**
 * Форматирование числа с разделителями тысяч
 */
fun formatNumber(value: Double): String {
    return DecimalFormat("#,##0.#").format(value)
}

/**
 * Форматирование числа с разделителями тысяч (Int)
 */
fun formatNumber(value: Int): String {
    return DecimalFormat("#,##0").format(value)
}
