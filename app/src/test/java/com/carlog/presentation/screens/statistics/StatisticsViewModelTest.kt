package com.carlog.presentation.screens.statistics

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.data.local.dao.TollPaymentDao
import com.carlog.data.preferences.AppPreferences
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.domain.model.OilTopUp
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.RefuelingRepository
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.Fine
import com.carlog.domain.model.FineViolationTypes
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.MileageFilter
import com.carlog.domain.model.Part
import com.carlog.domain.model.Refueling
import com.carlog.domain.model.StatisticsPeriod
import com.carlog.domain.model.TollPayment
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val fineDao = mockk<FineDao>(relaxed = true)
    private val oilTopUpDao = mockk<OilTopUpDao>(relaxed = true)
    private val tollPaymentDao = mockk<TollPaymentDao>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val refuelingRepository = mockk<RefuelingRepository>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    /** Реальный счётчик изменений: по нему статистика сама пересчитывается */
    private val dataVersion = MutableStateFlow(0L)

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
        every { dataChangeNotifier.dataVersion } returns dataVersion
        every { carDao.getCarById(1) } returns flowOf(car)
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(emptyList())
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(emptyList())
        every { partDao.getPartsByCarId(1) } returns flowOf(emptyList())
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(emptyList())
        every { expenseDao.getExpensesByCarId(1) } returns flowOf(emptyList())
        every { documentDao.getDocumentsByCarId(1) } returns flowOf(emptyList())
        every { fineDao.getFinesByCarId(1) } returns flowOf(emptyList())
        every { oilTopUpDao.getTopUpsByCarId(1) } returns flowOf(emptyList())
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns flowOf(emptyList())
        every { appPreferences.oilConsumptionEnabled } returns flowOf(true)
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = StatisticsViewModel(
        accidentDao, breakdownDao, consumableDao, carDao,
        partDao, refuelingDao, expenseDao, documentDao, fineDao, tollPaymentDao, oilTopUpDao, appPreferences,
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

    private fun accident(
        partIds: List<Long>?,
        repairCost: Double,
        mileage: Int = 90_000,
        kaskoPayout: Double? = null,
        compensationPaid: Double? = null
    ) = Accident(
        id = 1,
        carId = 1,
        date = yesterday,
        mileage = mileage,
        location = null,
        damageDescription = "Бампер",
        severity = "Средняя",
        isUserAtFault = false,
        osagoPayout = null,
        kaskoPayout = kaskoPayout,
        culpritPayout = null,
        installedPartIds = partIds,
        repairCost = repairCost,
        compensationPaid = compensationPaid,
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

    /**
     * Регрессия: со статистики можно уйти вперёд — в «Обслуживание» и «Расходники».
     * ViewModel остаётся в бэкстеке, данные читаются снимком, и после возврата цифры
     * оставались прежними: человек видел неверные суммы, не понимая, что они устарели.
     */
    @Test
    fun `статистика пересчитывается после изменения данных на другом экране`() = runTest(dispatcher) {
        // Пустой набор дал бы general == null — начинаем с одной запчасти
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0)))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        // Пользователь ушёл в «Обслуживание» и добавил там запчасть подороже
        every { partDao.getPartsByCarId(1) } returns
            flowOf(listOf(part(10, 1_000.0), part(11, 3_000.0)))
        dataVersion.value = dataVersion.value + 1
        advanceUntilIdle()

        // До правки здесь осталась бы прежняя 1000: снимок данных не перечитывался
        assertEquals(4_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    /** Пересчёт по возврату не должен мигать индикатором: экран уже показывает данные */
    @Test
    fun `фоновый пересчёт не поднимает индикатор загрузки`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        dataVersion.value = dataVersion.value + 1
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isLoading)
    }

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

    /**
     * Регрессия: при «Исключить ДТП» запчасти аварии теряли связь с ней и считались
     * отдельными — их цены всплывали в «Ремонтах» и в стоимости километра.
     */
    @Test
    fun `исключение ДТП не переносит его запчасти в ремонты`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 30_000.0)))
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = listOf(10), repairCost = 50_000.0)))

        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleExcludeAccidents()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.statistics.general)
        assertEquals(null, vm.uiState.value.statistics.repairs)
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

    private fun topUp(id: Long, cost: Double?, mileage: Int = 90_500) = OilTopUp(
        id = id, carId = 1, date = yesterday, mileage = mileage, volume = 0.5, cost = cost
    )

    /** Долив — трата на расходники: идёт в «ТО» общей статистики (§5 бизнес-логики) */
    @Test
    fun `деньги за долив масла идут в ТО`() = runTest(dispatcher) {
        every { oilTopUpDao.getTopUpsByCarId(1) } returns flowOf(listOf(topUp(1, 450.0), topUp(2, 550.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(listOf("ТО"), general.costDistribution.map { it.category })
        assertEquals(1_000.0, general.totalCost, 0.01)
    }

    /**
     * Во вкладке «Расходники» доливы — отдельная категория: в «Масле в двигателе» они
     * считались бы заменами и сбили бы среднюю стоимость ТО
     */
    @Test
    fun `долив — отдельная категория расходников и не сбивает среднюю стоимость ТО`() = runTest(dispatcher) {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(consumable(1, OilConsumptionCalculator.ENGINE_OIL, cost = 3_000.0))
        )
        every { oilTopUpDao.getTopUpsByCarId(1) } returns flowOf(listOf(topUp(1, 500.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val consumables = vm.uiState.value.statistics.consumables!!
        assertEquals(3_500.0, consumables.totalConsumablesCost, 0.01)
        assertEquals(
            setOf(OilConsumptionCalculator.ENGINE_OIL, OilConsumptionCalculator.TOP_UP_COST_CATEGORY),
            consumables.categoryDistribution.map { it.category }.toSet()
        )
        assertEquals("одна замена масла — 3000, долив не замена", 3_000.0, consumables.averageMaintenanceCost, 0.01)
    }

    /** Выключенный учёт прячет блок расхода, но деньги остаются — это реальные траты */
    @Test
    fun `выключенный учёт масла прячет расход, но не деньги`() = runTest(dispatcher) {
        every { appPreferences.oilConsumptionEnabled } returns flowOf(false)
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(consumable(1, OilConsumptionCalculator.ENGINE_OIL, cost = 3_000.0))
        )
        every { oilTopUpDao.getTopUpsByCarId(1) } returns flowOf(listOf(topUp(1, 500.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val statistics = vm.uiState.value.statistics
        assertEquals(null, statistics.oil)
        assertEquals(3_500.0, statistics.consumables!!.totalConsumablesCost, 0.01)
    }

    @Test
    fun `включённый учёт масла даёт расход по интервалам`() = runTest(dispatcher) {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(consumable(1, OilConsumptionCalculator.ENGINE_OIL, cost = 3_000.0))
        )
        every { oilTopUpDao.getTopUpsByCarId(1) } returns flowOf(listOf(topUp(1, 500.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val oil = vm.uiState.value.statistics.oil!!
        // Масло с 90 000, текущий пробег 100 000, долито 0,5 л
        assertEquals(0.05, oil.current!!.per1000Km!!, 0.0001)
    }

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
        assertEquals(9.0, fuel.fuelTypes.single().summary.consumption!!.per100Km, 0.0001)
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
        assertEquals(9.0, type.summary.consumption!!.per100Km, 0.0001)
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
        assertEquals(9.0, fuel.fuelTypes.single().summary.consumption!!.per100Km, 0.0001)
        assertEquals(null, fuel.consumptionSince)
    }

    /** Вкладка «Топливо» и экран заправок считают пробег на баке одним расчётом */
    @Test
    fun `пробег на баке считается по объёму бака из карточки машины`() = runTest(dispatcher) {
        every { carDao.getCarById(1) } returns flowOf(car.copy(tankVolume = 45.0))
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(
            listOf(
                refueling(1, 90_000, 50.0),
                refueling(2, 90_500, 45.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val summary = vm.uiState.value.statistics.fuel!!.fuelTypes.single().summary
        // 45 л при 9 л/100км = 500 км
        assertEquals(500.0, summary.rangeKm!!, 0.0001)
    }

    /** Машина, заведённая до 1.4.0: объёма нет — пробег на баке не выдумывается */
    @Test
    fun `без объёма бака пробег на баке не считается`() = runTest(dispatcher) {
        every { refuelingDao.getRefuelingsByCarId(1) } returns flowOf(
            listOf(
                refueling(1, 90_000, 50.0),
                refueling(2, 90_500, 45.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val summary = vm.uiState.value.statistics.fuel!!.fuelTypes.single().summary
        assertEquals(null, summary.tankCapacity)
        assertEquals(null, summary.rangeKm)
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

    /**
     * Регрессия: у периода «Месяц» было ровно 4 недели от начала диапазона — траты текущей
     * недели попадали в четвёртую, которая заканчивалась раньше «сегодня».
     */
    @Test
    fun `трата текущей недели попадает в текущую неделю графика за месяц`() = runTest(dispatcher) {
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns flowOf(listOf(toll(amount = 300.0, date = now)))

        val vm = viewModel()
        advanceUntilIdle()
        vm.setPeriod(StatisticsPeriod.MONTH)
        advanceUntilIdle()

        val today = java.time.LocalDate.now()
        val currentMonday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val point = vm.uiState.value.statistics.general!!.costTrend.single { it.amount > 0 }
        assertEquals(currentMonday, point.date)
        assertEquals(300.0, point.amount, 0.01)
    }

    /**
     * Регрессия: «Год» строился за последние 12 месяцев, а месяц относился к ближайшему
     * началу блока — последний месяц каждого блока уезжал в соседний столбец.
     */
    @Test
    fun `год делится на блоки календарного года, трата попадает в свой блок`() = runTest(dispatcher) {
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns flowOf(listOf(toll(amount = 300.0, date = now)))

        val vm = viewModel()
        advanceUntilIdle()
        vm.setPeriod(StatisticsPeriod.YEAR)
        advanceUntilIdle()

        val today = java.time.LocalDate.now()
        val yearStart = java.time.LocalDate.of(today.year, 1, 1)
        val trend = vm.uiState.value.statistics.general!!.costTrend
        assertEquals(
            listOf(yearStart, yearStart.plusMonths(4), yearStart.plusMonths(8)),
            trend.map { it.date }
        )
        val expectedBlock = yearStart.plusMonths(((today.monthValue - 1) / 4 * 4).toLong())
        assertEquals(expectedBlock, trend.single { it.amount > 0 }.date)
    }

    /**
     * Регрессия: график и «самый дорогой месяц» складывали полную стоимость ТО (в ней уже
     * его расходники) и ещё раз каждый расходник — столбики давали больше, чем «Всего потрачено».
     */
    @Test
    fun `расходники ТО не задваиваются на графике расходов`() = runTest(dispatcher) {
        every { breakdownDao.getBreakdownsByCarId(1) } returns flowOf(
            listOf(
                breakdown(partIds = null, partsCost = 3_000.0).copy(
                    maintenanceType = "SCHEDULED_SERVICE",
                    linkedConsumableIds = listOf(5L),
                    serviceCost = 1_000.0,
                    totalCost = 4_000.0
                )
            )
        )
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(5, "Масло в двигателе", 3_000.0, linkedMaintenanceId = 1),
                consumable(6, "Фильтр салонный", 500.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(4_500.0, general.totalCost, 0.01)
        assertEquals(
            "сумма столбиков графика равна общей сумме расходов",
            general.totalCost,
            general.costTrend.sumOf { it.amount },
            0.01
        )
    }

    /**
     * Регрессия: работа сервиса в карточке отдельного расходника («установлено в сервисе»)
     * не попадала в расходы вовсе — ни в «Услуги сервисов», ни в общую сумму.
     */
    @Test
    fun `работа сервиса по отдельному расходнику идёт в услуги сервисов`() = runTest(dispatcher) {
        every { consumableDao.getConsumablesByCarId(1) } returns flowOf(
            listOf(
                consumable(6, "Фильтр салонный", 500.0).copy(isInstalledAtService = true, serviceCost = 300.0),
                // У расходника внутри ТО своих работ нет: они в стоимости работ события
                consumable(7, "Масло в двигателе", 3_000.0, linkedMaintenanceId = 1).copy(serviceCost = 999.0)
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(800.0, general.totalCost, 0.01)
        assertEquals(300.0, general.costDistribution.single { it.category == "Услуги сервисов" }.amount, 0.01)
        assertEquals(general.totalCost, general.costTrend.sumOf { it.amount }, 0.01)
    }

    // === Штрафы (§4.8 бизнес-логики) ===

    private fun fine(
        id: Long = 1,
        amount: Double,
        isPaid: Boolean = false,
        paidAmount: Double? = null,
        date: Long = yesterday
    ) = Fine(
        id = id,
        carId = 1,
        date = date,
        amount = amount,
        violationType = FineViolationTypes.SPEEDING,
        isPaid = isPaid,
        paidAmount = paidAmount
    )

    @Test
    fun `оплаченный штраф попадает в общие расходы отдельной категорией`() = runTest(dispatcher) {
        every { fineDao.getFinesByCarId(1) } returns flowOf(listOf(fine(amount = 1_500.0, isPaid = true)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(1_500.0, general.totalCost, 0.01)
        assertEquals(listOf("Штрафы"), general.costDistribution.map { it.category })
    }

    @Test
    fun `неоплаченный штраф в расходы не идёт`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0)))
        every { fineDao.getFinesByCarId(1) } returns flowOf(listOf(fine(amount = 5_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        // Только запчасть: штраф выписан, но денег за него ещё не платили
        assertEquals(1_000.0, general.totalCost, 0.01)
        assertEquals(listOf("Ремонты"), general.costDistribution.map { it.category })
    }

    @Test
    fun `в расходы идёт фактически уплаченная сумма, а не выписанная`() = runTest(dispatcher) {
        every { fineDao.getFinesByCarId(1) } returns
            flowOf(listOf(fine(amount = 5_000.0, isPaid = true, paidAmount = 2_500.0)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2_500.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    /** Штраф — не стоимость эксплуатации: как и ДТП, он исключён из стоимости километра */
    @Test
    fun `стоимость за км не включает штрафы`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0, mileage = 90_000)))
        every { fineDao.getFinesByCarId(1) } returns
            flowOf(listOf(fine(amount = 9_000.0, isPaid = true)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(10_000.0, general.totalCost, 0.01)
        // Диапазон пробега по одной записи нулевой — сюда важно, что штраф не поднял числитель
        assertEquals(0.0, general.costPerKm, 0.0001)
    }

    /** У штрафа нет пробега — при фильтре по пробегу он выпадает целиком, как документы */
    @Test
    fun `фильтр по пробегу исключает штрафы`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0, mileage = 95_000)))
        every { fineDao.getFinesByCarId(1) } returns
            flowOf(listOf(fine(amount = 2_000.0, isPaid = true)))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(3_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        vm.setMileageFilter(MileageFilter.Last10k)
        advanceUntilIdle()

        assertEquals(1_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    @Test
    fun `штраф вне выбранного периода в расходы не попадает`() = runTest(dispatcher) {
        val longAgo = now - TimeUnit.DAYS.toMillis(200)
        every { fineDao.getFinesByCarId(1) } returns
            flowOf(listOf(fine(amount = 2_000.0, isPaid = true, date = longAgo)))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        vm.setPeriod(StatisticsPeriod.MONTH)
        advanceUntilIdle()

        // Данных в периоде не осталось вовсе — блок общей статистики пуст
        assertEquals(null, vm.uiState.value.statistics.general)
    }

    // === Платные дороги (§4.9 бизнес-логики) ===

    private fun toll(id: Long = 1, amount: Double, date: Long = yesterday) =
        TollPayment(id = id, carId = 1, date = date, amount = amount, roadName = "М-11")

    @Test
    fun `платные дороги идут в общие расходы отдельной категорией`() = runTest(dispatcher) {
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns
            flowOf(listOf(toll(1, 500.0), toll(2, 700.0)))
        every { fineDao.getFinesByCarId(1) } returns flowOf(listOf(fine(amount = 1_500.0, isPaid = true)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(2_700.0, general.totalCost, 0.01)
        // Со штрафами не смешиваются: это разные по смыслу деньги
        assertEquals(listOf("Штрафы", "Платные дороги"), general.costDistribution.map { it.category })
        assertEquals(1_200.0, general.costDistribution.single { it.category == "Платные дороги" }.amount, 0.01)
    }

    /** Пробега у проезда нет — к километрам его не привязать, как и штраф */
    @Test
    fun `стоимость за км не включает платные дороги`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(
            listOf(part(10, 1_000.0, mileage = 90_000), part(11, 1_000.0, mileage = 91_000))
        )
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns flowOf(listOf(toll(amount = 50_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(52_000.0, general.totalCost, 0.01)
        // 2 000 ₽ запчастей на 1 000 км; без исключения было бы 52 ₽/км
        assertEquals(2.0, general.costPerKm, 0.0001)
    }

    @Test
    fun `фильтр по пробегу исключает платные дороги`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0, mileage = 95_000)))
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns flowOf(listOf(toll(amount = 400.0)))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1_400.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        vm.setMileageFilter(MileageFilter.Last10k)
        advanceUntilIdle()

        assertEquals(1_000.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    @Test
    fun `проезд вне выбранного периода в расходы не попадает`() = runTest(dispatcher) {
        // Свежий проезд — «сейчас», а не вчера: первого числа вчера был бы уже другой месяц
        val longAgo = now - TimeUnit.DAYS.toMillis(200)
        every { tollPaymentDao.getTollPaymentsByCarId(1) } returns
            flowOf(listOf(toll(1, 300.0, date = now), toll(2, 900.0, date = longAgo)))

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1_200.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)

        vm.setPeriod(StatisticsPeriod.MONTH)
        advanceUntilIdle()

        assertEquals(300.0, vm.uiState.value.statistics.general!!.totalCost, 0.01)
    }

    // region деньги по ДТП

    @Test
    fun `выплаченная компенсация идёт в расходы по ДТП`() = runTest(dispatcher) {
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 20_000.0, compensationPaid = 30_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(50_000.0, general.totalCost, 0.01)
        assertEquals(listOf("ДТП"), general.costDistribution.map { it.category })
    }

    @Test
    fun `полученная выплата уменьшает расходы по ДТП`() = runTest(dispatcher) {
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 50_000.0, kaskoPayout = 30_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            "из кармана ушло только то, что не закрыла страховая",
            20_000.0,
            vm.uiState.value.statistics.general!!.totalCost,
            0.01
        )
    }

    /**
     * Выплата больше ремонта не делает аварию источником дохода: иначе одно щедро
     * закрытое ДТП уменьшало бы стоимость заправок и обслуживания.
     */
    @Test
    fun `выплата больше ремонта не уводит расходы в минус`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0)))
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 20_000.0, kaskoPayout = 100_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals("осталась только запчасть", 1_000.0, general.totalCost, 0.01)
        assertEquals(listOf("Ремонты"), general.costDistribution.map { it.category })
    }

    /** ДТП не следует из пробега — как и раньше, в стоимость километра оно не входит */
    @Test
    fun `компенсация не попадает в стоимость за км`() = runTest(dispatcher) {
        every { partDao.getPartsByCarId(1) } returns flowOf(listOf(part(10, 1_000.0, mileage = 90_000)))
        every { accidentDao.getAccidentsByPeriod(1, any(), any()) } returns
            flowOf(listOf(accident(partIds = null, repairCost = 0.0, compensationPaid = 90_000.0)))

        val vm = viewModel()
        advanceUntilIdle()

        val general = vm.uiState.value.statistics.general!!
        assertEquals(91_000.0, general.totalCost, 0.01)
        // Диапазон пробега по одной записи нулевой — важно, что компенсация не подняла числитель
        assertEquals(0.0, general.costPerKm, 0.0001)
    }

    // endregion
}
