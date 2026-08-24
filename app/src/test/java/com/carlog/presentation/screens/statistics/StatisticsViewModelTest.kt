package com.carlog.presentation.screens.statistics

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.MileageFilter
import com.carlog.domain.model.Part
import com.carlog.domain.model.Refueling
import com.carlog.domain.model.StatisticsPeriod
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Денежные правила статистики (§5 бизнес-логики): ни одна сумма не учитывается дважды,
 * фильтры применяются логическим И ко всем записям.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val carDao = mockk<CarDao>(relaxed = true)
    private val breakdownDao = mockk<BreakdownDao>(relaxed = true)
    private val consumableDao = mockk<ConsumableDao>(relaxed = true)
    private val partDao = mockk<PartDao>(relaxed = true)
    private val accidentDao = mockk<AccidentDao>(relaxed = true)
    private val refuelingDao = mockk<RefuelingDao>(relaxed = true)
    private val expenseDao = mockk<ExpenseDao>(relaxed = true)
    private val documentDao = mockk<CarDocumentDao>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val refuelingRepository = mockk<RefuelingRepository>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    private val now = System.currentTimeMillis()
    private val yesterday = now - TimeUnit.DAYS.toMillis(1)

    private val car = Car(
        id = 1,
        brand = "Lada",
        model = "Vesta",
        year = 2020,
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
        purchaseDate = now - TimeUnit.DAYS.toMillis(365),
        mainPhotoPath = null,
        photosPaths = null,
        notes = null,
        createdAt = now - TimeUnit.DAYS.toMillis(365),
        updatedAt = now
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { carDao.getCarById(1) } returns flowOf(car)
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(emptyList())
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(emptyList())
        every { partDao.getPartsByCarId(1) } returns flowOf(emptyList())
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(emptyList())
        every { expenseDao.getExpensesByCarId(1) } returns flowOf(emptyList())
        every { documentDao.getDocumentsByCarId(1) } returns flowOf(emptyList())
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = StatisticsViewModel(
        accidentDao, breakdownDao, consumableDao, carDao,
        partDao, refuelingDao, expenseDao, documentDao,
        carRepository, refuelingRepository, dataChangeNotifier,
        dispatcher,
        SavedStateHandle(mapOf("carId" to 1L))
    )

    private fun part(id: Long, price: Double, mileage: Int = 90_000) = Part(
        id = id,
        carId = 1,
        name = "Деталь $id",
        installDate = yesterday,
        installMileage = mileage,
        installationType = "ДТП",
        price = price
    )

    private fun accident(partIds: List<Long>?, repairCost: Double, mileage: Int = 90_000) = Accident(
        id = 1,
        carId = 1,
        date = yesterday,
        mileage = mileage,
        location = null,
        damageDescription = "Бампер",
        severity = "Средняя",
        isUserAtFault = false,
        osagoPayout = null,
        kaskoPayout = null,
        culpritPayout = null,
        installedPartIds = partIds,
        repairCost = repairCost,
        photosPaths = null,
        documentPath = null,
        notes = null
    )

    private fun breakdown(partIds: List<Long>?, partsCost: Double, mileage: Int = 90_000) = Breakdown(
        id = 1,
        carId = 1,
        title = "Ремонт",
        description = "Описание",
        breakdownDate = yesterday,
        breakdownMileage = mileage,
        maintenanceType = "REPAIR",
        installedPartIds = partIds,
        partsCost = partsCost,
        totalCost = partsCost
    )

    /** Регрессия: запчасти из ДТП считались и в «Ремонтах», и в «ДТП» через repairCost */
    @Test
    fun `запчасти из ДТП не учитываются вторично в ремонтах`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 5_000.0)))
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = listOf(10), repairCost = 5_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        // Только стоимость ДТП: 5000, а не 5000 (ДТП) + 5000 (запчасть)
        assertEquals(5_000.0, general.totalCost, 0.01)
        assertEquals(
            listOf("ДТП"),
            general.costDistribution.map { it.category }
        )
    }

    /** Стоимость за км считается без ДТП — запчасти аварии не должны в неё протекать */
    @Test
    fun `стоимость за км не включает ремонт по ДТП`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 5_000.0)))
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = listOf(10), repairCost = 5_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0.0, vm.uiState.value.statistics.general!!.costPerKm, 0.0001)
    }

    @Test
    fun `запчасти из обслуживания не учитываются вторично`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 3_000.0)))
        every { breakdownDao.getBreakdownsByCarId(1) } returns
            flowOf(listOf(breakdown(partIds = listOf(10), partsCost = 3_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(3_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    @Test
    fun `отдельная запчасть учитывается в ремонтах`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 2_500.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(2_500.0, general.totalCost, 0.01)
        assertEquals(listOf("Ремонты"), general.costDistribution.map { it.category })
    }

    /** Регрессия: ДТП не проходили фильтр по пробегу и попадали в расчёт целиком */
    @Test
    fun `ДТП вне диапазона пробега исключается фильтром`() = runTest(dispatcher) {
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 50_000.0, mileage = 10_000)))

        val vm = viewModel()
        advanceUntilIdle()

        vm.setMileageFilter(MileageFilter.CustomRange(80_000, 100_000))
        advanceUntilIdle()

        // Авария на 10 000 км в диапазон не входит — расходов нет вообще
        assertEquals(null, vm.uiState.value.statistics.general)
    }

    @Test
    fun `исключение ДТП убирает аварии из расчёта`() = runTest(dispatcher) {
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 50_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(50_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        vm.toggleExcludeAccidents()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.statistics.general)
    }

    private fun consumable(
        id: Long,
        category: String,
        cost: Double,
        customName: String? = null,
        linkedMaintenanceId: Long? = null
    ) = Consumable(
        id = id,
        carId = 1,
        category = category,
        customName = customName,
        manufacturer = null,
        articleNumber = null,
        installationMileage = 90_000,
        installationDate = yesterday,
        linkedMaintenanceId = linkedMaintenanceId,
        replacementMileage = null,
        replacementDate = null,
        cost = cost,
        isInstalledAtService = false,
        serviceCost = null,
        volume = null,
        replacementIntervalMileage = null,
        replacementIntervalDays = null,
        isActive = true,
        notes = null,
        createdAt = yesterday,
        updatedAt = yesterday
    )

    /**
     * Разовая позиция ТО («Другое») не имеет категории с напоминаниями, поэтому в статистику
     * расходников не идёт — её деньги уже посчитаны в стоимости самого ТО.
     */
    @Test
    fun `позиция «Другое» не попадает в статистику расходников`() = runTest(dispatcher) {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(1, "Фильтр масляный", cost = 900.0),
                consumable(
                    2, ConsumableCategories.OTHER, cost = 500.0,
                    customName = "Герметик", linkedMaintenanceId = 7
                )
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val consumables = vm.uiState.value.statistics.consumables!!
        assertEquals(900.0, consumables.totalConsumablesCost, 0.01)
        assertEquals(
            listOf("Фильтр масляный"),
            consumables.averagePerCategory.map { it.category }
        )
    }

    @Test
    fun `из одних позиций «Другое» статистики расходников нет`() = runTest(dispatcher) {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(consumable(1, ConsumableCategories.OTHER, cost = 500.0, customName = "Хомуты"))
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.statistics.consumables)
    }

    private fun refueling(
        id: Long,
        mileage: Int,
        liters: Double,
        cost: Double = 1_000.0,
        isResetPoint: Boolean = false
    ) = Refueling(
        id = id,
        carId = 1,
        // Порядок дат совпадает с порядком пробега, все заправки — в пределах периода
        date = now - TimeUnit.DAYS.toMillis((110_000L - mileage) / 100),
        mileage = mileage,
        liters = liters,
        fuelType = "АИ-95",
        totalCost = cost,
        isConsumptionResetPoint = isResetPoint
    )

    /**
     * Бак первой заправки сгорел до неё: на пройденной дистанции его нет, иначе среднее
     * завышается на целый бак (§6.4 бизнес-логики).
     */
    @Test
    fun `средний расход не считает бак первой заправки`() = runTest(dispatcher) {
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(
            listOf(
                refueling(1, 90_000, 50.0),
                refueling(2, 90_500, 45.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        // 45 л на 500 км = 9 л/100км (а не (50 + 45) / 500)
        val fuel = vm.uiState.value.statistics.fuel!!
        assertEquals(9.0, fuel.fuelTypes.single().averageConsumption, 0.0001)
    }

    /** Пропущенная заправка сбивает среднее — расчёт начинается с новой точки отсчёта */
    @Test
    fun `точка отсчёта ограничивает средний расход, но не литры и деньги`() = runTest(dispatcher) {
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(
            listOf(
                // «Испорченный» участок: заправку между ними пользователь забыл добавить
                refueling(1, 80_000, 50.0, cost = 3_000.0),
                refueling(2, 85_000, 50.0, cost = 3_000.0),
                // Новая точка отсчёта и заправка после неё
                refueling(3, 90_000, 50.0, cost = 3_000.0, isResetPoint = true),
                refueling(4, 90_500, 45.0, cost = 2_000.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val type = vm.uiState.value.statistics.fuel!!.fuelTypes.single()
        // 45 л на 500 км — участок до точки отсчёта (1 л/100км) в среднее не идёт
        assertEquals(9.0, type.averageConsumption, 0.0001)
        assertEquals("литры остаются за всю историю", 195.0, type.totalLiters, 0.0001)
        assertEquals("деньги остаются за всю историю", 11_000.0, type.totalCost, 0.01)
    }

    @Test
    fun `без точки отсчёта средний расход считается по всей истории`() = runTest(dispatcher) {
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(
            listOf(
                refueling(1, 90_000, 50.0),
                refueling(2, 90_500, 45.0),
                refueling(3, 91_000, 45.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val fuel = vm.uiState.value.statistics.fuel!!
        // (45 + 45) л на 1000 км = 9 л/100км
        assertEquals(9.0, fuel.fuelTypes.single().averageConsumption, 0.0001)
        assertEquals(null, fuel.consumptionSince)
    }

    /** Регрессия: помесячные точки брались от «сегодня», и траты из начала диапазона пропадали */
    @Test
    fun `тренд расходов покрывает весь диапазон периода`() = runTest(dispatcher) {
        val almostThreeMonthsAgo = now - TimeUnit.DAYS.toMillis(85)
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(
            listOf(
                breakdown(partIds = null, partsCost = 1_000.0).copy(
                    id = 2,
                    breakdownDate = almostThreeMonthsAgo
                )
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        vm.setPeriod(StatisticsPeriod.THREE_MONTHS)
        advanceUntilIdle()

        val trend = vm.uiState.value.statistics.general!!.costTrend
        assertEquals(
            "трата из начала диапазона должна попасть на график",
            1_000.0,
            trend.sumOf { it.amount },
            0.01
        )
    }
}
