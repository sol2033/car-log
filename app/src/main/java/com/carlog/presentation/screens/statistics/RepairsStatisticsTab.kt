package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carlog.domain.model.RepairsStatistics
import com.carlog.domain.model.StatisticsPeriod
import com.carlog.presentation.screens.statistics.charts.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun getPeriodLabel(period: StatisticsPeriod): String {
    return when (period) {
        StatisticsPeriod.WEEK -> "по дням"
        StatisticsPeriod.TWO_WEEKS, StatisticsPeriod.MONTH -> "по неделям"
        StatisticsPeriod.THREE_MONTHS, StatisticsPeriod.SIX_MONTHS, StatisticsPeriod.YEAR -> "по месяцам"
        StatisticsPeriod.ALL_TIME -> "по месяцам"
    }
}

private fun getDateRangeText(period: StatisticsPeriod, specificMonth: YearMonth?): String {
    val now = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
    
    // Для 2 недель показываем детальные интервалы
    if (period == StatisticsPeriod.TWO_WEEKS) {
        val currentMonday = now.minusDays(now.dayOfWeek.value.toLong() - 1)
        val previousMonday = currentMonday.minusWeeks(1)
        val previousSunday = previousMonday.plusDays(6)
        return "неделя 1: ${previousMonday.format(formatter)} - ${previousSunday.format(formatter)}\n" +
               "неделя 2: ${currentMonday.format(formatter)} - ${now.format(formatter)}"
    }
    
    // Для месяца показываем 4 недели
    if (period == StatisticsPeriod.MONTH) {
        val startDate = now.minusMonths(1)
        val currentMonday = startDate.minusDays(startDate.dayOfWeek.value.toLong() - 1)
        val weeks = StringBuilder()
        for (i in 0..3) {
            val weekMonday = currentMonday.plusWeeks(i.toLong())
            val weekSunday = weekMonday.plusDays(6)
            if (i > 0) weeks.append("\n")
            weeks.append("неделя ${i + 1}: ${weekMonday.format(formatter)} - ${weekSunday.format(formatter)}")
        }
        return weeks.toString()
    }
    
    val (start, end) = when {
        specificMonth != null -> {
            val startDate = specificMonth.atDay(1)
            val endDate = specificMonth.atEndOfMonth()
            startDate to endDate
        }
        period == StatisticsPeriod.WEEK -> {
            val monday = now.minusDays(now.dayOfWeek.value.toLong() - 1)
            val sunday = monday.plusDays(6)
            monday to sunday
        }
        else -> return ""
    }
    
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))
    return "${start.format(dateFormatter)} - ${end.format(dateFormatter)}"
}

@Composable
fun RepairsStatisticsTab(
    statistics: RepairsStatistics?,
    selectedPeriod: StatisticsPeriod,
    specificMonth: YearMonth?,
    onNavigateToBreakdowns: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (statistics == null) {
        EmptyTabContent(
            message = "Нет данных по ремонтам",
            modifier = modifier
        )
        return
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Monthly Repair Costs Bar Chart (скрываем для ALL_TIME)
        if (statistics.monthlyRepairCosts.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBreakdowns() }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Расходы на ремонты ${getPeriodLabel(selectedPeriod)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    val dateRange = getDateRangeText(selectedPeriod, specificMonth)
                    if (dateRange.isNotEmpty()) {
                        Text(
                            text = dateRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    VicoBarChart(
                        data = statistics.monthlyRepairCosts.map { item ->
                            BarChartData(
                                label = item.month,
                                value = item.amount.toFloat()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Parts vs Labor Pie Chart
        if (statistics.partsVsLaborDistribution.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Запчасти vs Работа",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SimplePieChart(
                        data = statistics.partsVsLaborDistribution.mapIndexed { index, item ->
                            PieChartData(
                                label = item.category,
                                value = item.amount.toFloat(),
                                color = if (index == 0) Color(0xFF2196F3) else Color(0xFFFFC107)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Key Metrics
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ключевые показатели",
                    style = MaterialTheme.typography.titleMedium
                )
                
                MetricRow(
                    label = "Общая стоимость ремонтов",
                    value = formatCurrency(statistics.totalRepairsCost)
                )
                
                MetricRow(
                    label = "Количество ремонтов",
                    value = statistics.repairsCount.toString()
                )
                
                MetricRow(
                    label = "Средняя стоимость ремонта",
                    value = formatCurrency(statistics.averageRepairCost)
                )
            }
        }
        
        // Hint about navigation
        Text(
            text = "Нажмите на карточку с графиком, чтобы перейти к списку ремонтов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
