package com.carlog.presentation.screens.expenses

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarRepository
import com.carlog.data.repository.ExpenseRepository
import com.carlog.data.repository.NameSuggestionRepository
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test

/**
 * Выбор сервиса из подсказок подставляет его прошлый адрес — только в пустое поле
 * (§4.10 бизнес-логики). Механика одна во всех трёх формах; проверяем на прочем расходе.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceAddressPickTest {

    private val dispatcher = StandardTestDispatcher()
    private val expenseRepository = mockk<ExpenseRepository>(relaxed = true)
    private val carRepository = mockk<CarRepository>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)
    private val suggestions = mockk<NameSuggestionRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { carRepository.getCarById(1) } returns flowOf(null)
        every { suggestions.serviceNameSuggestions(any()) } returns flowOf(emptyList())
        coEvery { suggestions.lastServiceAddress("Мойка у дома") } returns "ул. Садовая, 1"
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AddExpenseViewModel(
        expenseRepository, carRepository, dataChangeNotifier, suggestions,
        SavedStateHandle(mapOf("carId" to 1L))
    )

    @Test
    fun `выбор сервиса подставляет его адрес в пустое поле`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.pickServiceNameSuggestion("Мойка у дома")
        advanceUntilIdle()

        assertEquals("Мойка у дома", vm.serviceName.value)
        assertEquals("ул. Садовая, 1", vm.serviceAddress.value)
    }

    @Test
    fun `введённый адрес не перетирается`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateServiceAddress("пр. Мира, 10")

        vm.pickServiceNameSuggestion("Мойка у дома")
        advanceUntilIdle()

        assertEquals("пр. Мира, 10", vm.serviceAddress.value)
        coVerify(exactly = 0) { suggestions.lastServiceAddress(any()) }
    }

    /** Адрес ищется асинхронно — набранное за это время пользователем важнее подстановки */
    @Test
    fun `адрес, набранный пока шёл поиск, не перетирается`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.pickServiceNameSuggestion("Мойка у дома")
        vm.updateServiceAddress("свой адрес")
        advanceUntilIdle()

        assertEquals("свой адрес", vm.serviceAddress.value)
    }

    @Test
    fun `у сервиса без сохранённого адреса поле остаётся пустым`() = runTest(dispatcher) {
        coEvery { suggestions.lastServiceAddress("Новый сервис") } returns null
        val vm = viewModel()

        vm.pickServiceNameSuggestion("Новый сервис")
        advanceUntilIdle()

        assertEquals("", vm.serviceAddress.value)
    }

    @Test
    fun `обычный ввод названия адрес не трогает`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.updateServiceName("Мойка у дома")
        advanceUntilIdle()

        assertEquals("", vm.serviceAddress.value)
    }

    /**
     * Регрессия: исключение при записи в базу уходило из корутины необработанным —
     * приложение падало вместе с набранной формой. Теперь форма остаётся, видна ошибка,
     * и сохранить можно снова.
     */
    @Test
    fun `сбой базы при сохранении показывает ошибку, а не роняет приложение`() = runTest(dispatcher) {
        coEvery { expenseRepository.insertExpense(any()) } throws IllegalStateException("database is locked")
        val vm = viewModel()
        vm.updateMileage("90000")
        vm.updateTitle("Мойка")
        vm.updateCost("500")

        vm.saveExpense()
        advanceUntilIdle()

        assertEquals("database is locked", vm.saveError.value)
        assertEquals(false, vm.isSaving.value)
        assertEquals("запись не считается сохранённой", false, vm.saveSuccess.value)
    }
}
