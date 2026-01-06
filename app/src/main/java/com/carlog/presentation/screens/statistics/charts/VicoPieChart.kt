package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Vico doesn't support pie charts natively, so we'll use the existing SimplePieChart
@Composable
fun VicoPieChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier
) {
    // For now, use the existing SimplePieChart implementation
    // Vico library doesn't have built-in pie chart support
    SimplePieChart(data = data, modifier = modifier)
}
