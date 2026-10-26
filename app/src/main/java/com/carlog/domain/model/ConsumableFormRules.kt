package com.carlog.domain.model

import com.carlog.util.toMileageOrNull
import com.carlog.util.toDecimalOrNull

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
        // Отрицательная стоимость уменьшала бы стоимость ТО
        if ((cost.toDecimalOrNull() ?: return false) < 0) return false
        if (requiresVolume(category) && volume.toDecimalOrNull() == null) return false
        if (supportsReminders(category) &&
            parseInterval(intervalMileage) == null &&
            parseInterval(intervalDays) == null
        ) return false
        return true
    }

    /**
     * Интервал замены (км или дни): пробелы-разделители допустимы («10 000»), ноль — нет.
     * Раньше разбор шёл через `toIntOrNull`, и «10 000» или «10.000» молча превращались
     * в «интервала нет» — напоминание о замене пропадало без единого слова.
     */
    fun parseInterval(raw: String): Int? = raw.toMileageOrNull()?.takeIf { it > 0 }

    /** Ошибка необязательного поля интервала: пусто — можно, иначе целое число больше нуля */
    fun intervalError(raw: String): String? =
        if (raw.isBlank() || parseInterval(raw) != null) null else "Введите целое число больше нуля"

    /**
     * Объём, который подставить в новую карточку жидкости: из последней карточки той же
     * категории этой машины, а если её нет — из настроек категории. Пользователь всё равно
     * может его поправить. null — подставлять нечего (или категория не жидкость).
     */
    fun defaultVolume(
        category: String,
        sameCategoryHistory: List<Consumable>,
        settingsVolume: Double?
    ): Double? {
        if (!requiresVolume(category)) return null
        val previous = sameCategoryHistory
            .filter { it.category == category && (it.volume ?: 0.0) > 0 }
            .maxWithOrNull(compareBy({ it.installationMileage }, { it.installationDate }, { it.id }))
        return previous?.volume ?: settingsVolume
    }
}
