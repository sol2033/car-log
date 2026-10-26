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
import java.time.YearMonth

private fun getPeriodLabel(period: StatisticsPeriod): String {
    return when (period) {
        StatisticsPeriod.WEEK -> "по дням"
        StatisticsPeriod.TWO_WEEKS, StatisticsPeriod.MONTH -> "по неделям"
        StatisticsPeriod.THREE_MONTHS, StatisticsPeriod.SIX_MONTHS, StatisticsPeriod.YEAR -> "по месяцам"
        StatisticsPeriod.ALL_TIME -> "по месяцам"
    }
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
                    
                    val dateRange = statisticsDateRangeText(selectedPeriod, specificMonth)
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
                        text = "Запчасти и работа",
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
    }
}
