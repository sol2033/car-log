package com.carlog.domain.model

/**
 * Цепочка замен расходника: кто кого заменил.
 *
 * Связи «заменил» в БД нет — есть только категория и совпадение даты и пробега замены
 * предшественника с датой и пробегом установки преемника. Этого достаточно, потому что
 * деактивацию всегда проставляет тот же код, что создаёт новую позицию
 * (`AddBreakdownViewModel`, «Заменить» на экране расходника).
 */
object ConsumableChainRepair {

    /**
     * Кого вернуть в активные, если [removed] удаляют из ТО.
     *
     * Возвращаем предшественника только тогда, когда после удаления в категории вообще
     * не остаётся активной позиции: если после этого ТО расходник уже меняли ещё раз,
     * активен более свежий, и воскрешать старый нельзя — в категории окажется два активных,
     * а это ровно то рассогласование, которое ищет экран «Проверка данных».
     *
     * @param all все расходники автомобиля (включая [removed])
     */
    fun reactivationTarget(all: List<Consumable>, removed: Consumable): Consumable? {
        // Разовые позиции «Другое» цепочки не образуют — им некого возвращать
        if (!ConsumableFormRules.supportsReminders(removed.category)) return null

        val sameCategory = all.filter { it.category == removed.category && it.id != removed.id }
        if (sameCategory.any { it.isActive }) return null

        return sameCategory
            .filter {
                !it.isActive &&
                    it.replacementDate == removed.installationDate &&
                    it.replacementMileage == removed.installationMileage
            }
            .maxWithOrNull(compareBy({ it.installationDate }, { it.id }))
    }

    /**
     * Кому поправить дату и пробег замены, если у [consumable] сдвинулись дата и пробег
     * установки (пользователь исправил дату самого ТО).
     *
     * Без этого предшественник остался бы «заменённым» задним числом относительно своей
     * замены, и связь между ними потерялась бы: [reactivationTarget] ищет её по совпадению.
     *
     * @param all все расходники автомобиля до изменения
     */
    fun predecessorToShift(all: List<Consumable>, consumable: Consumable): Consumable? {
        if (!ConsumableFormRules.supportsReminders(consumable.category)) return null
        return all
            .filter {
                it.id != consumable.id &&
                    it.category == consumable.category &&
                    !it.isActive &&
                    it.replacementDate == consumable.installationDate &&
                    it.replacementMileage == consumable.installationMileage
            }
            .maxWithOrNull(compareBy({ it.installationDate }, { it.id }))
    }
}
