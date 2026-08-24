package com.carlog.domain.model

/**
 * Правила формы расходника: что именно спрашивать у пользователя для конкретной категории.
 *
 * Чистые функции без IO — так их проверяют тесты, а обе формы (отдельный расходник и диалог
 * внутри ТО) не расходятся в поведении: раньше диалог ТО требовал объём даже у фильтров.
 */
object ConsumableFormRules {

    /** Объём спрашиваем только у жидкостей: у фильтра или колодок литров не бывает */
    fun requiresVolume(category: String): Boolean =
        ConsumableCategories.FLUID_CATEGORIES.contains(category)

    /** У позиции «Другое» вместо категории показывается введённое пользователем название */
    fun requiresCustomName(category: String): Boolean =
        category == ConsumableCategories.OTHER

    /**
     * Напоминания о замене (интервалы по пробегу и сроку) есть у всего, кроме «Другого»:
     * разовая мелочь вроде герметика не заменяется по расписанию.
     */
    fun supportsReminders(category: String): Boolean =
        category != ConsumableCategories.OTHER

    /**
     * Категория уже занята другой позицией этого ТО: два масла в одном ТО — ошибка ввода.
     *
     * На «Другое» правило не распространяется: это не категория, а разовые позиции,
     * которых в одном ТО может быть сколько угодно (герметик, хомуты, промывка).
     */
    fun isDuplicateCategory(existingCategories: List<String>, category: String): Boolean =
        supportsReminders(category) && existingCategories.contains(category)

    /**
     * Можно ли добавить позицию в ТО по введённым в диалоге строкам.
     *
     * Интервал достаточно указать один: напоминание срабатывает по любому из них
     * (аккумулятор — только срок, колодки — только пробег, так же и в `DEFAULT_INTERVALS`).
     */
    fun canAdd(
        category: String,
        customName: String,
        cost: String,
        volume: String,
        intervalMileage: String,
        intervalDays: String
    ): Boolean {
        if (category.isBlank()) return false
        if (requiresCustomName(category) && customName.isBlank()) return false
        if (cost.toDoubleOrNull() == null) return false
        if (requiresVolume(category) && volume.toDoubleOrNull() == null) return false
        if (supportsReminders(category) &&
            intervalMileage.toIntOrNull() == null &&
            intervalDays.toIntOrNull() == null
        ) return false
        return true
    }
}
