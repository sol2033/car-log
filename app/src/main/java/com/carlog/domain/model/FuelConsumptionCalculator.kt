package com.carlog.domain.model

import com.carlog.util.DateUtils
import java.time.LocalDate

/**
 * Средний расход топлива и пробег на баке — одна формула для экрана заправок и статистики.
 *
 * Раньше их было две: список заправок усреднял расход отдельных «полных баков» (а их почти
 * никто не отмечал, и среднего просто не было видно), статистика делила литры на дистанцию.
 * С 1.4.0 «полного бака» нет: расход считается только по сумме литров на пройденные км.
 *
 * Чистые функции без IO (правило 9).
 */
object FuelConsumptionCalculator {

    const val PETROL = "Бензин"
    const val GAS = "Газ"
    const val DIESEL = "Дизель"
    const val ELECTRIC = "Электро"

    /** Группа топлива: все АИ — один бензин, метан и пропан — газ */
    fun fuelGroup(fuelType: String): String = when {
        fuelType.contains("АИ", ignoreCase = true) || fuelType == PETROL -> PETROL
        fuelType.contains("Метан", ignoreCase = true) ||
            fuelType.contains("Пропан", ignoreCase = true) ||
            fuelType.contains("CNG", ignoreCase = true) -> GAS
        fuelType.contains("Дизель", ignoreCase = true) -> DIESEL
        fuelType.contains("Электр", ignoreCase = true) -> ELECTRIC
        else -> fuelType
    }

    /** Единица объёма: метан заправляют в кубометрах, электричество — в кВт·ч */
    fun volumeUnit(fuelType: String): String = when {
        fuelType.contains("Электр", ignoreCase = true) -> "кВт·ч"
        fuelType.contains("Метан", ignoreCase = true) ||
            fuelType.contains("CNG", ignoreCase = true) -> "м³"
        else -> "л"
    }

    /**
     * Заправки, по которым считается средний расход: с последней точки отсчёта (§4.5).
     * Записи до неё в среднее не идут — перед точкой была пропущенная заправка.
     */
    fun consumptionWindow(refuelings: List<Refueling>): List<Refueling> {
        val sorted = sortedByMileage(refuelings)
        val resetIndex = sorted.indexOfLast { it.isConsumptionResetPoint }
        return if (resetIndex > 0) sorted.drop(resetIndex) else sorted
    }

    /** Что сделать с точкой отсчёта, когда удаляют заправку */
    sealed interface ResetPointOnDelete {
        /** Удаляемая заправка не действующая точка отсчёта — ничего не меняется */
        object Unaffected : ResetPointOnDelete
        /** Точка переходит к следующей заправке: окно расчёта остаётся тем же */
        data class MoveTo(val successor: Refueling) : ResetPointOnDelete
        /** Следующей заправки нет — точкой станет следующая добавленная (как запрос отсчёта) */
        object Pending : ResetPointOnDelete
    }

    /**
     * Удаление заправки, с которой считается расход (§4.5).
     *
     * Раньше точка отсчёта просто пропадала вместе с записью, и расход молча начинал
     * считаться по всей истории — вместе с пропущенной заправкой, из-за которой отсчёт
     * и начинали заново. Теперь окно расчёта сохраняется: точка переходит к следующей
     * заправке, а если её нет — ею станет следующая добавленная.
     *
     * @param all все заправки машины, включая удаляемую
     */
    fun resetPointOnDelete(all: List<Refueling>, deleted: Refueling): ResetPointOnDelete {
        if (!deleted.isConsumptionResetPoint) return ResetPointOnDelete.Unaffected
        val sorted = sortedByMileage(all)
        // Действует только последняя точка: более ранняя на расчёт уже не влияет
        if (sorted.lastOrNull { it.isConsumptionResetPoint }?.id != deleted.id) {
            return ResetPointOnDelete.Unaffected
        }
        val successor = sorted.getOrNull(sorted.indexOfFirst { it.id == deleted.id } + 1)
        return successor?.let { ResetPointOnDelete.MoveTo(it) } ?: ResetPointOnDelete.Pending
    }

    /** Дата точки отсчёта расхода; null — расход считается по всей истории */
    fun consumptionSince(refuelings: List<Refueling>): LocalDate? =
        sortedByMileage(refuelings).lastOrNull { it.isConsumptionResetPoint }
            ?.let { DateUtils.toLocalDate(it.date) }

    /**
     * Средний расход одной группы топлива на 100 км.
     *
     * Литры первой заправки в расчёт не идут: этот бак сгорел до неё, на пройденной
     * дистанции его нет. Дистанция — от первой заправки до последней.
     *
     * У машины с ГБО общий пробег делится между газом и бензином, поэтому, если у всех
     * заправок (кроме первой) указано «проехано на этом топливе», делим на сумму этих км —
     * расход точный. Если хоть где-то не указано — делим на общий пробег, и результат
     * помечается приблизительным.
     *
     * null — данных не хватает: меньше двух заправок или нулевая дистанция.
     */
    fun averageConsumption(
        groupRefuelings: List<Refueling>,
        hasGasEquipment: Boolean
    ): ConsumptionResult? {
        if (groupRefuelings.size < 2) return null
        val sorted = sortedByMileage(groupRefuelings)
        val counted = sorted.drop(1)
        val liters = counted.sumOf { it.liters }

        if (hasGasEquipment && counted.all { (it.fuelDistanceKm ?: 0) > 0 }) {
            val fuelDistance = counted.sumOf { it.fuelDistanceKm ?: 0 }
            return ConsumptionResult(liters / fuelDistance * 100, isApproximate = false)
        }

        val distance = sorted.last().mileage - sorted.first().mileage
        if (distance <= 0) return null
        return ConsumptionResult(liters / distance * 100, isApproximate = hasGasEquipment)
    }

    /**
     * Ёмкость, на которую считается пробег на баке: бензин и дизель — бак,
     * газ — баллон, электро — батарея (лежит в том же поле, что и бак).
     */
    fun tankCapacity(group: String, car: Car?): Double? {
        if (car == null) return null
        val capacity = if (group == GAS) car.gasTankVolume else car.tankVolume
        return capacity?.takeIf { it > 0 }
    }

    /** Пробег на полном баке, км: ёмкость / расход × 100 */
    fun rangePerTank(consumptionPer100Km: Double?, capacity: Double?): Double? {
        if (consumptionPer100Km == null || consumptionPer100Km <= 0) return null
        if (capacity == null || capacity <= 0) return null
        return capacity / consumptionPer100Km * 100
    }

    /**
     * Сводка по группам топлива машины: то, что показывают и экран заправок, и статистика.
     * Порядок групп — по первому появлению в истории.
     */
    fun summarize(refuelings: List<Refueling>, car: Car?): List<FuelGroupConsumption> {
        val hasGas = car?.hasGasEquipment == true
        val window = consumptionWindow(refuelings).groupBy { fuelGroup(it.fuelType) }
        return sortedByMileage(refuelings)
            .groupBy { fuelGroup(it.fuelType) }
            .map { (group, all) ->
                val consumption = averageConsumption(window[group].orEmpty(), hasGas)
                val capacity = tankCapacity(group, car)
                FuelGroupConsumption(
                    group = group,
                    unit = volumeUnit(all.last().fuelType),
                    consumption = consumption,
                    tankCapacity = capacity,
                    rangeKm = rangePerTank(consumption?.per100Km, capacity)
                )
            }
    }

    private fun sortedByMileage(refuelings: List<Refueling>) =
        refuelings.sortedWith(compareBy({ it.mileage }, { it.date }, { it.id }))
}

data class ConsumptionResult(
    val per100Km: Double,
    // Машина с ГБО, а км на этом топливе указаны не везде: делили на общий пробег
    val isApproximate: Boolean
)

data class FuelGroupConsumption(
    val group: String,
    val unit: String,
    val consumption: ConsumptionResult?,
    // null — объём бака (баллона, батареи) не указан: пробег на баке не посчитать
    val tankCapacity: Double?,
    val rangeKm: Double?
)
