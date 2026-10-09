package com.carlog.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.FuelConsumptionCalculator
import com.carlog.domain.model.FuelGroupConsumption
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Средний расход и пробег на баке одной группы топлива — общий блок экрана заправок
 * и вкладки «Топливо» статистики, чтобы оба экрана показывали одно и то же.
 *
 * Точка отсчёта общая для машины: запрос, отмена и сброс действуют на все типы топлива.
 */
@Composable
fun FuelConsumptionBlock(
    summary: FuelGroupConsumption,
    resetPending: Boolean,
    consumptionSince: LocalDate?,
    onRequestReset: () -> Unit,
    onCancelPending: () -> Unit,
    onClearReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.fuel_reset_dialog_title)) },
            text = { Text(stringResource(R.string.fuel_reset_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onRequestReset()
                    }
                ) {
                    Text(stringResource(R.string.fuel_reset_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val consumption = summary.consumption
    val approx = consumption?.isApproximate == true

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValueLine(
                label = stringResource(R.string.fuel_average_consumption),
                value = if (consumption != null) {
                    (if (approx) "≈ " else "") + stringResource(
                        R.string.fuel_consumption_value,
                        formatAmount(consumption.per100Km),
                        summary.unit
                    )
                } else "—",
                modifier = Modifier.weight(1f)
            )
            // Пропущенная заправка сбивает средний расход навсегда:
            // отсюда пользователь назначает новую точку отсчёта
            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.fuel_reset_action),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (consumption == null) {
            Hint(
                if (consumptionSince != null) stringResource(R.string.fuel_reset_need_more)
                else stringResource(R.string.fuel_need_two_refuelings)
            )
        } else if (approx) {
            Hint(stringResource(R.string.fuel_consumption_approx_gas))
        }

        ResetState(resetPending, consumptionSince, onCancelPending, onClearReset)

        Spacer(modifier = Modifier.height(8.dp))

        val rangeLabel = stringResource(
            when (summary.group) {
                FuelConsumptionCalculator.ELECTRIC -> R.string.fuel_range_battery
                FuelConsumptionCalculator.GAS -> R.string.fuel_range_gas_tank
                else -> R.string.fuel_range_tank
            }
        )
        val range = summary.rangeKm
        ValueLine(
            label = rangeLabel,
            value = if (range != null) {
                (if (approx) "≈ " else "") +
                    stringResource(R.string.fuel_range_value, DecimalFormat("#,##0").format(range))
            } else "—"
        )
        if (summary.tankCapacity == null) {
            // Машины, заведённые до 1.4.0: объёма нет, и выдумывать его нельзя
            Hint(
                stringResource(
                    when (summary.group) {
                        FuelConsumptionCalculator.ELECTRIC -> R.string.fuel_range_need_battery
                        FuelConsumptionCalculator.GAS -> R.string.fuel_range_need_gas_tank
                        else -> R.string.fuel_range_need_tank
                    }
                )
            )
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

/** С какого момента идёт расход и как вернуть расчёт по всей истории */
@Composable
private fun ResetState(
    resetPending: Boolean,
    consumptionSince: LocalDate?,
    onCancelPending: () -> Unit,
    onClearReset: () -> Unit
) {
    val (hint, actionLabel, action) = when {
        resetPending -> Triple(
            stringResource(R.string.fuel_reset_pending),
            stringResource(R.string.fuel_reset_pending_cancel),
            onCancelPending
        )
        consumptionSince != null -> Triple(
            stringResource(
                R.string.fuel_reset_active,
                consumptionSince.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            ),
            stringResource(R.string.fuel_reset_undo),
            onClearReset
        )
        else -> return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 2.dp)
        )
        TextButton(
            onClick = action,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(text = actionLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatAmount(value: Double): String = DecimalFormat("#,##0.0").format(value)
