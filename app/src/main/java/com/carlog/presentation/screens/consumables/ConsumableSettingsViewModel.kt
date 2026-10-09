package com.carlog.presentation.screens.consumables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.preferences.ConsumablePreferences
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.ConsumableSettingsRules
import com.carlog.domain.model.ConsumableSettingsRules.Parsed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Поля интервалов и объёмов хранят **набранный текст**, а не числа: разбор — только при
 * сохранении, через [ConsumableSettingsRules]. Иначе поле пересобиралось из числа на каждую
 * клавишу (см. описание правил).
 */
data class ConsumableSettingsState(
    val selectedCategories: Set<String> = emptySet(),
    val categoryIntervals: Map<String, Pair<String, String>> = emptyMap(), // Pair(км, дни) — текст полей
    val categoryVolumes: Map<String, String> = emptyMap(),                // текст полей
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConsumableSettingsViewModel @Inject constructor(
    private val preferences: ConsumablePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L

    private val _state = MutableStateFlow(ConsumableSettingsState())
    val state: StateFlow<ConsumableSettingsState> = _state.asStateFlow()

    private val allCategories = ConsumableCategories.STANDARD_CATEGORIES +
                                 ConsumableCategories.ADDITIONAL_CATEGORIES

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)

                preferences.selectedCategories.firstOrNull()?.let { selected ->
                    _state.value = _state.value.copy(selectedCategories = selected)
                }

                // Загрузить интервалы и объемы для всех категорий
                val intervals = mutableMapOf<String, Pair<String, String>>()
                val volumes = mutableMapOf<String, String>()

                allCategories.forEach { category ->
                    val defaults = ConsumableCategories.DEFAULT_INTERVALS[category]
                    val mileage = preferences.getIntervalMileage(category).firstOrNull()
                    val days = preferences.getIntervalDays(category).firstOrNull()

                    intervals[category] = Pair(
                        ConsumableSettingsRules.intervalText(mileage, defaults?.first),
                        ConsumableSettingsRules.intervalText(days, defaults?.second)
                    )
                    if (ConsumableCategories.FLUID_CATEGORIES.contains(category)) {
                        volumes[category] = ConsumableSettingsRules.volumeText(
                            preferences.getVolume(category).firstOrNull()
                        )
                    }
                }

                _state.value = _state.value.copy(
                    categoryIntervals = intervals,
                    categoryVolumes = volumes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun toggleCategory(category: String) {
        val current = _state.value.selectedCategories
        _state.value = _state.value.copy(
            selectedCategories = if (current.contains(category)) {
                current - category
            } else {
                current + category
            }
        )
    }

    fun updateIntervalMileage(category: String, mileage: String) {
        val current = getInterval(category)
        _state.value = _state.value.copy(
            categoryIntervals = _state.value.categoryIntervals + (category to Pair(mileage, current.second)),
            error = null
        )
    }

    fun updateIntervalDays(category: String, days: String) {
        val current = getInterval(category)
        _state.value = _state.value.copy(
            categoryIntervals = _state.value.categoryIntervals + (category to Pair(current.first, days)),
            error = null
        )
    }

    fun updateVolume(category: String, volume: String) {
        _state.value = _state.value.copy(
            categoryVolumes = _state.value.categoryVolumes + (category to volume),
            error = null
        )
    }

    fun saveSettings() {
        if (_state.value.isSaving) return
        val snapshot = _state.value

        // Сначала разбираем всё: при ошибке в одном поле не сохраняем ничего,
        // иначе половина настроек записалась бы, а половина — нет
        val invalid = mutableListOf<String>()
        val intervals = snapshot.categoryIntervals.mapValues { (category, texts) ->
            val defaults = ConsumableCategories.DEFAULT_INTERVALS[category]
            val mileage = ConsumableSettingsRules.interval(texts.first, defaults?.first)
            val days = ConsumableSettingsRules.interval(texts.second, defaults?.second)
            if (mileage is Parsed.Invalid || days is Parsed.Invalid) invalid += category
            Pair((mileage as? Parsed.Ok)?.stored, (days as? Parsed.Ok)?.stored)
        }
        val volumes = snapshot.categoryVolumes.mapValues { (category, text) ->
            val volume = ConsumableSettingsRules.volume(text)
            if (volume is Parsed.Invalid) invalid += category
            (volume as? Parsed.Ok)?.stored
        }
        if (invalid.isNotEmpty()) {
            _state.value = snapshot.copy(
                error = "Проверьте значения: " + invalid.distinct().joinToString(", ")
            )
            return
        }

        _state.value = snapshot.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                // Сохранить выбранные категории
                preferences.saveSelectedCategories(snapshot.selectedCategories)

                // Сохранить интервалы (null — убрать из настроек, действует умолчание)
                intervals.forEach { (category, values) ->
                    preferences.saveIntervalMileage(category, values.first)
                    preferences.saveIntervalDays(category, values.second)
                }

                // Сохранить объемы
                volumes.forEach { (category, volume) ->
                    preferences.saveVolume(category, volume)
                }

                // Отметить, что первый запуск завершен
                preferences.setFirstLaunchCompleted(carId)

                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }

    fun getInterval(category: String): Pair<String, String> {
        _state.value.categoryIntervals[category]?.let { return it }
        // Ещё не загружено — показываем умолчание, как и раньше
        val defaults = ConsumableCategories.DEFAULT_INTERVALS[category]
        return Pair(
            ConsumableSettingsRules.intervalText(null, defaults?.first),
            ConsumableSettingsRules.intervalText(null, defaults?.second)
        )
    }

    fun getVolume(category: String): String {
        return _state.value.categoryVolumes[category] ?: ""
    }
}
