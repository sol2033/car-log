package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LineChartData(
    val label: String,
    val value: Float
)

@Composable
fun SimpleLineChart(
    data: List<LineChartData>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.value } ?: 0f
    val minValue = data.minOfOrNull { it.value } ?: 0f
    
    if (maxValue == 0f && minValue == 0f) return
    
    val textMeasurer = rememberTextMeasurer()
    
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            val padding = 40.dp.toPx()
            val graphWidth = size.width - padding * 2
            val graphHeight = size.height - padding
            
            val range = maxValue - minValue
            val step = if (data.size > 1) graphWidth / (data.size - 1) else 0f
            
            // Draw grid lines
            for (i in 0..4) {
                val y = padding + (graphHeight / 4) * i
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // Draw line
            val path = Path()
            data.forEachIndexed { index, point ->
                val x = padding + step * index
                val y = if (range > 0) {
                    padding + graphHeight - ((point.value - minValue) / range * graphHeight)
                } else {
                    padding + graphHeight / 2
                }
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                
                // Draw point
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
            
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // X-axis labels (simplified - show first, middle, last)
        if (data.size > 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.first().label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = data[data.size / 2].label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = data.last().label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}
