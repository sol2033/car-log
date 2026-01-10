package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carlog.domain.model.FuelStatistics
import com.carlog.domain.model.StatisticsPeriod
import com.carlog.presentation.screens.statistics.charts.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
fun FuelStatisticsTab(
    statistics: FuelStatistics?,
    selectedPeriod: StatisticsPeriod,
    specificMonth: YearMonth?,
    modifier: Modifier = Modifier
) {
    if (statistics == null) {
        EmptyTabContent(
            message = "Нет данных для отображения статистики по заправкам.\nДобавьте заправки для просмотра статистики.",
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
        // Total Summary
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Общая статистика",
                    style = MaterialTheme.typography.titleMedium
                )
                
                MetricRow(
                    label = "Общая стоимость всего топлива",
                    value = formatCurrency(statistics.totalFuelCost)
                )
            }
        }
        
        // Statistics for each fuel type
        statistics.fuelTypes.forEach { fuelTypeStats ->
            // Fuel Type Card with Metrics and Charts
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = fuelTypeStats.fuelType,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Key Metrics for this fuel type
                    Column {
                        MetricRow(
                            label = "Средний расход",
                            value = if (fuelTypeStats.averageConsumption > 0) 
                                "${formatNumber(fuelTypeStats.averageConsumption)} л/100км" 
                            else "—"
                        )
                        if (fuelTypeStats.averageConsumption == 0.0) {
                            Text(
                                text = "Добавьте минимум 2 заправки",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                    
                    MetricRow(
                        label = "Общее количество",
                        value = "${formatNumber(fuelTypeStats.totalLiters)} ${if (fuelTypeStats.fuelType == "Электро") "кВт·ч" else "л"}"
                    )
                    
                    MetricRow(
                        label = "Общая стоимость",
                        value = formatCurrency(fuelTypeStats.totalCost)
                    )
                    
                    // Monthly Liters Bar Chart for this fuel type (скрываем для ALL_TIME)
                    if (fuelTypeStats.monthlyLiters.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Расход ${getPeriodLabel(selectedPeriod)} (${if (fuelTypeStats.fuelType == "Электро") "кВт·ч" else "л"})",
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
                            data = fuelTypeStats.monthlyLiters.map { item ->
                                BarChartData(
                                    label = item.month,
                                    value = item.amount.toFloat()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Consumption Trend Line Chart for this fuel type (л/100км) (скрываем для ALL_TIME)
                    if (fuelTypeStats.consumptionTrend.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Тренд расхода топлива (л/100км)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        VicoLineChart(
                            data = fuelTypeStats.consumptionTrend.map { item ->
                                LineChartData(
                                    label = DateTimeFormatter.ofPattern("dd.MM").format(item.date),
                                    value = item.consumption.toFloat()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            lineColor = when(fuelTypeStats.fuelType) {
                                "Бензин" -> Color(0xFF4CAF50)
                                "Газ" -> Color(0xFF2196F3)
                                "Дизель" -> Color(0xFFFF9800)
                                "Электро" -> Color(0xFF9C27B0)
                                else -> Color(0xFF4CAF50)
                            }
                        )
                    }
                }
            }
        }
        
        // Monthly Fuel Costs Bar Chart (all types combined) (скрываем для ALL_TIME)
        if (statistics.monthlyFuelCosts.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Расходы на топливо ${getPeriodLabel(selectedPeriod)} (все типы)",
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
                        data = statistics.monthlyFuelCosts.map { item ->
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
        
    }
}
