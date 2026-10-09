package com.carlog.presentation.screens.fines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.TollPaymentRepository
import com.carlog.domain.model.TollPayment
import com.carlog.domain.model.TollPaymentSummary
import com.carlog.domain.model.TollSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Вкладка «Платные дороги»: записи, сгруппированные по месяцам, и сводка.
 *
 * Пробега у записи нет, поэтому `carRepository.updateCarMileage*` здесь не вызывается,
 * а `notifyDataChanged` обязателен — без него не сработает авто-бэкап.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TollPaymentsViewModel @Inject constructor(
    private val tollPaymentRepository: TollPaymentRepository,
    private val dataChangeNotifier: DataChangeNotifier
) : ViewModel() {

    private val _carId = MutableStateFlow<Long?>(null)

    /** null — данные ещё не пришли: без этого вкладка на миг показывала «Оплат пока нет» */
    val summary: StateFlow<TollSummary?> = _carId.filterNotNull().flatMapLatest { id ->
        tollPaymentRepository.getTollPaymentsByCarId(id)
    }.map { TollPaymentSummary.summarize(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setCarId(carId: Long) {
        _carId.value = carId
    }

    fun deleteTollPayment(payment: TollPayment) {
        viewModelScope.launch {
            tollPaymentRepository.deleteTollPayment(payment)
            dataChangeNotifier.notifyDataChanged()
        }
    }
}
