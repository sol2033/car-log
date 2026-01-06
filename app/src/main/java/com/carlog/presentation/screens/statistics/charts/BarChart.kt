package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data class for charts (used by both old and new implementations)
data class BarChartData(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified
)

@Composable
fun SimpleBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.value } ?: 0f
    if (maxValue == 0f) return
    
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            val padding = 20.dp.toPx()
            val graphHeight = size.height - padding * 2
            val barWidth = (size.width - padding * 2) / data.size * 0.7f
            val spacing = (size.width - padding * 2) / data.size
            
            data.forEachIndexed { index, item ->
                val barHeight = (item.value / maxValue) * graphHeight
                val x = padding + spacing * index + (spacing - barWidth) / 2
                val y = size.height - padding - barHeight
                
                val barColor = if (item.color == Color.Unspecified) {
                    Color(0xFF00BCD4) // Default cyan color
                } else {
                    item.color
                }
                
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        
        // X-axis labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }
        }
    }
}
