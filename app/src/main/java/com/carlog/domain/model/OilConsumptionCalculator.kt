package com.carlog.domain.model

/**
 * Расход моторного масла (угар) — по интервалам между заменами и в среднем за историю.
 *
 * Интервал — одна карточка «Масло в двигателе»: от пробега установки до пробега замены
 * (у активного — до текущего пробега машины). Расход интервала:
 * `(долито + падение уровня к замене) / пройденные км × 1000`, л/1000 км. Так видно и общий
 * масложор двигателя, и вырос ли он на конкретном масле.
 *
 * Чистые функции без IO (правило 9).
 */
object OilConsumptionCalculator {

    const val ENGINE_OIL = "Масло в двигателе"

    /** Категория, под которой деньги за доливы видны в статистике расходников */
    const val TOP_UP_COST_CATEGORY = "Долив масла"

    /** «Не знаю» объём между MIN и MAX: на большинстве моторов около литра */
    const val DEFAULT_DIPSTICK_VOLUME = 1.0

    /** Доли щупа, которые предлагаются пользователю: ¼, ½, ¾ и «до MIN» */
    val DIPSTICK_FRACTIONS = listOf(0.25, 0.5, 0.75, 1.0)

    fun isEngineOil(consumable: Consumable): Boolean = consumable.category == ENGINE_OIL

    /**
     * Падение уровня к замене в литрах. В мл — точно; долей щупа — через объём между
     * MIN и MAX, и это всегда оценка. Если объём не указан или выбран «не знаю», берётся 1 л,
     * и погрешность ещё больше. null — пользователь уровень не указал.
     */
    fun levelDrop(oil: Consumable, car: Car?): LevelDrop? {
        oil.oilLevelDropMl?.let { ml ->
            return LevelDrop(liters = ml / 1000.0, byDipstick = false, dipstickVolumeIsGuess = false)
        }
        val fraction = oil.oilLevelDropFraction ?: return null
        val span = car?.oilDipstickVolume?.takeIf { it > 0 }
        return LevelDrop(
            liters = fraction * (span ?: DEFAULT_DIPSTICK_VOLUME),
            byDipstick = true,
            dipstickVolumeIsGuess = span == null || car?.oilDipstickVolumeIsGuess == true
        )
    }

    /**
     * Интервалы масел машины по возрастанию пробега установки.
     *
     * Долив относится к последнему маслу, установленному не позже него. Долив после замены,
     * когда нового масла в журнале нет, не относится никуда: приписать его старому значило
     * бы завысить его расход.
     */
    fun intervals(
        consumables: List<Consumable>,
        topUps: List<OilTopUp>,
        car: Car?,
        currentMileage: Int
    ): List<OilInterval> {
        val oils = consumables.filter(::isEngineOil)
            .sortedWith(compareBy({ it.installationMileage }, { it.installationDate }, { it.id }))
        if (oils.isEmpty()) return emptyList()

        val topUpsByOil = topUps.groupBy { topUp ->
            val owner = oils.lastOrNull { it.installationMileage <= topUp.mileage }
            val ownerEnd = owner?.replacementMileage
            if (owner != null && ownerEnd != null && topUp.mileage > ownerEnd) null else owner?.id
        }

        return oils.map { oil ->
            val end = oil.replacementMileage ?: currentMileage
            val oilTopUps = topUpsByOil[oil.id].orEmpty().sortedBy { it.mileage }
            val topUpLiters = oilTopUps.sumOf { it.volume }
            val drop = levelDrop(oil, car)
            val distance = end - oil.installationMileage
            OilInterval(
                oil = oil,
                startMileage = oil.installationMileage,
                endMileage = end,
                isCompleted = oil.replacementMileage != null,
                topUps = oilTopUps,
                topUpLiters = topUpLiters,
                levelDrop = drop,
                distanceKm = distance,
                per1000Km = if (distance > 0) (topUpLiters + (drop?.liters ?: 0.0)) / distance * 1000 else null
            )
        }
    }

    /**
     * Средний расход за историю — только по завершённым интервалам: у текущего масла
     * падение уровня ещё неизвестно, и его доливы занизили бы среднее.
     */
    fun summarize(
        consumables: List<Consumable>,
        topUps: List<OilTopUp>,
        car: Car?,
        currentMileage: Int
    ): OilConsumptionSummary {
        val all = intervals(consumables, topUps, car, currentMileage)
        val completed = all.filter { it.isCompleted && it.distanceKm > 0 }
        val totalDistance = completed.sumOf { it.distanceKm }
        val overall = if (completed.isNotEmpty()) {
            completed.sumOf { it.consumedLiters } / totalDistance * 1000
        } else null
        return OilConsumptionSummary(
            intervals = all,
            overallPer1000Km = overall,
            overallIsApproximate = completed.any { it.levelDrop?.byDipstick == true },
            overallUsesGuessedDipstick = completed.any { it.levelDrop?.dipstickVolumeIsGuess == true },
            current = all.lastOrNull { !it.isCompleted }
        )
    }

    /**
     * Какое моторное масло закрывает новая установка: последнее из тех, что план вставки
     * помечает заменёнными. Про его уровень и спрашиваем — одинаково при замене с экрана
     * масла, прямом добавлении карточки и масле внутри ТО.
     */
    fun oilClosedBy(replaced: List<Consumable>): Consumable? =
        replaced.filter(::isEngineOil)
            .maxWithOrNull(compareBy({ it.installationMileage }, { it.installationDate }, { it.id }))

    /** Ответ пользователя записывается целиком: «не проверял» стирает прежнее значение */
    fun withLevelDrop(oil: Consumable, input: OilLevelDropInput): Consumable =
        oil.copy(oilLevelDropMl = input.ml, oilLevelDropFraction = input.fraction)

    /**
     * Долив как денежная запись для статистики: в «ТО» и вкладке «Расходники» — отдельной
     * категорией «Долив масла». Не в «Масле в двигателе»: иначе доливы считались бы
     * заменами и сбили бы среднюю стоимость ТО и число замен.
     */
    fun asCostEntry(topUp: OilTopUp): Consumable = Consumable(
        id = -topUp.id,
        carId = topUp.carId,
        category = TOP_UP_COST_CATEGORY,
        manufacturer = null,
        articleNumber = null,
        installationMileage = topUp.mileage,
        installationDate = topUp.date,
        linkedMaintenanceId = null,
        replacementMileage = null,
        replacementDate = null,
        cost = topUp.cost,
        isInstalledAtService = false,
        serviceCost = null,
        volume = topUp.volume,
        replacementIntervalMileage = null,
        replacementIntervalDays = null,
        isActive = false,
        notes = null,
        createdAt = topUp.createdAt,
        updatedAt = topUp.updatedAt
    )
}

/** Падение уровня масла к замене: в мл или долей щупа; оба null — не указано */
data class OilLevelDropInput(val ml: Int?, val fraction: Double?)

data class LevelDrop(
    val liters: Double,
    // Указано долей щупа — значение оценочное
    val byDipstick: Boolean,
    // Объём между MIN и MAX неизвестен («не знаю» или не задан): взят 1 л
    val dipstickVolumeIsGuess: Boolean
)

data class OilInterval(
    val oil: Consumable,
    val startMileage: Int,
    val endMileage: Int,
    // Масло уже заменено: падение уровня известно (или его не указали)
    val isCompleted: Boolean,
    val topUps: List<OilTopUp>,
    val topUpLiters: Double,
    val levelDrop: LevelDrop?,
    val distanceKm: Int,
    // null — пробега на этом масле ещё нет
    val per1000Km: Double?
) {
    val consumedLiters: Double get() = topUpLiters + (levelDrop?.liters ?: 0.0)
    val isApproximate: Boolean get() = levelDrop?.byDipstick == true
}

data class OilConsumptionSummary(
    val intervals: List<OilInterval>,
    val overallPer1000Km: Double?,
    val overallIsApproximate: Boolean,
    val overallUsesGuessedDipstick: Boolean,
    val current: OilInterval?
)
