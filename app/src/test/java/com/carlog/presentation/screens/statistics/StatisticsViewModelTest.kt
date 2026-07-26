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
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.MileageFilter
import com.carlog.domain.model.Part
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
