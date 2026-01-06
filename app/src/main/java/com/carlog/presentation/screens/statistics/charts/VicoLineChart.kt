package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf

@Composable
fun VicoLineChart(
    data: List<LineChartData>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Unspecified,
    showYAxis: Boolean = true
) {
    if (data.isEmpty()) return
    
    val chartEntryModel = remember(data) {
        val entries = data.mapIndexed { index, item -> 
            entryOf(index.toFloat(), item.value.toFloat())
        }
        entryModelOf(entries)
    }
    
    val bottomAxisValueFormatter = remember(data) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index >= 0 && index < data.size) {
                data[index].label
            } else {
                ""
            }
        }
    }
    
    val textColor = MaterialTheme.colorScheme.onSurface
    val axisLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(modifier = modifier) {
        ProvideChartStyle {
            Chart(
                chart = lineChart(),
                model = chartEntryModel,
                startAxis = if (showYAxis) rememberStartAxis(
                    label = textComponent(
                        color = textColor
                    ),
                    axis = lineComponent(
                        color = axisLineColor,
                        thickness = 0.5.dp
                    ),
                    tick = lineComponent(
                        color = axisLineColor,
                        thickness = 0.5.dp
                    )
                ) else null,
                bottomAxis = rememberBottomAxis(
                    valueFormatter = bottomAxisValueFormatter,
                    guideline = null,
                    label = textComponent(
                        color = textColor
                    ),
                    axis = lineComponent(
                        color = axisLineColor,
                        thickness = 0.5.dp
                    ),
                    tick = lineComponent(
                        color = axisLineColor,
                        thickness = 0.5.dp
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}

