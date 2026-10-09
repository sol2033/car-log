package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ConsumableRepository
import com.carlog.data.repository.OilTopUpRepository
import com.carlog.domain.model.OilTopUp
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Долив с плитки масла в списке расходников делает то же, что и с экрана масла:
 * запись, пробег машины и уведомление для авто-бэкапа (правило 5 из CLAUDE.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsumablesTopUpTest {

    private val dispatcher = StandardTestDispatcher()
    private val consumableRepository = mockk<ConsumableRepository>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val preferences = mockk<ConsumablePreferences>(relaxed = true)
    private val oilTopUpRepository = mockk<OilTopUpRepository>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ConsumablesViewModel(
        consumableRepository, carRepository, preferences, oilTopUpRepository,
        appPreferences, dataChangeNotifier, SavedStateHandle(mapOf("carId" to 7L))
    )

    @Test
    fun `долив с плитки сохраняется, двигает пробег и запускает авто-бэкап`() = runTest(dispatcher) {
        val saved = slot<OilTopUp>()
        val vm = viewModel()

        vm.addTopUp(date = 1_000L, mileage = 101_500, volume = 0.5, cost = 450.0)
        advanceUntilIdle()

        coVerify { oilTopUpRepository.insertTopUp(capture(saved)) }
        assertEquals(7L, saved.captured.carId)
        assertEquals(101_500, saved.captured.mileage)
        assertEquals(0.5, saved.captured.volume, 0.0001)
        assertEquals(450.0, saved.captured.cost!!, 0.0001)
        coVerifyOrder {
            oilTopUpRepository.insertTopUp(any())
            carRepository.updateCarMileageIfNeeded(7L, 101_500)
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
