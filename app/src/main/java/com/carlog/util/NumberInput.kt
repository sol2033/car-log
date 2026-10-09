package com.carlog.util

/**
 * Разбор дробного числа из поля ввода.
 *
 * На русской раскладке клавиатура даёт запятую, а `toDoubleOrNull()` на ней возвращает null —
 * и введённая стоимость молча пропадала при сохранении (у диалога расходника ТО при этом
 * ещё и блокировалась кнопка, так что правка «не сохранялась» вообще без объяснений).
 * Единая точка разбора нужна, чтобы поля не расходились в поведении: часть из них
 * запятую понимала, часть — нет.
 */
fun String.toDecimalOrNull(): Double? = filterNot(::isDigitGroupSeparator)
    .replace(',', '.')
    .takeIf { it.isNotEmpty() }
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() }

/**
 * Пробелы между разрядами. Суммы приложение само показывает как «15 000,00 ₽» — ровно так
 * их и набирают или вставляют из буфера. Раньше «15 000» не разбиралось: почти везде это
 * была ошибка ввода, а в форме ДТП выплата молча сохранялась как «не указана».
 * `isFinite` отсекает «NaN» и «Infinity», которые `toDoubleOrNull` честно принимает.
 */
private fun isDigitGroupSeparator(c: Char): Boolean = c.isWhitespace() || c == '\u00A0' || c == '\u202F'

/**
 * Ошибка необязательного денежного поля: пусто — можно, иначе нужно неотрицательное число.
 * Для полей, где сумма раньше молча терялась при неразобранном вводе.
 */
fun moneyInputError(raw: String): String? {
    if (raw.isBlank()) return null
    val value = raw.toDecimalOrNull()
    return if (value == null || value < 0) "Введите сумму числом" else null
}

/**
 * Разбор пробега из поля ввода.
 *
 * Разделители разрядов выбрасываются: «80 000» — ровно то, что человек видит в приложении
 * (`formatNumber` печатает пробег через неразрывный пробел) и что он набирает или вставляет
 * из буфера. Раньше такое значение доходило до `toInt()` внутри сохранения и падало
 * с `NumberFormatException` — запись не сохранялась, а пользователь получал красным
 * текст java-исключения по-английски.
 */
fun String.toMileageOrNull(): Int? = filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
    .takeIf { it.isNotEmpty() }
    ?.toIntOrNull()
    ?.takeIf { it >= 0 }

/**
 * Сумма, посчитанная приложением, — в текст поля ввода: до копеек и без хвостов двоичной
 * арифметики. `(54.99 * 41.4).toString()` давал «2276.5860000000002» — так и
 * показывалось в поле общей стоимости заправки и так же сохранялось.
 */
fun Double.toMoneyInput(): String =
    java.math.BigDecimal.valueOf(this)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
