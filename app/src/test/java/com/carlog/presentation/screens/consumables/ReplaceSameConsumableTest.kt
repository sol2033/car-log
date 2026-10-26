package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.OilTopUpRepository
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * «Заменить на такой же» на экране расходника (§4.4 бизнес-логики).
 *
 * Регрессия: новый расходник копировался со старого вместе с привязкой к ТО. Он показывался
 * в карточке старого ТО, но в его список не попадал — и его стоимость не считалась
 * в статистике нигде (не «отдельный» и не внутри стоимости ТО).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplaceSameConsumableTest {

    private val dispatcher = StandardTestDispatcher()
    private val consumableRepository = mockk<ConsumableRepository>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val oilTopUpRepository = mockk<OilTopUpRepository>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    private val car = Car(
        id = 1, brand = "Lada", model = "Vesta", year = null, color = null,
        licensePlate = null, vin = null, engineModel = null, engineVolume = null,
        transmissionType = null, driveType = null, bodyType = null, fuelType = "Бензин",
        hasGasEquipment = false, gasType = null, currentMileage = 100_000,
        purchaseMileage = null, purchaseDate = null, mainPhotoPath = null,
        photosPaths = null, notes = null, createdAt = 0, updatedAt = 0
    )

    /** Фильтр, поставленный в ТО №5 */
    private val fromMaintenance = Consumable(
        id = 20, carId = 1, category = "Фильтр салонный", manufacturer = "Mann",
        articleNumber = "CU 123", installationMileage = 90_000, installationDate = 0,
        linkedMaintenanceId = 5, replacementMileage = null, replacementDate = null,
        cost = 800.0, isInstalledAtService = false, serviceCost = null, volume = null,
        replacementIntervalMileage = 15_000, replacementIntervalDays = 365, isActive = true,
        notes = null, createdAt = 0, updatedAt = 0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { consumableRepository.getConsumableById(20) } returns flowOf(fromMaintenance)
        every { carRepository.getCarById(1) } returns flowOf(car)
        every { consumableRepository.getConsumablesByCategory(1, any()) } returns flowOf(emptyList())
        every { oilTopUpRepository.getTopUpsByCarId(1) } returns flowOf(emptyList())
        every { appPreferences.oilConsumptionEnabled } returns flowOf(false)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `новый расходник после замены — отдельный, без привязки к старому ТО`() = runTest(dispatcher) {
        val vm = ConsumableDetailViewModel(
            consumableRepository, carRepository, oilTopUpRepository, appPreferences, dataChangeNotifier,
            SavedStateHandle(mapOf("consumableId" to 20L, "carId" to 1L))
        )
        advanceUntilIdle()

        var replaced = false
        vm.replaceSameConsumable(
            newCost = 900.0,
            isInstalledAtService = false,
            serviceCost = null,
            replacementDate = 1_000,
            onReplaced = { replaced = true }
        )
        advanceUntilIdle()

        assertTrue(replaced)
        val inserted = mutableListOf<Consumable>()
        coVerify { consumableRepository.insertConsumable(capture(inserted)) }
        val created = inserted.single()
        assertNull("привязка к старому ТО не наследуется", created.linkedMaintenanceId)
        assertEquals(900.0, created.cost!!, 0.001)
        assertEquals(100_000, created.installationMileage)
        assertTrue(created.isActive)
        // Остальное — как у старого: производитель, артикул, интервалы
        assertEquals("CU 123", created.articleNumber)
        assertEquals(15_000, created.replacementIntervalMileage)

        // Старый остаётся позицией своего ТО и уходит в историю
        coVerify {
            consumableRepository.updateConsumable(match {
                it.id == 20L && !it.isActive && it.linkedMaintenanceId == 5L && it.replacementMileage == 100_000
            })
        }
    }

    /** Регрессия: замена задним числом получала прошлую дату, но сегодняшний пробег */
    @Test
    fun `замена задним числом берёт пробег из диалога`() = runTest(dispatcher) {
        val vm = ConsumableDetailViewModel(
            consumableRepository, carRepository, oilTopUpRepository, appPreferences, dataChangeNotifier,
            SavedStateHandle(mapOf("consumableId" to 20L, "carId" to 1L))
        )
        advanceUntilIdle()

        vm.replaceSameConsumable(
            newCost = 900.0,
            isInstalledAtService = false,
            serviceCost = null,
            replacementDate = 1_000,
            replacementMileage = 95_000,
            onReplaced = {}
        )
        advanceUntilIdle()

        val inserted = mutableListOf<Consumable>()
        coVerify { consumableRepository.insertConsumable(capture(inserted)) }
        assertEquals(95_000, inserted.single().installationMileage)
        coVerify {
            consumableRepository.updateConsumable(match { it.id == 20L && it.replacementMileage == 95_000 })
        }
    }
}
