package com.carlog.presentation.screens.parts

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Part
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

/**
 * Запчасть, отмеченная в событии как скрытая, не показывается в модуле «Запчасти»
 * (§4.1 бизнес-логики). Сама запись при этом остаётся: её стоимость учтена в событии.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartsVisibilityTest {

    private val dispatcher = StandardTestDispatcher()

    private val partRepository = mockk<PartRepository>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    private fun part(id: Long, name: String, visible: Boolean) = Part(
        id = id,
        carId = 1,
        name = name,
        installDate = 0,
        installMileage = 90_000,
        installationType = "Сервис",
        price = 1_000.0,
        showInPartsList = visible
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { carRepository.getCarById(1) } returns flowOf(null)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `скрытая запчасть не попадает в список модуля`() = runTest(dispatcher) {
        every { partRepository.getPartsByCarId(1) } returns flowOf(
            listOf(
                part(1, "Стойка амортизатора", visible = true),
                part(2, "Прокладка двигателя", visible = false)
            )
        )

        val viewModel = PartsViewModel(
            partRepository, carRepository, dataChangeNotifier,
            SavedStateHandle(mapOf("carId" to 1L))
        )
        advanceUntilIdle()

        assertEquals(
            listOf("Стойка амортизатора"),
            viewModel.uiState.value.parts.map { it.name }
        )
    }
}
