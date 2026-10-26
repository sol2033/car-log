package com.carlog.presentation.screens.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.carlog.R
import com.carlog.domain.model.StatisticsPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Подпись под графиком: какие даты стоят за точками «неделя N» или за выбранным периодом.
 *
 * Недели считаются так же, как точки графика в `StatisticsViewModel.generateAllPeriods`:
 * раньше у «Месяца» здесь было ровно 4 недели, а на графике — неделя «сегодня» в них
 * не входила. Функция была скопирована в три вкладки — теперь она одна.
 *
 * @return пустая строка — для периода подпись не нужна
 */
@Composable
fun statisticsDateRangeText(period: StatisticsPeriod, specificMonth: YearMonth?): String {
    val weekLabels = statisticsWeekRanges(period, specificMonth, LocalDate.now())
    if (weekLabels != null) {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return weekLabels.mapIndexed { index, (start, end) ->
            stringResource(
                R.string.statistics_week_range,
                index + 1,
                start.format(formatter),
                end.format(formatter)
            )
        }.joinToString("\n")
    }

    val (start, end) = when {
        specificMonth != null -> specificMonth.atDay(1) to specificMonth.atEndOfMonth()
        period == StatisticsPeriod.WEEK -> {
            val now = LocalDate.now()
            val monday = now.minusDays(now.dayOfWeek.value.toLong() - 1)
            monday to monday.plusDays(6)
        }
        else -> return ""
    }
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    return "${start.format(dateFormatter)} - ${end.format(dateFormatter)}"
}

/**
 * Недели графика для периодов «2 недели» и «Месяц»: начало (понедельник) и конец каждой,
 * последняя обрезана по [today]. null — график этого периода не по неделям.
 */
internal fun statisticsWeekRanges(
    period: StatisticsPeriod,
    specificMonth: YearMonth?,
    today: LocalDate
): List<Pair<LocalDate, LocalDate>>? {
    if (specificMonth != null) return null
    val rangeStart = when (period) {
        // Предыдущая неделя и текущая
        StatisticsPeriod.TWO_WEEKS -> today.minusDays(today.dayOfWeek.value.toLong() - 1).minusWeeks(1)
        StatisticsPeriod.MONTH -> today.minusMonths(1)
        else -> return null
    }
    var monday = rangeStart.minusDays(rangeStart.dayOfWeek.value.toLong() - 1)
    val weeks = mutableListOf<Pair<LocalDate, LocalDate>>()
    while (!monday.isAfter(today)) {
        val sunday = monday.plusDays(6)
        weeks += monday to (if (sunday.isAfter(today)) today else sunday)
        monday = monday.plusWeeks(1)
    }
    return weeks
}

/**
 * Подпись точки-недели на графике. ViewModel без контекста строковых ресурсов, поэтому язык
 * берётся из локали по умолчанию — её выставляет `MainActivity` по выбранному языку приложения
 * (раньше подпись была только по-русски)
 */
internal fun weekPointLabel(number: Int, locale: Locale = Locale.getDefault()): String =
    if (locale.language == "en") "week $number" else "неделя $number"
