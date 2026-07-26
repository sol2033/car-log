package com.carlog.presentation.screens.integrity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.integrity.DataIntegrityChecker
import com.carlog.data.integrity.EventCandidate
import com.carlog.data.integrity.IntegrityFinding
import com.carlog.data.preferences.IntegrityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataCheckUiState(
    val isLoading: Boolean = true,
    val findings: List<IntegrityFinding> = emptyList(),
    /** Сколько находок скрыто пользователем (кнопкой «Оставить как есть») */
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DataCheckViewModel @Inject constructor(
    private val checker: DataIntegrityChecker,
    private val preferences: IntegrityPreferences,
    private val dataChangeNotifier: DataChangeNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataCheckUiState())
    val uiState: StateFlow<DataCheckUiState> = _uiState.asStateFlow()

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val all = checker.scan()
                val ignored = preferences.ignoredFindings.firstOrNull().orEmpty()
                val visible = if (_uiState.value.showHidden) all else all.filterNot { ignored.contains(it.id) }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    findings = visible,
                    hiddenCount = all.count { ignored.contains(it.id) }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось проверить данные"
                )
            }
        }
    }

    fun toggleShowHidden() {
        _uiState.value = _uiState.value.copy(showHidden = !_uiState.value.showHidden)
        rescan()
    }

    fun ignoreFinding(finding: IntegrityFinding) {
        viewModelScope.launch {
            preferences.ignore(finding.id)
            rescan()
        }
    }

    fun restoreHidden() {
        viewModelScope.launch {
            preferences.clearIgnored()
            rescan()
        }
    }

    fun linkPart(finding: IntegrityFinding.UnlinkedPart, candidate: EventCandidate) = fix {
        checker.linkPartToEvent(finding.part.id, candidate)
    }

    fun applyAccidentRepairCost(finding: IntegrityFinding.AccidentWithoutRepairCost, serviceCost: Double) = fix {
        checker.setAccidentRepairCost(finding.accident.id, finding.linkedPartsSum + serviceCost)
    }

    fun alignBreakdownCost(finding: IntegrityFinding.BreakdownCostMismatch) = fix {
        checker.alignBreakdownPartsCost(finding.breakdown.id, finding.linkedPartsSum)
    }

    fun setPurchaseInfo(finding: IntegrityFinding.CarWithoutPurchaseInfo, date: Long, mileage: Int) = fix {
        checker.setPurchaseInfo(finding.car.id, date, mileage)
    }

    fun keepSingleActiveConsumable(finding: IntegrityFinding.DuplicateActiveConsumables) = fix {
        val carId = finding.consumables.first().carId
        checker.keepSingleActiveConsumable(carId, finding.category)
    }

    /** Общая обвязка исправления: применить, уведомить бэкап, пересканировать */
    private fun fix(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                dataChangeNotifier.notifyDataChanged()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Не удалось исправить")
            }
            rescan()
        }
    }
}
