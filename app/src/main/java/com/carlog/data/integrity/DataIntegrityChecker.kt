package com.carlog.data.integrity

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.repair.EventPartLinkRepair
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.Part
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Поиск и точечное исправление следов бага редактирования (§16 технической документации).
 *
 * Однозначные случаи чинит миграция 19→20 автоматически; сюда попадает то, что автоматика
 * решать не вправе — где кандидатов несколько, суммы не сходятся или нужных данных
 * в базе просто нет. Скан **только читает**; изменения происходят лишь по явному
 * действию пользователя на экране «Проверка данных».
 */
@Singleton
class DataIntegrityChecker @Inject constructor(
    private val carDao: CarDao,
    private val partDao: PartDao,
    private val breakdownDao: BreakdownDao,
    private val accidentDao: AccidentDao,
    private val consumableDao: ConsumableDao
) {

    private companion object {
        const val MONEY_TOLERANCE = 0.01
    }

    suspend fun scan(): List<IntegrityFinding> {
        val cars = carDao.getAllCarsOnce()
        return cars.flatMap { car -> scanCar(car) }
    }

    private suspend fun scanCar(car: Car): List<IntegrityFinding> {
        val label = "${car.brand} ${car.model}"
        val parts = partDao.getPartsByCarId(car.id).firstOrNull().orEmpty()
        val breakdowns = breakdownDao.getBreakdownsByCarId(car.id).firstOrNull().orEmpty()
        val accidents = accidentDao.getAccidentsByCarId(car.id).firstOrNull().orEmpty()
        val consumables = consumableDao.getConsumablesByCarId(car.id).firstOrNull().orEmpty()

        val findings = mutableListOf<IntegrityFinding>()
        findings += unlinkedParts(label, parts, breakdowns, accidents)
        findings += accidentsWithoutRepairCost(label, accidents, parts)
        findings += breakdownCostMismatches(label, breakdowns, parts)
        findings += carWithoutPurchaseInfo(label, car)
        findings += duplicateActiveConsumables(label, consumables)
        return findings
    }

    // === Правила поиска ===

    private fun unlinkedParts(
        carLabel: String,
        parts: List<Part>,
        breakdowns: List<Breakdown>,
        accidents: List<Accident>
    ): List<IntegrityFinding> {
        val claimed = (breakdowns.flatMap { it.installedPartIds.orEmpty() } +
            accidents.flatMap { it.installedPartIds.orEmpty() }).toSet()

        return parts.mapNotNull { part ->
            if (claimed.contains(part.id)) return@mapNotNull null

            val candidates = when (part.installationType) {
                EventPartLinkRepair.SERVICE_INSTALLATION -> breakdowns
                    .filter { it.breakdownDate == part.installDate && it.breakdownMileage == part.installMileage }
                    .map {
                        EventCandidate(
                            type = EventType.BREAKDOWN,
                            id = it.id,
                            title = it.title,
                            date = it.breakdownDate,
                            mileage = it.breakdownMileage,
                            declaredPartsCost = it.partsCost
                        )
                    }

                EventPartLinkRepair.ACCIDENT_INSTALLATION -> accidents
                    .filter { it.date == part.installDate && it.mileage == part.installMileage }
                    .map {
                        EventCandidate(
                            type = EventType.ACCIDENT,
                            id = it.id,
                            title = it.damageDescription,
                            date = it.date,
                            mileage = it.mileage,
                            declaredPartsCost = it.repairCost
                        )
                    }

                // Установленную самостоятельно запчасть событие создать не могло
                else -> emptyList()
            }

            if (candidates.isEmpty()) return@mapNotNull null

            IntegrityFinding.UnlinkedPart(
                id = "unlinked_part_${part.id}",
                carLabel = carLabel,
                part = part,
                candidates = candidates
            )
        }
    }

    private fun accidentsWithoutRepairCost(
        carLabel: String,
        accidents: List<Accident>,
        parts: List<Part>
    ): List<IntegrityFinding> = accidents.mapNotNull { accident ->
        val linkedIds = accident.installedPartIds.orEmpty()
        if (linkedIds.isEmpty()) return@mapNotNull null
        if ((accident.repairCost ?: 0.0) > MONEY_TOLERANCE) return@mapNotNull null

        val sum = parts.filter { linkedIds.contains(it.id) }.sumOf { it.price }
        if (sum <= MONEY_TOLERANCE) return@mapNotNull null

        IntegrityFinding.AccidentWithoutRepairCost(
            id = "accident_repair_cost_${accident.id}",
            carLabel = carLabel,
            accident = accident,
            linkedPartsSum = sum
        )
    }

    private fun breakdownCostMismatches(
        carLabel: String,
        breakdowns: List<Breakdown>,
        parts: List<Part>
    ): List<IntegrityFinding> = breakdowns.mapNotNull { breakdown ->
        val linkedIds = breakdown.installedPartIds.orEmpty()
        if (linkedIds.isEmpty()) return@mapNotNull null

        val sum = parts.filter { linkedIds.contains(it.id) }.sumOf { it.price }
        if (abs(sum - breakdown.partsCost) <= MONEY_TOLERANCE) return@mapNotNull null

        IntegrityFinding.BreakdownCostMismatch(
            id = "breakdown_cost_${breakdown.id}",
            carLabel = carLabel,
            breakdown = breakdown,
            linkedPartsSum = sum
        )
    }

    private fun carWithoutPurchaseInfo(carLabel: String, car: Car): List<IntegrityFinding> =
        if (car.purchaseDate == null || car.purchaseMileage == null) {
            listOf(
                IntegrityFinding.CarWithoutPurchaseInfo(
                    id = "car_purchase_${car.id}",
                    carLabel = carLabel,
                    car = car
                )
            )
        } else {
            emptyList()
        }

    private fun duplicateActiveConsumables(
        carLabel: String,
        consumables: List<com.carlog.domain.model.Consumable>
    ): List<IntegrityFinding> = consumables
        .filter { it.isActive }
        .groupBy { it.category }
        .filter { (_, items) -> items.size > 1 }
        .map { (category, items) ->
            IntegrityFinding.DuplicateActiveConsumables(
                id = "duplicate_active_${items.first().carId}_$category",
                carLabel = carLabel,
                category = category,
                consumables = items.sortedByDescending { it.installationMileage }
            )
        }

    // === Точечные исправления ===

    /** Привязывает запчасть к выбранному событию: её стоимость перестаёт считаться дважды */
    suspend fun linkPartToEvent(partId: Long, candidate: EventCandidate) {
        when (candidate.type) {
            EventType.BREAKDOWN -> {
                val breakdown = breakdownDao.getBreakdownById(candidate.id).firstOrNull() ?: return
                val ids = breakdown.installedPartIds.orEmpty() + partId
                breakdownDao.updateBreakdown(
                    breakdown.copy(
                        installedPartIds = ids.distinct(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            EventType.ACCIDENT -> {
                val accident = accidentDao.getAccidentById(candidate.id).firstOrNull() ?: return
                val ids = accident.installedPartIds.orEmpty() + partId
                accidentDao.updateAccident(
                    accident.copy(
                        installedPartIds = ids.distinct(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Проставляет стоимость ремонта ДТП. По умолчанию — сумма привязанных запчастей;
     * стоимость работ сервиса отдельно не хранилась и известна только пользователю,
     * поэтому её можно передать вторым слагаемым.
     */
    suspend fun setAccidentRepairCost(accidentId: Long, repairCost: Double) {
        val accident = accidentDao.getAccidentById(accidentId).firstOrNull() ?: return
        accidentDao.updateAccident(
            accident.copy(repairCost = repairCost, updatedAt = System.currentTimeMillis())
        )
    }

    /** Приводит стоимость запчастей обслуживания к сумме привязанных к нему запчастей */
    suspend fun alignBreakdownPartsCost(breakdownId: Long, partsSum: Double) {
        val breakdown = breakdownDao.getBreakdownById(breakdownId).firstOrNull() ?: return
        breakdownDao.updateBreakdown(
            breakdown.copy(
                partsCost = partsSum,
                totalCost = partsSum + (breakdown.serviceCost ?: 0.0),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Заполняет дату и пробег покупки — без них средний пробег в день считается неверно */
    suspend fun setPurchaseInfo(carId: Long, purchaseDate: Long, purchaseMileage: Int) {
        val car = carDao.getCarByIdOnce(carId) ?: return
        carDao.updateCar(
            car.copy(
                purchaseDate = purchaseDate,
                purchaseMileage = purchaseMileage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Оставляет активным расходник с наибольшим пробегом установки, остальные помечает
     * заменёнными на его дату и пробег — как это делает штатная замена.
     */
    suspend fun keepSingleActiveConsumable(carId: Long, category: String) {
        val active = consumableDao.getConsumablesByCarId(carId).firstOrNull().orEmpty()
            .filter { it.isActive && it.category == category }
        if (active.size < 2) return

        val newest = active.maxByOrNull { it.installationMileage } ?: return
        val now = System.currentTimeMillis()
        active.filter { it.id != newest.id }.forEach { outdated ->
            consumableDao.updateConsumable(
                outdated.copy(
                    isActive = false,
                    replacementMileage = newest.installationMileage,
                    replacementDate = newest.installationDate,
                    updatedAt = now
                )
            )
        }
    }
}
