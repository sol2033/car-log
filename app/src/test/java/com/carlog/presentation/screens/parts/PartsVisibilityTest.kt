package com.carlog.presentation.screens.parts

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.PartRepository
import com.carlog.domain.model.Part
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    @Test
    fun `фильтр «Скрытые» показывает только скрытые`() = runTest(dispatcher) {
        // Единственное место, откуда скрытую позицию видно и можно вернуть в общий список
        every { partRepository.getPartsByCarId(1) } returns flowOf(
            listOf(
                part(1, "Стойка амортизатора", visible = true),
                part(2, "Прокладка двигателя", visible = false),
                part(3, "Хомут", visible = false)
            )
        )

        val viewModel = PartsViewModel(
            partRepository, carRepository, dataChangeNotifier,
            SavedStateHandle(mapOf("carId" to 1L))
        )
        viewModel.setFilter(PartFilter.Hidden)
        advanceUntilIdle()

        assertEquals(
            listOf("Прокладка двигателя", "Хомут"),
            viewModel.uiState.value.parts.map { it.name }.sorted()
        )
    }

    @Test
    fun `скрытая запчасть не всплывает в остальных фильтрах`() = runTest(dispatcher) {
        every { partRepository.getPartsByCarId(1) } returns flowOf(
            listOf(
                part(1, "Рычаг", visible = true).copy(maintenanceType = "REPAIR"),
                part(2, "Прокладка", visible = false).copy(maintenanceType = "REPAIR")
            )
        )

        val viewModel = PartsViewModel(
            partRepository, carRepository, dataChangeNotifier,
            SavedStateHandle(mapOf("carId" to 1L))
        )
        viewModel.setFilter(PartFilter.Repair)
        advanceUntilIdle()

        assertEquals(listOf("Рычаг"), viewModel.uiState.value.parts.map { it.name })
    }

    @Test
    fun `переключение видимости из карточки меняет флаг и будит авто-бэкап`() = runTest(dispatcher) {
        val hidden = part(2, "Прокладка двигателя", visible = false)
        every { partRepository.getPartById(2) } returns flowOf(hidden)
        every { carRepository.getCarById(any()) } returns flowOf(null)

        val viewModel = PartDetailViewModel(
            partRepository, carRepository, dataChangeNotifier,
            SavedStateHandle(mapOf("carId" to 1L, "partId" to 2L))
        )
        viewModel.toggleVisibility(hidden)
        advanceUntilIdle()

        val saved = slot<Part>()
        coVerify { partRepository.updatePart(capture(saved)) }
        assertEquals(true, saved.captured.showInPartsList)
        // Правило 5: данные изменились — авто-бэкап должен об этом узнать
        coVerify { dataChangeNotifier.notifyDataChanged() }
    }
}
