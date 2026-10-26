package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** Регрессия: отрицательная стоимость принималась и уменьшала стоимость ТО */
    @Test
    fun `отрицательная стоимость позицию не пропускает, ноль — пропускает`() {
        assertFalse(canAdd("Фильтр масляный", cost = "-500"))
        assertTrue(canAdd("Фильтр масляный", cost = "0"))
    }

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

    /**
     * Русская раскладка даёт запятую, и `toDoubleOrNull()` на ней возвращал null:
     * кнопка «Сохранить» в диалоге расходника гасла без единого объяснения,
     * и правка ТО выглядела как «изменения не сохранились».
     */
    @Test
    fun `стоимость с запятой принимается`() {
        assertTrue(canAdd("Фильтр масляный", cost = "1250,50"))
        assertTrue(canAdd("Масло в двигателе", cost = "4200", volume = "4,5"))
    }

    @Test
    fun `нечисловая стоимость по-прежнему не принимается`() {
        assertFalse(canAdd("Фильтр масляный", cost = "дорого"))
        assertFalse(canAdd("Фильтр масляный", cost = ""))
    }

    // --- автоподстановка объёма ---

    private fun card(id: Long, mileage: Int, volume: Double?, category: String = "Масло в двигателе") = Consumable(
        id = id, carId = 1, category = category, manufacturer = null, articleNumber = null,
        installationMileage = mileage, installationDate = mileage.toLong(),
        replacementMileage = null, replacementDate = null, cost = 1.0,
        isInstalledAtService = false, serviceCost = null, volume = volume,
        replacementIntervalMileage = null, replacementIntervalDays = null,
        isActive = true, notes = null, createdAt = 0, updatedAt = 0
    )

    @Test
    fun `объём берётся из последней карточки той же категории`() {
        val volume = ConsumableFormRules.defaultVolume(
            "Масло в двигателе",
            listOf(card(1, 10_000, 4.0), card(2, 20_000, 4.2)),
            settingsVolume = 5.0
        )
        assertEquals(4.2, volume!!, 0.0001)
    }

    @Test
    fun `карточка без объёма пропускается`() {
        val volume = ConsumableFormRules.defaultVolume(
            "Масло в двигателе",
            listOf(card(1, 10_000, 4.0), card(2, 20_000, null)),
            settingsVolume = null
        )
        assertEquals(4.0, volume!!, 0.0001)
    }

    @Test
    fun `без истории — объём из настроек`() {
        assertEquals(3.5, ConsumableFormRules.defaultVolume("Масло в двигателе", emptyList(), 3.5)!!, 0.0001)
    }

    @Test
    fun `у не-жидкости объём не подставляется`() {
        assertNull(
            ConsumableFormRules.defaultVolume(
                "Фильтр масляный", listOf(card(1, 10_000, 4.0, category = "Фильтр масляный")), 1.0
            )
        )
    }
}
