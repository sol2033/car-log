package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carlog.domain.model.ExpensesStatistics
import com.carlog.presentation.screens.statistics.charts.*

@Composable
fun ExpensesStatisticsTab(
    statistics: ExpensesStatistics?,
    modifier: Modifier = Modifier
) {
    if (statistics == null) {
        EmptyTabContent(
            message = "Нет данных о прочих расходах.\nДобавьте расходы, чтобы увидеть статистику.",
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
        // Category Distribution Pie Chart
        if (statistics.categoryDistribution.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Распределение по категориям",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SimplePieChart(
                        data = statistics.categoryDistribution.mapIndexed { index, item ->
                            PieChartData(
                                label = item.category,
                                value = item.amount.toFloat(),
                                color = getExpenseColor(index)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Monthly Expenses Bar Chart
        if (statistics.monthlyExpenses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Расходы по месяцам",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    VicoBarChart(
                        data = statistics.monthlyExpenses.map { item ->
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
        
        // Top Categories
        if (statistics.topCategories.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Топ категорий по сумме",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    statistics.topCategories.forEach { item ->
                        MetricRow(
                            label = item.category,
                            value = formatCurrency(item.amount)
                        )
                    }
                }
            }
        }
        
        // Total Expenses
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
                    label = "Общая стоимость расходов",
                    value = formatCurrency(statistics.totalExpensesCost)
                )
            }
        }
    }
}

@Composable
private fun getExpenseColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF3F51B5), // Indigo
        Color(0xFF00BCD4), // Cyan
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50)  // Green
    )
    return colors[index % colors.size]
}
