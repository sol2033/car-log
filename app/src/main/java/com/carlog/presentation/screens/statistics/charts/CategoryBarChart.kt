package com.carlog.presentation.screens.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class CategoryBarData(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * Столбчатый график для категориальных данных с длинными названиями
 * (расходники, категории расходов). Подписи НЕ ставятся под столбцами —
 * вместо них цветная легенда снизу со значениями. Так подписи никогда
 * не накладываются друг на друга, сколько бы категорий ни было.
 */
@Composable
fun CategoryBarChart(
    data: List<CategoryBarData>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = ::formatChartValue
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.takeIf { it > 0f } ?: return

    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val valueStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val count = data.size
            val gap = 10.dp.toPx()
            val topPadding = 22.dp.toPx() // место под значения над столбцами
            val cornerRadius = CornerRadius(8f, 8f)
            val chartHeight = size.height - topPadding
            val totalGap = gap * (count + 1)
            val barWidth = ((size.width - totalGap) / count).coerceAtLeast(1f)

            data.forEachIndexed { index, item ->
                val barHeight = if (maxValue > 0f) (item.value / maxValue) * chartHeight else 0f
                val left = gap + index * (barWidth + gap)
                val top = topPadding + (chartHeight - barHeight)

                drawRoundRect(
                    color = item.color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                val valueLayout = textMeasurer.measure(valueFormatter(item.value), valueStyle)
                val textX = (left + barWidth / 2f - valueLayout.size.width / 2f)
                    .coerceIn(0f, size.width - valueLayout.size.width)
                drawText(
                    textLayoutResult = valueLayout,
                    topLeft = Offset(textX, (top - valueLayout.size.height - 2f).coerceAtLeast(0f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Легенда: цвет — название — значение
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
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = item.color)
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = valueFormatter(item.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
