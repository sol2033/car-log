package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Средний расход без «полного бака» и пробег на баке (§4.5, §6.4 бизнес-логики).
 */
class FuelConsumptionCalculatorTest {

    private fun refueling(
        id: Long,
        mileage: Int,
        liters: Double,
        fuelType: String = "АИ-95",
        isResetPoint: Boolean = false,
        fuelDistanceKm: Int? = null
    ) = Refueling(
        id = id,
        carId = 1,
        date = mileage.toLong(), // порядок дат совпадает с порядком пробега
        mileage = mileage,
        liters = liters,
        fuelType = fuelType,
        isConsumptionResetPoint = isResetPoint,
        fuelDistanceKm = fuelDistanceKm
    )

    private val car = Car(
        id = 1, brand = "Lada", model = "Vesta", year = null, color = null,
        licensePlate = null, vin = null, engineModel = null, engineVolume = null,
        transmissionType = null, driveType = null, bodyType = null, fuelType = "Бензин",
        currentMileage = 100_000, purchaseMileage = null, purchaseDate = null,
        mainPhotoPath = null, photosPaths = null, notes = null, createdAt = 0, updatedAt = 0
    )

    private fun average(vararg refuelings: Refueling, hasGas: Boolean = false) =
        FuelConsumptionCalculator.averageConsumption(refuelings.toList(), hasGas)

    // --- средний расход ---

    @Test
    fun `одной заправки мало — расход не считается`() {
        assertNull(average(refueling(1, 100_000, 40.0)))
    }

    @Test
    fun `литры первой заправки в расход не идут`() {
        val result = average(
            refueling(1, 100_000, 40.0),
            refueling(2, 100_500, 45.0)
        )!!
        // 45 л на 500 км, а не (40 + 45) / 500
        assertEquals(9.0, result.per100Km, 0.0001)
        assertFalse(result.isApproximate)
    }

    @Test
    fun `частичные доливы просто складываются — полный бак не нужен`() {
        val result = average(
            refueling(1, 100_000, 40.0),
            refueling(2, 100_200, 10.0),
            refueling(3, 100_500, 35.0)
        )!!
        // (10 + 35) л на 500 км
        assertEquals(9.0, result.per100Km, 0.0001)
    }

    @Test
    fun `порядок задаёт пробег, а не порядок в списке`() {
        val result = average(
            refueling(3, 100_500, 35.0),
            refueling(1, 100_000, 40.0),
            refueling(2, 100_200, 10.0)
        )!!
        assertEquals(9.0, result.per100Km, 0.0001)
    }

    @Test
    fun `нулевая дистанция не даёт деления на ноль`() {
        assertNull(average(refueling(1, 100_000, 40.0), refueling(2, 100_000, 20.0)))
    }

    // --- точка отсчёта ---

    @Test
    fun `заправки до точки отсчёта в расход не идут`() {
        val list = listOf(
            refueling(1, 80_000, 50.0),
            refueling(2, 85_000, 50.0),
            refueling(3, 90_000, 50.0, isResetPoint = true),
            refueling(4, 90_500, 45.0)
        )
        val window = FuelConsumptionCalculator.consumptionWindow(list)
        assertEquals(listOf(3L, 4L), window.map { it.id })

        val summary = FuelConsumptionCalculator.summarize(list, car).single()
        assertEquals(9.0, summary.consumption!!.per100Km, 0.0001)
    }

    @Test
    fun `точка отсчёта на первой заправке ничего не отрезает`() {
        val list = listOf(
            refueling(1, 90_000, 50.0, isResetPoint = true),
            refueling(2, 90_500, 45.0)
        )
        assertEquals(2, FuelConsumptionCalculator.consumptionWindow(list).size)
    }

    @Test
    fun `сразу после точки отсчёта расхода ещё нет`() {
        val list = listOf(
            refueling(1, 80_000, 50.0),
            refueling(2, 90_000, 50.0, isResetPoint = true)
        )
        assertNull(FuelConsumptionCalculator.summarize(list, car).single().consumption)
    }

    // --- группы и единицы ---

    @Test
    fun `марки бензина считаются одной группой`() {
        val summary = FuelConsumptionCalculator.summarize(
            listOf(
                refueling(1, 100_000, 40.0, fuelType = "АИ-92"),
                refueling(2, 100_500, 45.0, fuelType = "АИ-95")
            ),
            car
        )
        assertEquals(listOf(FuelConsumptionCalculator.PETROL), summary.map { it.group })
        assertEquals(9.0, summary.single().consumption!!.per100Km, 0.0001)
    }

    @Test
    fun `метан в кубометрах, электричество в кВт·ч, остальное в литрах`() {
        assertEquals("м³", FuelConsumptionCalculator.volumeUnit("Метан (CNG)"))
        assertEquals("л", FuelConsumptionCalculator.volumeUnit("Пропан-бутан"))
        assertEquals("кВт·ч", FuelConsumptionCalculator.volumeUnit("Электричество"))
        assertEquals("л", FuelConsumptionCalculator.volumeUnit("Дизель"))
    }

    // --- ГБО: км на этом топливе ---

    @Test
    fun `у ГБО с км на топливе расход делится на них, а не на общий пробег`() {
        val result = average(
            refueling(1, 100_000, 40.0, fuelType = "Пропан-бутан"),
            refueling(2, 101_000, 40.0, fuelType = "Пропан-бутан", fuelDistanceKm = 400),
            refueling(3, 102_000, 40.0, fuelType = "Пропан-бутан", fuelDistanceKm = 400),
            hasGas = true
        )!!
        // 80 л на 800 км на газе, а не на 2000 км общего пробега
        assertEquals(10.0, result.per100Km, 0.0001)
        assertFalse(result.isApproximate)
    }

    @Test
    fun `у ГБО км на первой заправке не нужны — её литры и так не считаются`() {
        val result = average(
            refueling(1, 100_000, 40.0, fuelType = "Пропан-бутан"),
            refueling(2, 101_000, 40.0, fuelType = "Пропан-бутан", fuelDistanceKm = 400),
            hasGas = true
        )!!
        assertFalse(result.isApproximate)
    }

    @Test
    fun `у ГБО без км хоть в одной заправке расход по общему пробегу и приблизительный`() {
        val result = average(
            refueling(1, 100_000, 40.0, fuelType = "Пропан-бутан"),
            refueling(2, 101_000, 40.0, fuelType = "Пропан-бутан", fuelDistanceKm = 400),
            refueling(3, 102_000, 40.0, fuelType = "Пропан-бутан"),
            hasGas = true
        )!!
        // 80 л на 2000 км общего пробега
        assertEquals(4.0, result.per100Km, 0.0001)
        assertTrue(result.isApproximate)
    }

    @Test
    fun `без ГБО км на топливе игнорируются`() {
        val result = average(
            refueling(1, 100_000, 40.0),
            refueling(2, 100_500, 45.0, fuelDistanceKm = 100)
        )!!
        assertEquals(9.0, result.per100Km, 0.0001)
        assertFalse(result.isApproximate)
    }

    @Test
    fun `у ГБО газ и бензин считаются отдельно, каждый со своей ёмкостью`() {
        val gasCar = car.copy(
            hasGasEquipment = true, gasType = "Пропан-бутан",
            tankVolume = 50.0, gasTankVolume = 40.0
        )
        val summary = FuelConsumptionCalculator.summarize(
            listOf(
                refueling(1, 100_000, 40.0, fuelType = "Пропан-бутан"),
                refueling(2, 100_100, 10.0, fuelType = "АИ-95"),
                refueling(3, 100_400, 40.0, fuelType = "Пропан-бутан", fuelDistanceKm = 400),
                refueling(4, 100_600, 10.0, fuelType = "АИ-95", fuelDistanceKm = 100)
            ),
            gasCar
        ).associateBy { it.group }

        val gas = summary.getValue(FuelConsumptionCalculator.GAS)
        assertEquals(10.0, gas.consumption!!.per100Km, 0.0001)
        assertEquals(40.0, gas.tankCapacity!!, 0.0001)
        assertEquals(400.0, gas.rangeKm!!, 0.0001)

        val petrol = summary.getValue(FuelConsumptionCalculator.PETROL)
        assertEquals(10.0, petrol.consumption!!.per100Km, 0.0001)
        assertEquals(50.0, petrol.tankCapacity!!, 0.0001)
        assertEquals(500.0, petrol.rangeKm!!, 0.0001)
    }

    // --- пробег на баке ---

    @Test
    fun `пробег на баке — объём на расход`() {
        assertEquals(500.0, FuelConsumptionCalculator.rangePerTank(9.0, 45.0)!!, 0.0001)
    }

    @Test
    fun `без объёма или расхода пробег на баке не считается`() {
        assertNull(FuelConsumptionCalculator.rangePerTank(9.0, null))
        assertNull(FuelConsumptionCalculator.rangePerTank(null, 45.0))
        assertNull(FuelConsumptionCalculator.rangePerTank(0.0, 45.0))
        assertNull(FuelConsumptionCalculator.rangePerTank(9.0, 0.0))
    }

    @Test
    fun `у машины до 1-3-1 объёма нет — просим указать, а не выдумываем`() {
        val summary = FuelConsumptionCalculator.summarize(
            listOf(refueling(1, 100_000, 40.0), refueling(2, 100_500, 45.0)),
            car
        ).single()
        assertNull(summary.tankCapacity)
        assertNull(summary.rangeKm)
    }

    @Test
    fun `у электромобиля пробег считается по ёмкости батареи`() {
        val ev = car.copy(fuelType = "Электро", tankVolume = 60.0)
        val summary = FuelConsumptionCalculator.summarize(
            listOf(
                refueling(1, 10_000, 30.0, fuelType = "Электричество"),
                refueling(2, 10_200, 30.0, fuelType = "Электричество")
            ),
            ev
        ).single()
        assertEquals("кВт·ч", summary.unit)
        // 30 кВт·ч на 200 км = 15 кВт·ч/100км, батарея 60 → 400 км
        assertEquals(400.0, summary.rangeKm!!, 0.0001)
    }

    // region Удаление точки отсчёта (регрессия: расход молча возвращался ко всей истории)

    @Test
    fun `удаление действующей точки передаёт её следующей заправке`() {
        val point = refueling(2, 100_500, 40.0, isResetPoint = true)
        val all = listOf(refueling(1, 100_000, 40.0), point, refueling(3, 101_000, 42.0))

        val result = FuelConsumptionCalculator.resetPointOnDelete(all, point)

        assertEquals(3L, (result as FuelConsumptionCalculator.ResetPointOnDelete.MoveTo).successor.id)
    }

    @Test
    fun `удаление точки без следующей заправки ставит запрос нового отсчёта`() {
        val point = refueling(2, 100_500, 40.0, isResetPoint = true)

        val result = FuelConsumptionCalculator.resetPointOnDelete(listOf(refueling(1, 100_000, 40.0), point), point)

        assertEquals(FuelConsumptionCalculator.ResetPointOnDelete.Pending, result)
    }

    @Test
    fun `удаление обычной или устаревшей точки отсчёт не трогает`() {
        val oldPoint = refueling(1, 100_000, 40.0, isResetPoint = true)
        val plain = refueling(2, 100_500, 40.0)
        val all = listOf(oldPoint, plain, refueling(3, 101_000, 42.0, isResetPoint = true))

        assertEquals(
            FuelConsumptionCalculator.ResetPointOnDelete.Unaffected,
            FuelConsumptionCalculator.resetPointOnDelete(all, plain)
        )
        assertEquals(
            FuelConsumptionCalculator.ResetPointOnDelete.Unaffected,
            FuelConsumptionCalculator.resetPointOnDelete(all, oldPoint)
        )
    }
}
