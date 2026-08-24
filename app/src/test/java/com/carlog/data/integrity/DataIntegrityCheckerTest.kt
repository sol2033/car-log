package com.carlog.data.integrity

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.Part
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Скан проверки данных. Ключевое требование: находкой считается только реальное
 * расхождение — законно устроенные записи в список попадать не должны, иначе экран
 * превращается в шум.
 */
class DataIntegrityCheckerTest {

    private val carDao = mockk<CarDao>(relaxed = true)
    private val partDao = mockk<PartDao>(relaxed = true)
    private val breakdownDao = mockk<BreakdownDao>(relaxed = true)
    private val accidentDao = mockk<AccidentDao>(relaxed = true)
    private val consumableDao = mockk<ConsumableDao>(relaxed = true)

    private val checker = DataIntegrityChecker(carDao, partDao, breakdownDao, accidentDao, consumableDao)

    private val car = Car(
        id = 1,
        brand = "Lada",
        model = "Vesta",
        year = null,
        color = null,
        licensePlate = null,
        vin = null,
        engineModel = null,
        engineVolume = null,
        transmissionType = null,
        driveType = null,
        bodyType = null,
        fuelType = "Бензин",
        hasGasEquipment = false,
        gasType = null,
        currentMileage = 100_000,
        purchaseMileage = 0,
        purchaseDate = 1_000,
        mainPhotoPath = null,
        photosPaths = null,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun part(
        id: Long,
        price: Double = 5_000.0,
        installationType: String = "Сервис",
        date: Long = 1_000,
        mileage: Int = 80_000
    ) = Part(
        id = id,
        carId = 1,
        name = "Деталь $id",
        installDate = date,
        installMileage = mileage,
        installationType = installationType,
        price = price
    )

    private fun breakdown(
        id: Long,
        partsCost: Double = 5_000.0,
        installedPartIds: List<Long>? = null,
        date: Long = 1_000,
        mileage: Int = 80_000
    ) = Breakdown(
        id = id,
        carId = 1,
        title = "Ремонт",
        description = "описание",
        breakdownDate = date,
        breakdownMileage = mileage,
        maintenanceType = "REPAIR",
        installedPartIds = installedPartIds,
        partsCost = partsCost,
        totalCost = partsCost
    )

    private fun accident(
        id: Long,
        installedPartIds: List<Long>? = null,
        repairCost: Double? = null,
        date: Long = 1_000,
        mileage: Int = 80_000
    ) = Accident(
        id = id,
        carId = 1,
        date = date,
        mileage = mileage,
        location = null,
        damageDescription = "Бампер",
        severity = "Средняя",
        isUserAtFault = false,
        osagoPayout = null,
        kaskoPayout = null,
        culpritPayout = null,
        installedPartIds = installedPartIds,
        repairCost = repairCost,
        photosPaths = null,
        documentPath = null,
        notes = null
    )

    private fun consumable(id: Long, category: String, isActive: Boolean, mileage: Int = 80_000) = Consumable(
        id = id,
        carId = 1,
        category = category,
        manufacturer = null,
        articleNumber = null,
        installationMileage = mileage,
        installationDate = 1_000,
        replacementMileage = null,
        replacementDate = null,
        cost = 1_000.0,
        isInstalledAtService = false,
        serviceCost = null,
        volume = null,
        replacementIntervalMileage = 10_000,
        replacementIntervalDays = null,
        isActive = isActive,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Before
    fun setUp() {
        coEvery { carDao.getAllCarsOnce() } returns listOf(car)
        every { partDao.getPartsByCarId(1) } returns flowOf(emptyList())
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(emptyList())
        every { accidentDao.getAccidentsByCarId(1) } returns flowOf(emptyList())
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(emptyList())
    }

    @Test
    fun `непривязанная запчасть с событием-кандидатом попадает в находки`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(listOf(breakdown(20)))

        val findings = checker.scan().filterIsInstance<IntegrityFinding.UnlinkedPart>()

        assertEquals(1, findings.size)
        assertEquals(10L, findings.first().part.id)
        assertEquals(EventType.BREAKDOWN, findings.first().candidates.single().type)
    }

    @Test
    fun `привязанная запчасть находкой не считается`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(20, installedPartIds = listOf(10))))

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.UnlinkedPart>().isEmpty())
    }

    @Test
    fun `отдельная запчасть без подходящего события находкой не считается`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10)))
        // Обслуживание в тот же день, но с другим пробегом — не кандидат
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(listOf(breakdown(20, mileage = 90_000)))

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.UnlinkedPart>().isEmpty())
    }

    @Test
    fun `установленная самостоятельно запчасть не предлагается к привязке`() = runTest {
        every { partDao.getPartsByCarId(1) } returns
            flowOf(listOf(part(10, installationType = "Самостоятельно")))
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(listOf(breakdown(20)))

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.UnlinkedPart>().isEmpty())
    }

    @Test
    fun `ДТП с запчастями и без стоимости ремонта попадает в находки`() = runTest {
        every { partDao.getPartsByCarId(1) } returns
            flowOf(listOf(part(10, price = 9_000.0, installationType = "ДТП")))
        every { accidentDao.getAccidentsByCarId(1) } returns
            flowOf(listOf(accident(30, installedPartIds = listOf(10), repairCost = 0.0)))

        val findings = checker.scan().filterIsInstance<IntegrityFinding.AccidentWithoutRepairCost>()

        assertEquals(1, findings.size)
        assertEquals(9_000.0, findings.first().linkedPartsSum, 0.01)
    }

    @Test
    fun `ДТП с заполненной стоимостью находкой не считается`() = runTest {
        every { partDao.getPartsByCarId(1) } returns
            flowOf(listOf(part(10, price = 9_000.0, installationType = "ДТП")))
        every { accidentDao.getAccidentsByCarId(1) } returns
            flowOf(listOf(accident(30, installedPartIds = listOf(10), repairCost = 12_000.0)))

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.AccidentWithoutRepairCost>().isEmpty())
    }

    @Test
    fun `расхождение стоимости запчастей обслуживания попадает в находки`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, price = 5_000.0)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(20, partsCost = 7_500.0, installedPartIds = listOf(10))))

        val findings = checker.scan().filterIsInstance<IntegrityFinding.BreakdownCostMismatch>()

        assertEquals(1, findings.size)
        assertEquals(5_000.0, findings.first().linkedPartsSum, 0.01)
    }

    @Test
    fun `сошедшаяся стоимость находкой не считается`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, price = 5_000.0)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(20, partsCost = 5_000.0, installedPartIds = listOf(10))))

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.BreakdownCostMismatch>().isEmpty())
    }

    @Test
    fun `машина без даты покупки попадает в находки`() = runTest {
        coEvery { carDao.getAllCarsOnce() } returns listOf(car.copy(purchaseDate = null))

        assertEquals(1, checker.scan().filterIsInstance<IntegrityFinding.CarWithoutPurchaseInfo>().size)
    }

    @Test
    fun `заполненная покупка находкой не считается`() = runTest {
        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.CarWithoutPurchaseInfo>().isEmpty())
    }

    @Test
    fun `два активных расходника одной категории попадают в находки`() = runTest {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(1, "Масло в двигателе", isActive = true, mileage = 80_000),
                consumable(2, "Масло в двигателе", isActive = true, mileage = 90_000)
            )
        )

        val findings = checker.scan().filterIsInstance<IntegrityFinding.DuplicateActiveConsumables>()

        assertEquals(1, findings.size)
        // Первым идёт самый свежий — его и предлагается оставить активным
        assertEquals(90_000, findings.first().consumables.first().installationMileage)
    }

    @Test
    fun `несколько позиций «Другое» дубликатами не считаются`() = runTest {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(1, ConsumableCategories.OTHER, isActive = true, mileage = 80_000),
                consumable(2, ConsumableCategories.OTHER, isActive = true, mileage = 80_000)
            )
        )

        assertTrue(
            "герметик и хомуты из одного ТО — не ошибка данных",
            checker.scan().filterIsInstance<IntegrityFinding.DuplicateActiveConsumables>().isEmpty()
        )
    }

    @Test
    fun `активный и заменённый расходники — это норма`() = runTest {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(1, "Масло в двигателе", isActive = false),
                consumable(2, "Масло в двигателе", isActive = true)
            )
        )

        assertTrue(checker.scan().filterIsInstance<IntegrityFinding.DuplicateActiveConsumables>().isEmpty())
    }

    @Test
    fun `у находок стабильные идентификаторы для кнопки «оставить как есть»`() = runTest {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(listOf(breakdown(20)))

        val first = checker.scan().map { it.id }
        val second = checker.scan().map { it.id }

        assertEquals(first, second)
    }
}
