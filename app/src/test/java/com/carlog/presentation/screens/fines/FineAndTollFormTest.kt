package com.carlog.presentation.screens.fines

import androidx.lifecycle.SavedStateHandle
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.FineRepository
import com.carlog.data.repository.TollPaymentRepository
import com.carlog.domain.model.Fine
import com.carlog.domain.model.TollPayment
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Формы штрафа и платной дороги: суммы понимают запятую, двойной тап не создаёт дубль.
 *
 * Регрессия: форма штрафа разбирала сумму через `toDoubleOrNull()`, и «1500,50», набранное
 * на русской раскладке, не сохранялось с ошибкой «Введите корректную сумму» — в отличие
 * от остальных форм, где разбор давно сведён в `toDecimalOrNull()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FineAndTollFormTest {

    private val dispatcher = StandardTestDispatcher()
    private val fineRepository = mockk<FineRepository>(relaxed = true)
    private val tollPaymentRepository = mockk<TollPaymentRepository>(relaxed = true)
    private val dataChangeNotifier = mockk<DataChangeNotifier>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { tollPaymentRepository.getRoadNames() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun handle() = SavedStateHandle(mapOf("carId" to 1L))

    @Test
    fun `штраф с суммой через запятую сохраняется`() = runTest(dispatcher) {
        val saved = slot<Fine>()
        coEvery { fineRepository.insertFine(capture(saved)) } returns 1L
        val vm = AddFineViewModel(fineRepository, dataChangeNotifier, handle())

        vm.updateAmount("1500,50")
        vm.updateIsPaid(true)
        vm.updatePaidAmount("750,25")
        vm.saveFine()
        advanceUntilIdle()

        assertNull(vm.amountError.value)
        assertNull(vm.paidAmountError.value)
        assertEquals(1_500.5, saved.captured.amount, 0.0001)
        assertEquals(750.25, saved.captured.paidAmount!!, 0.0001)
    }

    @Test
    fun `штраф с мусором в сумме не сохраняется`() = runTest(dispatcher) {
        val vm = AddFineViewModel(fineRepository, dataChangeNotifier, handle())

        vm.updateAmount("15о0")
        vm.saveFine()
        advanceUntilIdle()

        assertEquals("Введите корректную сумму", vm.amountError.value)
        coVerify(exactly = 0) { fineRepository.insertFine(any()) }
    }

    @Test
    fun `оплата платной дороги с суммой через запятую сохраняется`() = runTest(dispatcher) {
        val saved = slot<TollPayment>()
        coEvery { tollPaymentRepository.insertTollPayment(capture(saved)) } returns 1L
        val vm = AddTollPaymentViewModel(tollPaymentRepository, dataChangeNotifier, handle())

        vm.updateAmount("480,5")
        vm.updateRoadName("  М-11  ")
        vm.save()
        advanceUntilIdle()

        assertEquals(480.5, saved.captured.amount, 0.0001)
        assertEquals("пробелы по краям названия не сохраняются", "М-11", saved.captured.roadName)
    }

    /**
     * Регрессия: сохранение асинхронное, и два быстрых тапа (галочка в шапке и кнопка внизу)
     * оба проходили проверки — в базе оказывались две одинаковые записи.
     */
    @Test
    fun `двойной тап по сохранению штрафа создаёт одну запись`() = runTest(dispatcher) {
        val vm = AddFineViewModel(fineRepository, dataChangeNotifier, handle())
        vm.updateAmount("500")

        vm.saveFine()
        vm.saveFine()
        advanceUntilIdle()

        coVerify(exactly = 1) { fineRepository.insertFine(any()) }
    }

    @Test
    fun `двойной тап по сохранению оплаты дороги создаёт одну запись`() = runTest(dispatcher) {
        val vm = AddTollPaymentViewModel(tollPaymentRepository, dataChangeNotifier, handle())
        vm.updateAmount("500")

        vm.save()
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { tollPaymentRepository.insertTollPayment(any()) }
    }

    /** Ошибка ввода не должна навсегда заблокировать кнопку: исправил — сохраняй снова */
    @Test
    fun `после ошибки ввода сохранить можно снова`() = runTest(dispatcher) {
        val vm = AddTollPaymentViewModel(tollPaymentRepository, dataChangeNotifier, handle())

        vm.save()
        advanceUntilIdle()
        assertEquals("Обязательное поле", vm.amountError.value)
        assertEquals(false, vm.isSaving.value)

        vm.updateAmount("300")
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { tollPaymentRepository.insertTollPayment(any()) }
        assertEquals(true, vm.saveSuccess.value)
    }
}
