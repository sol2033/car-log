package com.carlog.data.local.repair

import kotlin.math.abs

/** Событие (обслуживание или ДТП), к которому могли относиться запчасти */
data class RepairEvent(
    val id: Long,
    val carId: Long,
    val date: Long,
    val mileage: Int,
    /** Тип обслуживания; у ДТП его нет */
    val maintenanceType: String? = null,
    /** Стоимость запчастей события; у ДТП сверять не с чем (обнулялась тем же багом) */
    val partsCost: Double? = null,
    val alreadyLinked: Boolean = false
)

/** Запчасть-кандидат на восстановление связи */
data class RepairPart(
    val id: Long,
    val carId: Long,
    val installDate: Long,
    val installMileage: Int,
    val installationType: String,
    val maintenanceType: String?,
    val price: Double,
    /** Уже закреплена за каким-то событием — трогать нельзя */
    val claimed: Boolean = false
)

/**
 * Правила восстановления связи «событие → его запчасти», потерянной при редактировании
 * (`installedPartIds` обнулялся, см. §16 технической документации).
 *
 * Чинятся **только однозначные** случаи; всё сомнительное остаётся нетронутым и попадает
 * на экран «Проверка данных», где решение принимает пользователь.
 *
 * Логика вынесена из миграции отдельно, чтобы её можно было покрыть обычными JVM-тестами,
 * а не только инструментальными.
 */
object EventPartLinkRepair {

    /** `installationType` запчастей, созданных через обслуживание */
    const val SERVICE_INSTALLATION = "Сервис"

    /** `installationType` запчастей, созданных через ДТП */
    const val ACCIDENT_INSTALLATION = "ДТП"

    /** Копейки: суммы — Double, точное сравнение недопустимо */
    private const val MONEY_TOLERANCE = 0.01

    /**
     * Связи для обслуживаний. Признаки, которые должны сойтись **все сразу**:
     * - `installationType` = «Сервис» (так помечает запчасти форма обслуживания);
     * - `installDate` и `installMileage` точно равны дате и пробегу события (копируются из него);
     * - `maintenanceType` запчасти равен типу события — у добавленных вручную он пуст;
     * - на этот набор признаков приходится ровно одно событие;
     * - сумма цен кандидатов точно равна `partsCost` события. Это главная защита: посторонняя
     *   запчасть в наборе ломает равенство, и случай пропускается.
     */
    fun resolveBreakdownLinks(
        breakdowns: List<RepairEvent>,
        parts: List<RepairPart>
    ): Map<Long, List<Long>> {
        val freeParts = groupFreeParts(parts, SERVICE_INSTALLATION)
        val eventsPerKey = breakdowns.groupingBy { it.key(SERVICE_INSTALLATION) }.eachCount()
        val result = mutableMapOf<Long, List<Long>>()

        for (breakdown in breakdowns) {
            if (breakdown.alreadyLinked) continue
            val maintenanceType = breakdown.maintenanceType ?: continue
            val partsCost = breakdown.partsCost ?: continue
            if (partsCost <= 0) continue

            val key = breakdown.key(SERVICE_INSTALLATION)
            if (eventsPerKey[key] != 1) continue

            val candidates = freeParts[key]
                ?.filter { it.maintenanceType == maintenanceType }
                ?.takeIf { it.isNotEmpty() }
                ?: continue

            if (abs(candidates.sumOf { it.price } - partsCost) > MONEY_TOLERANCE) continue

            result[breakdown.id] = candidates.map { it.id }
            freeParts[key] = freeParts.getValue(key) - candidates.toSet()
        }

        return result
    }

    /**
     * Связи для ДТП. Проверки суммы нет — `repairCost` тем же багом обнулялся, сверять не с чем.
     * Зато `installationType` = «ДТП» ставится **только** запчастям, созданным аварией:
     * принадлежность событию известна, неоднозначен лишь выбор между двумя авариями
     * в один день и с одним пробегом — такие пропускаются.
     */
    fun resolveAccidentLinks(
        accidents: List<RepairEvent>,
        parts: List<RepairPart>
    ): Map<Long, List<Long>> {
        val freeParts = groupFreeParts(parts, ACCIDENT_INSTALLATION)
        val eventsPerKey = accidents.groupingBy { it.key(ACCIDENT_INSTALLATION) }.eachCount()
        val result = mutableMapOf<Long, List<Long>>()

        for (accident in accidents) {
            if (accident.alreadyLinked) continue

            val key = accident.key(ACCIDENT_INSTALLATION)
            if (eventsPerKey[key] != 1) continue

            val candidates = freeParts[key]?.takeIf { it.isNotEmpty() } ?: continue

            result[accident.id] = candidates.map { it.id }
            freeParts[key] = emptyList()
        }

        return result
    }

    private fun groupFreeParts(
        parts: List<RepairPart>,
        installationType: String
    ): MutableMap<String, List<RepairPart>> =
        parts.asSequence()
            .filter { !it.claimed && it.installationType == installationType }
            .groupBy { "${it.carId}|${it.installDate}|${it.installMileage}|${it.installationType}" }
            .toMutableMap()

    private fun RepairEvent.key(installationType: String) =
        "$carId|$date|$mileage|$installationType"
}
