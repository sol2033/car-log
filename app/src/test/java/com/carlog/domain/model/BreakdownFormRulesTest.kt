package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки, из-за которых правка обслуживания не доезжала до базы: разбор пробега
 * и выбор режима запчастей у записи с повисшими ссылками.
 */
class BreakdownFormRulesTest {

    private fun part(id: Long) = Part(
        id = id,
        carId = 1,
        name = "Рычаг",
        installDate = 0L,
        installMileage = 10_000,
        installationType = "Сервис",
        price = 5_000.0,
        maintenanceType = MaintenanceType.REPAIR.name
    )

    // region mileageError

    @Test
    fun `обычный пробег принимается`() {
        assertNull(BreakdownFormRules.mileageError("80000"))
    }

    /**
     * Регрессия: проверялась только пустота, дальше шёл `toInt()`. Значение с пробелом —
     * ровно в таком виде пробег показан в самом приложении — роняло сохранение
     * в `catch`, и пользователь видел текст java-исключения вместо записи.
     */
    @Test
    fun `пробег с разделителем разрядов принимается`() {
        assertNull(BreakdownFormRules.mileageError("80 000"))
        assertNull(BreakdownFormRules.mileageError("80\u00A0000"))
    }

    @Test
    fun `пустой пробег — обязательное поле`() {
        assertEquals("Обязательное поле", BreakdownFormRules.mileageError(""))
        assertEquals("Обязательное поле", BreakdownFormRules.mileageError("   "))
    }

    @Test
    fun `нечисловой пробег отклоняется до сохранения, а не падает в нём`() {
        assertEquals("Пробег вводится целым числом", BreakdownFormRules.mileageError("80к"))
        assertEquals("Пробег вводится целым числом", BreakdownFormRules.mileageError("80,5"))
        assertEquals("Пробег вводится целым числом", BreakdownFormRules.mileageError("-100"))
        assertEquals(
            "значение больше Int роняло toInt()",
            "Пробег вводится целым числом",
            BreakdownFormRules.mileageError("99999999999")
        )
    }

    // endregion

    // region usesPartsList

    @Test
    fun `запчасти есть — форма открывается списком`() {
        assertTrue(BreakdownFormRules.usesPartsList(listOf(part(1))))
    }

    /**
     * Регрессия: режим выбирался по `installedPartIds`, а не по тому, что удалось
     * загрузить. Удаление запчасти в модуле «Запчасти» ссылку в событии не чистит,
     * и запись открывалась со списком, в котором пусто. Валидация «добавьте хотя бы
     * одну запчасть» после этого не давала сохранить её вообще — а сообщение
     * оставалось за краем экрана.
     */
    @Test
    fun `повисшие ссылки не включают пустой список`() {
        assertFalse(BreakdownFormRules.usesPartsList(emptyList()))
    }

    // endregion
}
