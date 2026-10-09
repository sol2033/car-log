package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.OilConsumptionSummary
import com.carlog.domain.model.OilInterval
import com.carlog.presentation.screens.consumables.formatPer1000
import com.carlog.presentation.screens.statistics.charts.BarChartData
import com.carlog.presentation.screens.statistics.charts.VicoBarChart
import java.text.DecimalFormat

/**
 * Расход масла во вкладке «Расходники»: средний за историю и по каждому маслу —
 * чтобы видеть, вырос ли масложор на конкретном масле.
 */
@Composable
fun OilConsumptionStatisticsCard(summary: OilConsumptionSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.oil_consumption_title), style = MaterialTheme.typography.titleMedium)

            MetricRow(
                label = stringResource(R.string.oil_consumption_overall),
                value = summary.overallPer1000Km?.let { formatPer1000(it, summary.overallIsApproximate) } ?: "—"
            )
            if (summary.overallPer1000Km == null) {
                Hint(stringResource(R.string.oil_overall_need_replacement))
            }
            if (summary.overallUsesGuessedDipstick) {
                Text(
                    stringResource(R.string.oil_dipstick_guess_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            summary.current?.let { current ->
                MetricRow(
                    label = stringResource(R.string.oil_consumption_current),
                    value = current.per1000Km?.let { formatPer1000(it, current.isApproximate) } ?: "—"
                )
            }

            // По маслам: столбик на каждое заменённое — рост сразу виден
            val completed = summary.intervals.filter { it.isCompleted && it.per1000Km != null }
            if (completed.size >= 2) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.oil_chart_title), style = MaterialTheme.typography.titleSmall)
                VicoBarChart(
                    data = completed.mapIndexed { index, interval ->
                        BarChartData(label = "${index + 1}", value = interval.per1000Km!!.toFloat())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            summary.intervals.asReversed().forEachIndexed { reversedIndex, interval ->
                IntervalRow(number = summary.intervals.size - reversedIndex, interval = interval)
            }
        }
    }
}

@Composable
private fun IntervalRow(number: Int, interval: OilInterval) {
    val liters = DecimalFormat("0.##")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$number. " + (interval.oil.manufacturer ?: stringResource(R.string.oil_interval_unnamed)) +
                    if (!interval.isCompleted) " · " + stringResource(R.string.oil_interval_current) else "",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(interval.per1000Km?.let { formatPer1000(it, interval.isApproximate) } ?: "—")
        }
        val drop = interval.levelDrop
        Hint(
            "${interval.startMileage} → ${interval.endMileage} км · " +
                stringResource(R.string.oil_interval_topped_up, liters.format(interval.topUpLiters)) +
                when {
                    drop != null -> " · " + stringResource(R.string.oil_interval_level_drop, liters.format(drop.liters))
                    interval.isCompleted -> " · " + stringResource(R.string.oil_level_drop_not_set)
                    else -> ""
                }
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
