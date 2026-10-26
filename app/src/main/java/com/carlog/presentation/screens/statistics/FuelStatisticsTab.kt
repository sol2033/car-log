package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.FuelStatistics
import com.carlog.domain.model.StatisticsPeriod
import com.carlog.presentation.components.FuelConsumptionBlock
import com.carlog.presentation.screens.statistics.charts.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

private fun getPeriodLabel(period: StatisticsPeriod): String {
    return when (period) {
        StatisticsPeriod.WEEK -> "по дням"
        StatisticsPeriod.TWO_WEEKS, StatisticsPeriod.MONTH -> "по неделям"
        StatisticsPeriod.THREE_MONTHS, StatisticsPeriod.SIX_MONTHS, StatisticsPeriod.YEAR -> "по месяцам"
        StatisticsPeriod.ALL_TIME -> "по месяцам"
    }
}

@Composable
fun FuelStatisticsTab(
    statistics: FuelStatistics?,
    selectedPeriod: StatisticsPeriod,
    specificMonth: YearMonth?,
    fuelResetPending: Boolean,
    onRequestFuelReset: () -> Unit,
    onCancelFuelReset: () -> Unit,
    onClearFuelReset: () -> Unit,
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
                    
                    FuelConsumptionBlock(
                        summary = fuelTypeStats.summary,
                        resetPending = fuelResetPending,
                        consumptionSince = statistics.consumptionSince,
                        onRequestReset = onRequestFuelReset,
                        onCancelPending = onCancelFuelReset,
                        onClearReset = onClearFuelReset
                    )
                    
                    MetricRow(
                        label = "Общее количество",
                        value = "${formatNumber(fuelTypeStats.totalLiters)} ${fuelTypeStats.summary.unit}"
                    )
                    
                    MetricRow(
                        label = "Общая стоимость",
                        value = formatCurrency(fuelTypeStats.totalCost)
                    )
                    
                    // Monthly Liters Bar Chart for this fuel type (скрываем для ALL_TIME)
                    if (fuelTypeStats.monthlyLiters.isNotEmpty() && selectedPeriod != StatisticsPeriod.ALL_TIME) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Расход ${getPeriodLabel(selectedPeriod)} (${fuelTypeStats.summary.unit})",
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
                            data = fuelTypeStats.monthlyLiters.map { item ->
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
