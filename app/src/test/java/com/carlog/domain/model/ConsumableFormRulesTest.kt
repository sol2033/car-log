package com.carlog.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Что форма расходника обязана спросить для конкретной категории (§4.4 бизнес-логики).
 * Правила общие для отдельной формы и для диалога внутри ТО — раньше диалог ТО требовал
 * объём даже у фильтров и колодок.
 */
class ConsumableFormRulesTest {

    private fun canAdd(
        category: String,
        customName: String = "",
        cost: String = "1500",
        volume: String = "",
        intervalMileage: String = "10000",
        intervalDays: String = ""
    ) = ConsumableFormRules.canAdd(
        category = category,
        customName = customName,
        cost = cost,
        volume = volume,
        intervalMileage = intervalMileage,
        intervalDays = intervalDays
    )

    @Test
    fun `объём спрашивается только у жидкостей`() {
        assertTrue(ConsumableFormRules.requiresVolume("Масло в двигателе"))
        assertFalse(ConsumableFormRules.requiresVolume("Фильтр масляный"))
        assertFalse(ConsumableFormRules.requiresVolume("Колодки тормозные передние"))
        assertFalse(ConsumableFormRules.requiresVolume(ConsumableCategories.OTHER))
    }

    @Test
    fun `фильтр добавляется без объёма`() {
        assertTrue(canAdd("Фильтр масляный", volume = ""))
    }

    @Test
    fun `масло без объёма не добавить`() {
        assertFalse(canAdd("Масло в двигателе", volume = ""))
        assertTrue(canAdd("Масло в двигателе", volume = "4.2"))
    }

    @Test
    fun `хватает одного интервала — по пробегу или по сроку`() {
        assertTrue(canAdd("Колодки тормозные передние", intervalMileage = "40000", intervalDays = ""))
        assertTrue(canAdd("Аккумулятор", intervalMileage = "", intervalDays = "1825"))
        assertFalse(
            "без единого интервала напоминать не о чем",
            canAdd("Аккумулятор", intervalMileage = "", intervalDays = "")
        )
    }

    @Test
    fun `у позиции «Другое» нет ни объёма, ни интервалов, но нужно название`() {
        val other = ConsumableCategories.OTHER
        assertFalse(ConsumableFormRules.supportsReminders(other))
        assertTrue(ConsumableFormRules.requiresCustomName(other))

        assertFalse(
            "без названия позиция безымянная — в карточке ТО её не опознать",
            canAdd(other, customName = "", intervalMileage = "", intervalDays = "")
        )
        assertTrue(canAdd(other, customName = "Герметик", intervalMileage = "", intervalDays = ""))
    }

    @Test
    fun `две позиции одной категории в одном ТО — ошибка, а «Другого» может быть много`() {
        val added = listOf("Масло в двигателе", ConsumableCategories.OTHER)

        assertTrue(ConsumableFormRules.isDuplicateCategory(added, "Масло в двигателе"))
        assertFalse(ConsumableFormRules.isDuplicateCategory(added, "Фильтр масляный"))
        assertFalse(
            "герметик и хомуты — разные позиции, а категория у них одна",
            ConsumableFormRules.isDuplicateCategory(added, ConsumableCategories.OTHER)
        )
    }

    @Test
    fun `стоимость обязательна, категория тоже`() {
        assertFalse(canAdd("Фильтр масляный", cost = ""))
        assertFalse(canAdd("Фильтр масляный", cost = "не число"))
        assertFalse(canAdd(category = ""))
    }
}
