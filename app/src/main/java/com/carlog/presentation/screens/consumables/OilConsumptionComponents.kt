package com.carlog.presentation.screens.consumables

import com.carlog.presentation.util.LocalCurrency
import com.carlog.util.PickerDates
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.BreakdownFormRules
import com.carlog.domain.model.Car
import com.carlog.domain.model.CarFormRules
import com.carlog.domain.model.OilConsumptionCalculator
import com.carlog.domain.model.OilInterval
import com.carlog.domain.model.OilLevelDropInput
import com.carlog.domain.model.OilTopUp
import com.carlog.util.toDecimalOrNull
import com.carlog.util.toMileageOrNull
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// === Падение уровня к замене ===

enum class OilLevelDropMode { NOT_CHECKED, NO_DROP, MILLILITERS, DIPSTICK }

/** Черновик ответа «насколько упал уровень» — общий для окна замены и последующей правки */
class OilLevelDropDraft(initialMl: Int?, initialFraction: Double?, car: Car) {
    var mode by mutableStateOf(
        when {
            initialFraction != null -> OilLevelDropMode.DIPSTICK
            initialMl == 0 -> OilLevelDropMode.NO_DROP
            initialMl != null -> OilLevelDropMode.MILLILITERS
            else -> OilLevelDropMode.NOT_CHECKED
        }
    )
    var mlText by mutableStateOf(initialMl?.takeIf { it > 0 }?.toString() ?: "")
    var fraction by mutableStateOf(initialFraction ?: 0.5)

    // Объём MIN–MAX спрашиваем, только если у машины его ещё нет
    val needsDipstickVolume = car.oilDipstickVolume == null
    var dipstickVolumeText by mutableStateOf("")
    var dipstickUnknown by mutableStateOf(false)

    var error by mutableStateOf<String?>(null)

    /** Ответ для сохранения или null, если в полях ошибка (её текст — в [error]) */
    fun toInput(): OilLevelDropInput? {
        error = null
        return when (mode) {
            OilLevelDropMode.NOT_CHECKED -> OilLevelDropInput(ml = null, fraction = null)
            OilLevelDropMode.NO_DROP -> OilLevelDropInput(ml = 0, fraction = null)
            OilLevelDropMode.MILLILITERS -> {
                val ml = mlText.toMileageOrNull()
                if (ml == null || ml <= 0) {
                    error = "Введите целое число мл"
                    null
                } else OilLevelDropInput(ml = ml, fraction = null)
            }
            OilLevelDropMode.DIPSTICK -> {
                if (needsDipstickVolume && !dipstickUnknown &&
                    CarFormRules.volumeError(dipstickVolumeText, required = true) != null
                ) {
                    error = "Укажите объём между MIN и MAX или выберите «Не знаю»"
                    null
                } else OilLevelDropInput(ml = null, fraction = fraction)
            }
        }
    }

    /** Объём щупа, который надо записать в машину (если его спрашивали), и признак «не знаю» */
    fun dipstickVolumeToSave(): Pair<Double, Boolean>? {
        if (mode != OilLevelDropMode.DIPSTICK || !needsDipstickVolume) return null
        return if (dipstickUnknown) OilConsumptionCalculator.DEFAULT_DIPSTICK_VOLUME to true
        else dipstickVolumeText.toDecimalOrNull()?.let { it to false }
    }
}

@Composable
fun rememberOilLevelDropDraft(initialMl: Int?, initialFraction: Double?, car: Car) =
    remember(initialMl, initialFraction, car.oilDipstickVolume) {
        OilLevelDropDraft(initialMl, initialFraction, car)
    }

@Composable
fun OilLevelDropSection(draft: OilLevelDropDraft, car: Car) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.oil_level_drop_question),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.oil_level_drop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(
            OilLevelDropMode.NOT_CHECKED to R.string.oil_level_drop_not_checked,
            OilLevelDropMode.NO_DROP to R.string.oil_level_drop_none,
            OilLevelDropMode.MILLILITERS to R.string.oil_level_drop_ml,
            OilLevelDropMode.DIPSTICK to R.string.oil_level_drop_dipstick
        ).forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = draft.mode == mode, onClick = { draft.mode = mode }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = draft.mode == mode, onClick = { draft.mode = mode })
                Text(stringResource(label))
            }
        }

        when (draft.mode) {
            OilLevelDropMode.MILLILITERS -> OutlinedTextField(
                value = draft.mlText,
                onValueChange = { draft.mlText = it },
                label = { Text(stringResource(R.string.oil_level_drop_ml_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OilLevelDropMode.DIPSTICK -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OilConsumptionCalculator.DIPSTICK_FRACTIONS.forEach { value ->
                        FilterChip(
                            selected = draft.fraction == value,
                            onClick = { draft.fraction = value },
                            label = { Text(fractionLabel(value)) }
                        )
                    }
                }
                if (draft.needsDipstickVolume) {
                    DipstickVolumeFields(
                        volumeText = draft.dipstickVolumeText,
                        onVolumeChange = { draft.dipstickVolumeText = it },
                        unknown = draft.dipstickUnknown,
                        onUnknownChange = { draft.dipstickUnknown = it }
                    )
                }
                // Доля щупа — всегда оценка; без известного объёма MIN–MAX погрешность больше
                val guess = if (draft.needsDipstickVolume) draft.dipstickUnknown else car.oilDipstickVolumeIsGuess
                Text(
                    text = stringResource(
                        if (guess) R.string.oil_dipstick_guess_warning else R.string.oil_dipstick_approx_note
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (guess) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Unit
        }

        draft.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DipstickVolumeFields(
    volumeText: String,
    onVolumeChange: (String) -> Unit,
    unknown: Boolean,
    onUnknownChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = if (unknown) "" else volumeText,
        onValueChange = onVolumeChange,
        enabled = !unknown,
        label = { Text(stringResource(R.string.oil_dipstick_volume_label)) },
        supportingText = { Text(stringResource(R.string.oil_dipstick_volume_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUnknownChange(!unknown) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = unknown, onCheckedChange = onUnknownChange)
        Text(stringResource(R.string.oil_dipstick_unknown))
    }
}

/** Отдельное окно падения уровня: замена «на другой расходник» и правка задним числом */
@Composable
fun OilLevelDropDialog(
    initialMl: Int?,
    initialFraction: Double?,
    car: Car,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (OilLevelDropInput, Pair<Double, Boolean>?) -> Unit
) {
    val draft = rememberOilLevelDropDraft(initialMl, initialFraction, car)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oil_level_drop_title)) },
        text = { OilLevelDropSection(draft, car) },
        confirmButton = {
            TextButton(onClick = {
                draft.toInput()?.let { onConfirm(it, draft.dipstickVolumeToSave()) }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Исправить объём MIN–MAX в любой момент: пересчитаются все интервалы, указанные по щупу */
@Composable
fun DipstickVolumeDialog(car: Car, onDismiss: () -> Unit, onSave: (Double, Boolean) -> Unit) {
    var text by remember {
        mutableStateOf(
            car.oilDipstickVolume?.takeIf { !car.oilDipstickVolumeIsGuess }?.let(::formatVolume) ?: ""
        )
    }
    var unknown by remember { mutableStateOf(car.oilDipstickVolumeIsGuess) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oil_dipstick_volume_title)) },
        text = {
            Column {
                DipstickVolumeFields(text, { text = it; error = null }, unknown, { unknown = it; error = null })
                if (unknown) {
                    Text(
                        stringResource(R.string.oil_dipstick_guess_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (unknown) {
                    onSave(OilConsumptionCalculator.DEFAULT_DIPSTICK_VOLUME, true)
                } else {
                    error = CarFormRules.volumeError(text, required = true)
                    if (error == null) onSave(text.toDecimalOrNull()!!, false)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// === Долив ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OilTopUpDialog(
    existing: OilTopUp?,
    defaultMileage: Int,
    onDismiss: () -> Unit,
    onSave: (date: Long, mileage: Int, volume: Double, cost: Double?) -> Unit
) {
    var date by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var mileage by remember { mutableStateOf((existing?.mileage ?: defaultMileage).toString()) }
    var volume by remember { mutableStateOf(existing?.volume?.let(::formatVolume) ?: "") }
    var cost by remember { mutableStateOf(existing?.cost?.let(::formatVolume) ?: "") }
    var mileageError by remember { mutableStateOf<String?>(null) }
    var volumeError by remember { mutableStateOf<String?>(null) }
    var costError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Диалог закрывается не мгновенно: второй тап по «Сохранить» успевал записать второй долив
    var saved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.oil_top_up_add_title else R.string.oil_top_up_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDate(date))
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it; mileageError = null },
                    label = { Text(stringResource(R.string.mileage_km)) },
                    isError = mileageError != null,
                    supportingText = mileageError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it; volumeError = null },
                    label = { Text(stringResource(R.string.oil_top_up_volume_label)) },
                    isError = volumeError != null,
                    supportingText = volumeError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it; costError = null },
                    label = { Text(stringResource(R.string.oil_top_up_cost_label, LocalCurrency.current.symbol)) },
                    isError = costError != null,
                    supportingText = costError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (saved) return@TextButton
                mileageError = BreakdownFormRules.mileageError(mileage)
                volumeError = CarFormRules.volumeError(volume, required = true)
                // Стоимость необязательна, но введённый мусор молча не теряем
                costError = CarFormRules.volumeError(cost, required = false)
                if (mileageError == null && volumeError == null && costError == null) {
                    saved = true
                    onSave(date, mileage.toMileageOrNull()!!, volume.toDecimalOrNull()!!, cost.toDecimalOrNull())
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = PickerDates.toPicker(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = PickerDates.fromPicker(it, date) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }
}

// === Карточка расхода на экране масла ===

@Composable
fun OilConsumptionCard(
    interval: OilInterval,
    overallPer1000Km: Double?,
    overallIsApproximate: Boolean,
    car: Car,
    onAddTopUp: () -> Unit,
    onEditTopUp: (OilTopUp) -> Unit,
    onDeleteTopUp: (OilTopUp) -> Unit,
    onEditLevelDrop: () -> Unit,
    onEditDipstickVolume: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.oil_consumption_title), style = MaterialTheme.typography.titleMedium)

            DetailRow(
                stringResource(R.string.oil_consumption_this_oil),
                interval.per1000Km?.let { formatPer1000(it, interval.isApproximate) } ?: "—"
            )
            DetailRow(
                stringResource(R.string.oil_topped_up_total),
                stringResource(R.string.oil_liters_value, formatVolume(interval.topUpLiters))
            )
            if (interval.isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.oil_level_drop_short),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(levelDropText(interval))
                    IconButton(onClick = onEditLevelDrop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, stringResource(R.string.edit), modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.oil_current_interval_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            overallPer1000Km?.let {
                DetailRow(stringResource(R.string.oil_consumption_overall), formatPer1000(it, overallIsApproximate))
            }

            // Объём MIN–MAX — у машины; исправить можно в любой момент
            if (car.oilDipstickVolume != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onEditDipstickVolume),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.oil_dipstick_volume_short),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (car.oilDipstickVolumeIsGuess) stringResource(R.string.oil_dipstick_volume_guess_value)
                        else stringResource(R.string.oil_liters_value, formatVolume(car.oilDipstickVolume))
                    )
                    Icon(Icons.Default.Edit, null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                }
            }

            if (interval.topUps.isNotEmpty()) {
                HorizontalDivider()
                interval.topUps.sortedByDescending { it.mileage }.forEach { topUp ->
                    key(topUp.id) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEditTopUp(topUp) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.oil_liters_value, formatVolume(topUp.volume)),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${formatDate(topUp.date)} · ${topUp.mileage} км" +
                                    (topUp.cost?.let { " · ${formatVolume(it)} ${LocalCurrency.current.symbol}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        var confirmDelete by remember { mutableStateOf(false) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                        if (confirmDelete) {
                            AlertDialog(
                                onDismissRequest = { confirmDelete = false },
                                title = { Text(stringResource(R.string.oil_top_up_delete_title)) },
                                confirmButton = {
                                    TextButton(onClick = { confirmDelete = false; onDeleteTopUp(topUp) }) {
                                        Text(stringResource(R.string.delete))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                                }
                            )
                        }
                    }
                    }
                }
            }

            if (!interval.isCompleted) {
                OutlinedButton(onClick = onAddTopUp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.oil_top_up_action))
                }
            }
        }
    }
}

@Composable
private fun levelDropText(interval: OilInterval): String {
    val oil = interval.oil
    return when {
        oil.oilLevelDropMl == 0 -> stringResource(R.string.oil_level_drop_none)
        oil.oilLevelDropMl != null -> stringResource(R.string.oil_ml_value, oil.oilLevelDropMl)
        oil.oilLevelDropFraction != null -> {
            val liters = interval.levelDrop?.liters ?: 0.0
            "${fractionLabel(oil.oilLevelDropFraction)} ≈ " + stringResource(R.string.oil_liters_value, formatVolume(liters))
        }
        else -> stringResource(R.string.oil_level_drop_not_set)
    }
}

@Composable
fun formatPer1000(value: Double, approximate: Boolean): String =
    (if (approximate) "≈ " else "") + stringResource(R.string.oil_per_1000_value, DecimalFormat("0.00").format(value))

fun fractionLabel(value: Double): String = when (value) {
    0.25 -> "¼"
    0.5 -> "½"
    0.75 -> "¾"
    1.0 -> "до MIN"
    else -> DecimalFormat("0.##").format(value)
}

private fun formatVolume(value: Double): String = DecimalFormat("0.##").format(value)

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
