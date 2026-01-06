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
import com.carlog.domain.model.ConsumablesStatistics
import com.carlog.presentation.screens.statistics.charts.*

@Composable
fun ConsumablesStatisticsTab(
    statistics: ConsumablesStatistics?,
    onNavigateToConsumables: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (statistics == null) {
        EmptyTabContent(
            message = "Нет данных по расходникам",
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
        // Category Spending Bar Chart
        if (statistics.categorySpending.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToConsumables() }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Расходы по категориям",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    VicoBarChart(
                        data = statistics.categorySpending.take(8).map { item ->
                            BarChartData(
                                label = item.month, // Using month field for category name
                                value = item.amount.toFloat()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Category Distribution Pie Chart
        if (statistics.categoryDistribution.isNotEmpty()) {
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
                        data = statistics.categoryDistribution.take(7).mapIndexed { index, item ->
                            PieChartData(
                                label = item.category,
                                value = item.amount.toFloat(),
                                color = getConsumableColor(index)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Average per Category
        if (statistics.averagePerCategory.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Средняя стоимость по категориям",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    statistics.averagePerCategory.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Замен: ${item.count}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatCurrency(item.average),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
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
                    label = "Средняя стоимость ТО (запчасти)",
                    value = formatCurrency(statistics.averageMaintenanceCost)
                )
                
                MetricRow(
                    label = "Средняя стоимость ТО (с сервисом)",
                    value = formatCurrency(statistics.averageMaintenanceCostWithService)
                )
                
                MetricRow(
                    label = "Общая стоимость расходников",
                    value = formatCurrency(statistics.totalConsumablesCost)
                )
            }
        }
        
        // Hint about navigation
        Text(
            text = "Нажмите на карточку с графиком, чтобы перейти к списку расходников",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun getConsumableColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF00BCD4), // Cyan
        Color(0xFF2196F3), // Blue
        Color(0xFF3F51B5), // Indigo
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFFF5722)  // Deep Orange
    )
    return colors[index % colors.size]
}
