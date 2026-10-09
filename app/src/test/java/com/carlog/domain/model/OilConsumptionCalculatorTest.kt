package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Расход моторного масла: доливы и падение уровня по интервалам между заменами (§4.4).
 */
class OilConsumptionCalculatorTest {

    private val car = Car(
        id = 1, brand = "Lada", model = "Vesta", year = null, color = null,
        licensePlate = null, vin = null, engineModel = null, engineVolume = null,
        transmissionType = null, driveType = null, bodyType = null, fuelType = "Бензин",
        currentMileage = 30_000, purchaseMileage = null, purchaseDate = null,
        mainPhotoPath = null, photosPaths = null, notes = null, createdAt = 0, updatedAt = 0
    )

    private fun oil(
        id: Long,
        installed: Int,
        replaced: Int? = null,
        dropMl: Int? = null,
        dropFraction: Double? = null,
        category: String = OilConsumptionCalculator.ENGINE_OIL
    ) = Consumable(
        id = id, carId = 1, category = category, manufacturer = "Масло $id", articleNumber = null,
        installationMileage = installed, installationDate = installed.toLong(),
        replacementMileage = replaced, replacementDate = replaced?.toLong(),
        cost = 3_000.0, isInstalledAtService = false, serviceCost = null, volume = 4.0,
        replacementIntervalMileage = 10_000, replacementIntervalDays = null,
        isActive = replaced == null, notes = null, createdAt = 0, updatedAt = 0,
        oilLevelDropMl = dropMl, oilLevelDropFraction = dropFraction
    )

    private fun topUp(id: Long, mileage: Int, liters: Double, cost: Double? = null) = OilTopUp(
        id = id, carId = 1, date = mileage.toLong(), mileage = mileage, volume = liters, cost = cost
    )

    // --- интервалы ---

    @Test
    fun `расход интервала — доливы плюс падение уровня на пройденные км`() {
        val interval = OilConsumptionCalculator.intervals(
            listOf(oil(1, 0, replaced = 10_000, dropMl = 500)),
            listOf(topUp(1, 5_000, 0.5)),
            car, 30_000
        ).single()

        // (0,5 + 0,5) л на 10 000 км = 0,1 л/1000 км
        assertEquals(1.0, interval.consumedLiters, 0.0001)
        assertEquals(0.1, interval.per1000Km!!, 0.0001)
        assertFalse(interval.isApproximate)
    }

    @Test
    fun `долив относится к маслу, стоявшему на его пробеге`() {
        val intervals = OilConsumptionCalculator.intervals(
            listOf(oil(1, 0, replaced = 10_000), oil(2, 10_000)),
            listOf(topUp(1, 9_000, 0.3), topUp(2, 10_000, 0.2), topUp(3, 15_000, 0.4)),
            car, 20_000
        )
        assertEquals(listOf(1L), intervals[0].topUps.map { it.id })
        // Долив на пробеге замены — уже на новом масле
        assertEquals(listOf(2L, 3L), intervals[1].topUps.map { it.id })
    }

    @Test
    fun `долив после замены без нового масла никуда не относится`() {
        val interval = OilConsumptionCalculator.intervals(
            listOf(oil(1, 0, replaced = 10_000)),
            listOf(topUp(1, 12_000, 1.0)),
            car, 20_000
        ).single()
        assertTrue("иначе старому маслу приписали бы чужой долив", interval.topUps.isEmpty())
    }

    @Test
    fun `долив до первого масла в журнале игнорируется`() {
        val interval = OilConsumptionCalculator.intervals(
            listOf(oil(1, 10_000)),
            listOf(topUp(1, 5_000, 1.0)),
            car, 20_000
        ).single()
        assertTrue(interval.topUps.isEmpty())
    }

    @Test
    fun `у текущего масла интервал до текущего пробега`() {
        val interval = OilConsumptionCalculator.intervals(
            listOf(oil(1, 20_000)),
            listOf(topUp(1, 25_000, 0.5)),
            car, 30_000
        ).single()
        assertFalse(interval.isCompleted)
        assertEquals(10_000, interval.distanceKm)
        assertEquals(0.05, interval.per1000Km!!, 0.0001)
    }

    @Test
    fun `на свежем масле без пробега расхода нет`() {
        val interval = OilConsumptionCalculator.intervals(listOf(oil(1, 30_000)), emptyList(), car, 30_000).single()
        assertNull(interval.per1000Km)
    }

    @Test
    fun `без доливов и с неупавшим уровнем расход нулевой, а не пустой`() {
        val interval = OilConsumptionCalculator.intervals(
            listOf(oil(1, 0, replaced = 10_000, dropMl = 0)), emptyList(), car, 30_000
        ).single()
        assertEquals(0.0, interval.per1000Km!!, 0.0001)
    }

    @Test
    fun `другие категории в расход масла не попадают`() {
        val intervals = OilConsumptionCalculator.intervals(
            listOf(oil(1, 0, category = "Масло/жидкость КПП"), oil(2, 0)),
            emptyList(), car, 30_000
        )
        assertEquals(listOf(2L), intervals.map { it.oil.id })
    }

    // --- щуп ---

    @Test
    fun `доля щупа переводится через объём MIN–MAX, а не картера`() {
        val drop = OilConsumptionCalculator.levelDrop(
            oil(1, 0, replaced = 10_000, dropFraction = 0.5),
            car.copy(oilDipstickVolume = 0.8)
        )!!
        assertEquals(0.4, drop.liters, 0.0001)
        assertTrue("по щупу — всегда оценка", drop.byDipstick)
        assertFalse(drop.dipstickVolumeIsGuess)
    }

    @Test
    fun `без объёма щупа берётся 1 литр и это помечается`() {
        val drop = OilConsumptionCalculator.levelDrop(oil(1, 0, replaced = 10_000, dropFraction = 0.5), car)!!
        assertEquals(0.5, drop.liters, 0.0001)
        assertTrue(drop.dipstickVolumeIsGuess)
    }

    @Test
    fun `«не знаю» помечается даже с подставленным литром`() {
        val drop = OilConsumptionCalculator.levelDrop(
            oil(1, 0, replaced = 10_000, dropFraction = 1.0),
            car.copy(oilDipstickVolume = 1.0, oilDipstickVolumeIsGuess = true)
        )!!
        assertTrue(drop.dipstickVolumeIsGuess)
    }

    @Test
    fun `исправленный объём щупа пересчитывает старые интервалы`() {
        val oils = listOf(oil(1, 0, replaced = 10_000, dropFraction = 0.5))
        val before = OilConsumptionCalculator.intervals(oils, emptyList(), car.copy(oilDipstickVolume = 1.0), 30_000)
        val after = OilConsumptionCalculator.intervals(oils, emptyList(), car.copy(oilDipstickVolume = 1.5), 30_000)
        assertEquals(0.05, before.single().per1000Km!!, 0.0001)
        assertEquals(0.075, after.single().per1000Km!!, 0.0001)
    }

    @Test
    fun `падение в мл — точное значение`() {
        val drop = OilConsumptionCalculator.levelDrop(oil(1, 0, replaced = 10_000, dropMl = 300), car)!!
        assertEquals(0.3, drop.liters, 0.0001)
        assertFalse(drop.byDipstick)
    }

    @Test
    fun `уровень не указан — падения нет`() {
        assertNull(OilConsumptionCalculator.levelDrop(oil(1, 0, replaced = 10_000), car))
    }

    // --- средний за историю ---

    @Test
    fun `средний — по завершённым интервалам, взвешенный по пробегу`() {
        val summary = OilConsumptionCalculator.summarize(
            listOf(
                oil(1, 0, replaced = 10_000, dropMl = 1_000),      // 1 л на 10 000 км
                oil(2, 10_000, replaced = 15_000, dropMl = 1_500), // 1,5 л на 5 000 км
                oil(3, 15_000)
            ),
            listOf(topUp(1, 20_000, 2.0)), // текущее масло в средний не идёт
            car, 30_000
        )
        // 2,5 л на 15 000 км ≈ 0,1667 л/1000 км, а не среднее (0,1 + 0,3) / 2
        assertEquals(2.5 / 15_000 * 1000, summary.overallPer1000Km!!, 0.0001)
        assertEquals(3L, summary.current!!.oil.id)
    }

    @Test
    fun `без завершённых интервалов среднего нет`() {
        val summary = OilConsumptionCalculator.summarize(listOf(oil(1, 0)), emptyList(), car, 30_000)
        assertNull(summary.overallPer1000Km)
    }

    @Test
    fun `средний помечается приблизительным, если в нём есть оценка по щупу`() {
        val summary = OilConsumptionCalculator.summarize(
            listOf(oil(1, 0, replaced = 10_000, dropFraction = 0.25), oil(2, 10_000, replaced = 20_000, dropMl = 100)),
            emptyList(), car, 30_000
        )
        assertTrue(summary.overallIsApproximate)
        assertTrue(summary.overallUsesGuessedDipstick)
    }

    // --- деньги ---

    @Test
    fun `долив в статистике — отдельная категория со своими деньгами, датой и пробегом`() {
        val entry = OilConsumptionCalculator.asCostEntry(topUp(7, 12_000, 0.5, cost = 450.0))
        assertEquals(OilConsumptionCalculator.TOP_UP_COST_CATEGORY, entry.category)
        assertEquals(450.0, entry.cost!!, 0.001)
        assertEquals(12_000, entry.installationMileage)
        assertNull("не привязан к ТО — идёт в «отдельные» расходники", entry.linkedMaintenanceId)
    }

    // --- вопрос об уровне при любой замене ---

    @Test
    fun `закрывается последнее моторное масло из заменяемых`() {
        val closed = OilConsumptionCalculator.oilClosedBy(
            listOf(oil(1, 0), oil(2, 10_000), oil(3, 5_000, category = "Фильтр масляный"))
        )
        assertEquals(2L, closed!!.id)
    }

    @Test
    fun `не масло — спрашивать не о чем`() {
        assertNull(OilConsumptionCalculator.oilClosedBy(listOf(oil(1, 0, category = "Фильтр масляный"))))
        assertNull(OilConsumptionCalculator.oilClosedBy(emptyList()))
    }

    @Test
    fun `ответ пишется целиком — мл вытесняет прежнюю долю щупа`() {
        val updated = OilConsumptionCalculator.withLevelDrop(
            oil(1, 0, dropFraction = 0.5),
            OilLevelDropInput(ml = 300, fraction = null)
        )
        assertEquals(300, updated.oilLevelDropMl)
        assertNull(updated.oilLevelDropFraction)
    }
}
