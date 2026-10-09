package com.carlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Единый разбор чисел из полей ввода.
 *
 * Регрессия: суммы с пробелами-разделителями («15 000» — ровно так приложение само их
 * показывает) не разбирались. Почти везде это была ошибка ввода, а в форме ДТП выплаты
 * и компенсация молча сохранялись как «не указано».
 */
class NumberInputTest {

    @Test
    fun `запятая и точка`() {
        assertEquals(1_500.5, "1500,5".toDecimalOrNull()!!, 0.0001)
        assertEquals(1_500.5, "1500.5".toDecimalOrNull()!!, 0.0001)
    }

    @Test
    fun `пробелы между разрядами, включая неразрывные`() {
        assertEquals(15_000.0, "15 000".toDecimalOrNull()!!, 0.0001)
        assertEquals(15_000.5, "15 000,5".toDecimalOrNull()!!, 0.0001)
        assertEquals(15_000.0, "15 000".toDecimalOrNull()!!, 0.0001)
        assertEquals(15_000.0, " 15000 ".toDecimalOrNull()!!, 0.0001)
    }

    @Test
    fun `мусор, пустота и нечисловые значения`() {
        assertNull("".toDecimalOrNull())
        assertNull("   ".toDecimalOrNull())
        assertNull("15к".toDecimalOrNull())
        assertNull("1,500.50".toDecimalOrNull())
        assertNull("NaN".toDecimalOrNull())
        assertNull("Infinity".toDecimalOrNull())
    }

    @Test
    fun `ошибка необязательного денежного поля`() {
        assertNull(moneyInputError(""))
        assertNull(moneyInputError("15 000"))
        assertNull(moneyInputError("0"))
        assertEquals("Введите сумму числом", moneyInputError("15к"))
        assertEquals("Введите сумму числом", moneyInputError("-500"))
    }

    /** `(54.99 * 41.4).toString()` давал «2276.5860000000002» прямо в поле суммы заправки */
    @Test
    fun `посчитанная сумма — до копеек и без хвостов`() {
        assertEquals("2276.59", (54.99 * 41.4).toMoneyInput())
        assertEquals("2300", 2300.0.toMoneyInput())
        assertEquals("1500.5", 1500.5.toMoneyInput())
        assertEquals("0.1", (0.1 + 0.2 - 0.2).toMoneyInput())
    }
}
