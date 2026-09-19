package com.carlog.presentation.screens.fines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.FineRepository
import com.carlog.domain.model.Fine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Список штрафов машины и сводка по ним.
 *
 * Пробега у штрафа нет, поэтому `carRepository.updateCarMileage*` здесь не вызывается —
 * в отличие от остальных модулей (правило 5 из CLAUDE.md к этому модулю не применимо в части
 * пробега, но `notifyDataChanged` обязателен: без него не сработает авто-бэкап).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FinesViewModel @Inject constructor(
    private val fineRepository: FineRepository,
    private val dataChangeNotifier: DataChangeNotifier
) : ViewModel() {

    private val _carId = MutableStateFlow<Long?>(null)

    val fines = _carId.filterNotNull().flatMapLatest { id ->
        fineRepository.getFinesByCarId(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalPaid = _carId.filterNotNull().flatMapLatest { id ->
        fineRepository.getTotalPaid(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val totalUnpaid = _carId.filterNotNull().flatMapLatest { id ->
        fineRepository.getTotalUnpaid(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val unpaidCount = _carId.filterNotNull().flatMapLatest { id ->
        fineRepository.getUnpaidCount(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setCarId(carId: Long) {
        _carId.value = carId
    }

    fun deleteFine(fine: Fine) {
        viewModelScope.launch {
            fineRepository.deleteFine(fine)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    /**
     * Отметка об оплате прямо из списка. Сумму не спрашиваем: если платёж прошёл в льготный
     * срок, пользователь поправит её в форме — а типичный случай «заплатил сколько выписали»
     * не должен стоить лишнего диалога.
     */
    fun markAsPaid(fine: Fine) {
        viewModelScope.launch {
            fineRepository.markAsPaid(fine, System.currentTimeMillis(), null)
            dataChangeNotifier.notifyDataChanged()
        }
    }

    fun markAsUnpaid(fine: Fine) {
        viewModelScope.launch {
            fineRepository.markAsUnpaid(fine)
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
