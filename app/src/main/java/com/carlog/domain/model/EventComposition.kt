package com.carlog.domain.model

/**
 * Деньги за запчасти одного события, разложенные по категориям статистики.
 * Работы сюда не входят: они и так отдельная категория «Услуги сервисов», не разбитая по типам.
 */
data class EventCostSplit(
    val scheduledService: Double = 0.0,
    val repair: Double = 0.0,
    val modification: Double = 0.0
) {
    val total: Double get() = scheduledService + repair + modification

    operator fun plus(other: EventCostSplit) = EventCostSplit(
        scheduledService = scheduledService + other.scheduledService,
        repair = repair + other.repair,
        modification = modification + other.modification
    )

    companion object {
        val EMPTY = EventCostSplit()
    }
}

/**
 * Состав события: одно обслуживание может быть сразу ТО, ремонтом и тюнингом.
 *
 * До 1.2.4 тип был один на всё событие (`Breakdown.maintenanceType`), и комплекс работ за один
 * заезд в сервис приходилось заводить тремя записями. Теперь тип **выводится из содержимого**:
 * расходники — это ТО, а каждая запчасть несёт свой тип в `Part.maintenanceType`.
 * Старое поле осталось запасным вариантом: у события без единой позиции (диагностика,
 * развал-схождение, запчасти одной суммой) выводить тип неоткуда, и его выбирают руками.
 *
 * Чистые функции без IO — от них зависят деньги в статистике, и проверяются они на JVM.
 */
object EventComposition {

    /** Порядок сегментов на карточке и чипов: фиксированный, чтобы цвета не прыгали */
    val DISPLAY_ORDER = listOf(
        MaintenanceType.SCHEDULED_SERVICE,
        MaintenanceType.REPAIR,
        MaintenanceType.MODIFICATION
    )

    /**
     * Тип запчасти внутри события. Запись без типа — ремонт: так же трактуются старые
     * события без `maintenanceType` в списке и в статистике.
     */
    fun typeOfPart(storedType: String?): MaintenanceType =
        MaintenanceType.fromString(storedType) ?: MaintenanceType.REPAIR

    /**
     * Типы, которые есть в событии, в порядке [DISPLAY_ORDER].
     *
     * Считаем по наличию позиций, а не по деньгам: позиция с нулевой ценой в событии всё равно
     * есть, и прятать из-за неё сегмент карточки неправильно.
     *
     * @param fallbackType `Breakdown.maintenanceType` — используется, только если позиций нет.
     */
    fun typesOf(
        fallbackType: MaintenanceType?,
        hasConsumables: Boolean,
        partTypes: List<MaintenanceType>
    ): List<MaintenanceType> {
        val present = mutableSetOf<MaintenanceType>()
        if (hasConsumables) present += MaintenanceType.SCHEDULED_SERVICE
        present += partTypes

        if (present.isEmpty()) {
            // Событие без позиций: только работы либо запчасти общей суммой
            present += fallbackType ?: MaintenanceType.REPAIR
        }
        return DISPLAY_ORDER.filter { present.contains(it) }
    }

    /**
     * Раскладка `partsCost` события по категориям.
     *
     * Итог намеренно приравнен к `partsCost`, а не к сумме позиций: именно `partsCost` показан
     * в карточке события и именно он до сих пор формировал категории в статистике. Если позиции
     * с ним разошлись (например, цену запчасти поправили в модуле «Запчасти», а событие с тех пор
     * не пересохраняли), разница уходит в запасную категорию — тогда суммы по категориям
     * по-прежнему складываются в стоимость события, и при обновлении приложения ни одна цифра
     * в статистике не сдвигается сама по себе.
     *
     * У события без позиций разница равна всему `partsCost` — он целиком уходит в [fallbackType],
     * ровно как считалось раньше.
     *
     * @param consumableCosts стоимости расходников события; null внутри списка — позиция без цены
     * @param partPrices пары «тип запчасти — её цена» для запчастей события
     */
    fun split(
        partsCost: Double,
        fallbackType: MaintenanceType?,
        consumableCosts: List<Double?>,
        partPrices: List<Pair<MaintenanceType, Double>>
    ): EventCostSplit {
        val byPositions = EventCostSplit(
            // Запчасть с типом ТО остаётся в ТО: так бывает у события, которому когда-то
            // сменили тип. Переводить её в ремонт значило бы молча подвинуть деньги
            scheduledService = consumableCosts.sumOf { it ?: 0.0 } +
                partPrices.filter { it.first == MaintenanceType.SCHEDULED_SERVICE }.sumOf { it.second },
            repair = partPrices.filter { it.first == MaintenanceType.REPAIR }.sumOf { it.second },
            modification = partPrices.filter { it.first == MaintenanceType.MODIFICATION }
                .sumOf { it.second }
        )

        val remainder = partsCost - byPositions.total
        if (remainder == 0.0) return byPositions

        val bucket = fallbackType ?: MaintenanceType.REPAIR
        return byPositions + when (bucket) {
            MaintenanceType.SCHEDULED_SERVICE -> EventCostSplit(scheduledService = remainder)
            MaintenanceType.REPAIR -> EventCostSplit(repair = remainder)
            MaintenanceType.MODIFICATION -> EventCostSplit(modification = remainder)
        }
    }
}
