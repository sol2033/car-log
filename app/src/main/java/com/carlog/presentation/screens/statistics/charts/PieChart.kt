package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class PieChartData(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun SimplePieChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total == 0f) return
    
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp)
        ) {
            val canvasSize = size.minDimension
            val radius = canvasSize / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            var startAngle = -90f
            
            data.forEach { item ->
                val sweepAngle = (item.value / total) * 360f
                
                drawArc(
                    color = item.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                
                startAngle += sweepAngle
            }
            
            // Draw circle in center for donut effect using theme surface color
            drawCircle(
                color = surfaceColor,
                radius = radius * 0.5f,
                center = center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Legend
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(color = item.color)
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${(item.value / total * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
