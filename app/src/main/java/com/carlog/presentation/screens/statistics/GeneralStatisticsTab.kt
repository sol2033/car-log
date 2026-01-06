package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carlog.domain.model.GeneralStatistics
import com.carlog.domain.model.StatisticsPeriod
import com.carlog.presentation.screens.statistics.charts.*
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
fun GeneralStatisticsTab(
    statistics: GeneralStatistics?,
    selectedPeriod: StatisticsPeriod,
    specificMonth: YearMonth?,
    modifier: Modifier = Modifier
) {
    if (statistics == null) {
        EmptyTabContent(
            message = "Нет данных для отображения общей статистики",
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
        // Cost Distribution Pie Chart
        if (statistics.costDistribution.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Распределение расходов",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SimplePieChart(
                        data = statistics.costDistribution.mapIndexed { index, item ->
                            PieChartData(
                                label = item.category,
                                value = item.amount.toFloat(),
                                color = getColorForCategory(index)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Cost Trend Line Chart (скрываем для ALL_TIME)
        if (statistics.costTrend.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Тренд расходов",
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
                    
                    VicoLineChart(
                        data = statistics.costTrend.map { item ->
                            LineChartData(
                                label = item.label,
                                value = item.amount.toFloat()
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
                    label = "Общие расходы",
                    value = formatCurrency(statistics.totalCost)
                )
                
                MetricRow(
                    label = "Стоимость за км",
                    value = formatCurrency(statistics.costPerKm) + "/км"
                )
                
                MetricRow(
                    label = "Средний пробег в день",
                    value = formatNumber(statistics.averageKmPerDay) + " км"
                )
                
                MetricRow(
                    label = "Средний пробег в месяц",
                    value = formatNumber(statistics.averageKmPerMonth) + " км"
                )
                
                if (statistics.mostExpensiveMonth != null) {
                    MetricRow(
                        label = "Самый дорогой месяц",
                        value = statistics.mostExpensiveMonth
                    )
                }
            }
        }
    }
}

@Composable
internal fun DistributionItem(
    category: String,
    amount: Double,
    percentage: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "${DecimalFormat("#.#").format(percentage)}%",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EmptyTabContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatCurrency(value: Double): String {
    return DecimalFormat("#,##0.00").format(value) + " ₽"
}

fun formatNumber(value: Double): String {
    return DecimalFormat("#,##0.#").format(value)
}

@Composable
private fun getColorForCategory(index: Int): Color {
    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF9800)  // Orange
    )
    return colors[index % colors.size]
}
