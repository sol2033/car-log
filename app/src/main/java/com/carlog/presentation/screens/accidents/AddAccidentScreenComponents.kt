package com.carlog.presentation.screens.accidents

import com.carlog.presentation.util.LocalCurrency
import com.carlog.util.formatCurrency
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.carlog.util.toDecimalOrNull
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.carlog.util.FileHelper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.presentation.components.AttachmentsPicker
import com.carlog.presentation.screens.accidents.AddAccidentViewModel
import com.carlog.presentation.components.EventPart
import com.carlog.presentation.components.ConfirmDeleteDialog
import com.carlog.presentation.components.EventPartDialog

@Composable
fun PayoutsSection(
    isUserAtFault: Boolean,
    hasOsagoPayout: Boolean,
    osagoPayout: String,
    hasKaskoPayout: Boolean,
    kaskoPayout: String,
    hasCulpritPayout: Boolean,
    culpritPayout: String,
    osagoPayoutError: String?,
    kaskoPayoutError: String?,
    culpritPayoutError: String?,
    onToggleOsago: (Boolean) -> Unit,
    onOsagoAmountChange: (String) -> Unit,
    onToggleKasko: (Boolean) -> Unit,
    onKaskoAmountChange: (String) -> Unit,
    onToggleCulprit: (Boolean) -> Unit,
    onCulpritAmountChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Выплаты по страховке",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        if (isUserAtFault) {
            Text(
                text = "Доступна только выплата по КАСКО (вы виновник)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        // ОСАГО
        PayoutItem(
            title = "По ОСАГО",
            enabled = !isUserAtFault,
            checked = hasOsagoPayout,
            amount = osagoPayout,
            error = osagoPayoutError,
            onCheckedChange = onToggleOsago,
            onAmountChange = onOsagoAmountChange
        )
        
        // КАСКО
        PayoutItem(
            title = "По КАСКО",
            enabled = true,
            checked = hasKaskoPayout,
            amount = kaskoPayout,
            error = kaskoPayoutError,
            onCheckedChange = onToggleKasko,
            onAmountChange = onKaskoAmountChange
        )
        
        // От виновника
        PayoutItem(
            title = "От виновника ДТП",
            enabled = !isUserAtFault,
            checked = hasCulpritPayout,
            amount = culpritPayout,
            error = culpritPayoutError,
            onCheckedChange = onToggleCulprit,
            onAmountChange = onCulpritAmountChange
        )
    }
}

/**
 * Компенсация, выплаченная пострадавшему. Показывается только виновнику: у пострадавшего
 * такой траты быть не может. Поле необязательное — платят не всегда.
 */
@Composable
fun CompensationSection(
    hasCompensationPaid: Boolean,
    compensationPaid: String,
    error: String?,
    onToggle: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.accident_compensation_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.accident_compensation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PayoutItem(
            title = stringResource(R.string.accident_compensation_label),
            enabled = true,
            checked = hasCompensationPaid,
            amount = compensationPaid,
            error = error,
            onCheckedChange = onToggle,
            onAmountChange = onAmountChange
        )
    }
}

@Composable
private fun PayoutItem(
    title: String,
    enabled: Boolean,
    checked: Boolean,
    amount: String,
    error: String?,
    onCheckedChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { newValue -> if (enabled) onCheckedChange(newValue) },
            enabled = enabled
        )
        
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        
        if (checked) {
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.amount_label)) },
                modifier = Modifier.width(140.dp),
                suffix = { Text(LocalCurrency.current.symbol) },
                singleLine = true,
                // Без цифровой клавиатуры сюда легко попадало «15к» — и сумма молча терялась
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        }
    }
}

@Composable
fun RepairSection(
    usePartsForRepair: Boolean,
    addedParts: List<EventPart>,
    serviceCost: String,
    totalRepairCost: String,
    serviceCostError: String?,
    totalRepairCostError: String?,
    onToggleRepairMethod: (Boolean) -> Unit,
    onAddPart: (EventPart) -> Unit,
    onUpdatePart: (Int, EventPart) -> Unit,
    onRemovePart: (Int) -> Unit,
    onPhotoDiscarded: (String) -> Unit,
    onServiceCostChange: (String) -> Unit,
    onTotalRepairCostChange: (String) -> Unit
) {
    // null — окно закрыто, -1 — добавление новой запчасти, иначе индекс редактируемой
    var editedPartIndex by remember { mutableStateOf<Int?>(null) }
    // Корзинка стоит вплотную к цене — промах стирал введённую запчасть без вопроса
    var partToRemove by remember { mutableStateOf<Int?>(null) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Стоимость ремонта",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Выбор способа расчета
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = usePartsForRepair,
                onClick = { onToggleRepairMethod(true) }
            )
            Text(stringResource(R.string.by_parts_label), modifier = Modifier.padding(start = 8.dp))
            
            Spacer(modifier = Modifier.width(16.dp))
            
            RadioButton(
                selected = !usePartsForRepair,
                onClick = { onToggleRepairMethod(false) }
            )
            Text(stringResource(R.string.total_amount_label), modifier = Modifier.padding(start = 8.dp))
        }
        
        if (usePartsForRepair) {
            // Список запчастей
            if (addedParts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        addedParts.forEachIndexed { index, part ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editedPartIndex = index },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = part.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val details = listOfNotNull(
                                        part.manufacturer.ifBlank { null },
                                        part.partNumber.ifBlank { null },
                                        part.photosPaths.size.takeIf { it > 0 }
                                            ?.let { stringResource(R.string.attachments_count, it) }
                                    ).joinToString(" · ")
                                    if (details.isNotBlank()) {
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = formatCurrency(part.price),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { partToRemove = index }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Сумма запчастей:",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatCurrency(addedParts.sumOf { part -> part.price }),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Кнопка добавления запчасти
            OutlinedButton(
                onClick = { editedPartIndex = -1 },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_part_button))
            }
            
            // Стоимость работ
            OutlinedTextField(
                value = serviceCost,
                onValueChange = onServiceCostChange,
                label = { Text(stringResource(R.string.labor_cost_optional)) },
                modifier = Modifier.fillMaxWidth(),
                suffix = { Text(LocalCurrency.current.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = serviceCostError != null,
                supportingText = serviceCostError?.let { { Text(it) } },
                leadingIcon = {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
            )
            
            // Итоговая стоимость
            val partsTotal = addedParts.sumOf { part -> part.price }
            val serviceTotal = serviceCost.toDecimalOrNull() ?: 0.0
            val total = partsTotal + serviceTotal
            
            if (total > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Итого:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatCurrency(total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            // Ввод общей стоимости
            OutlinedTextField(
                value = totalRepairCost,
                onValueChange = onTotalRepairCostChange,
                label = { Text(stringResource(R.string.total_repair_cost_label)) },
                modifier = Modifier.fillMaxWidth(),
                suffix = { Text(LocalCurrency.current.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = totalRepairCostError != null,
                supportingText = totalRepairCostError?.let { { Text(it) } },
                leadingIcon = {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
            )
        }
    }
    
    partToRemove?.let { index ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_event_part_title),
            text = stringResource(
                R.string.delete_event_part_message,
                addedParts.getOrNull(index)?.name.orEmpty()
            ),
            onConfirm = { onRemovePart(index) },
            onDismiss = { partToRemove = null }
        )
    }

    editedPartIndex?.let { index ->
        EventPartDialog(
            initial = addedParts.getOrNull(index),
            onDismiss = { editedPartIndex = null },
            onConfirm = { part ->
                if (index >= 0) onUpdatePart(index, part) else onAddPart(part)
                editedPartIndex = null
            },
            onPhotoDiscarded = onPhotoDiscarded,
            // Мелочь после аварии (клипсы, герметик) можно не показывать в модуле
            // «Запчасти» — как в обслуживании. Стоимость ремонта и привязка к ДТП
            // от этого не меняются. Переключателя «Ремонт / Тюнинг» здесь по-прежнему
            // нет: запчасть относится к аварии, а не к обслуживанию
            showVisibilityToggle = true
        )
    }
}

@Composable
fun PhotosSection(
    photosPaths: List<String>,
    onAddPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    // Отдельным полем ниже живёт документ ДТП (протокол): он один и имеет свой смысл.
    // Здесь — свободные вложения: снимки повреждений и любые сопутствующие PDF
    AttachmentsPicker(
        attachments = photosPaths,
        onAdd = onAddPhoto,
        onRemove = onRemovePhoto,
        title = stringResource(R.string.attachments_title),
        emptyHint = stringResource(R.string.attachments_empty)
    )
}

@Composable
fun DocumentSection(
    documentPath: String?,
    onDocumentSelected: (String?) -> Unit
) {
    // Открепление протокола — тоже потеря: файл уже выбран и скопирован в хранилище
    var showDetachDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // Копируем PDF во внутреннее хранилище (как фото): сырой content:// URI
        // со временем протухает и не попадает в бэкапы
        uri?.let {
            FileHelper.saveDocumentToInternalStorage(context, it)?.let { savedPath ->
                onDocumentSelected(savedPath)
            }
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Документ ДТП (PDF)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedButton(
                onClick = { documentPickerLauncher.launch("application/pdf") }
            ) {
                Icon(
                    if (documentPath != null) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (documentPath != null) "Изменить" else "Добавить")
            }
        }
        
        if (documentPath != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Документ прикреплен",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    IconButton(onClick = { showDetachDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Документ не добавлен",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDetachDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_accident_document_title),
            text = stringResource(R.string.delete_accident_document_message),
            onConfirm = { onDocumentSelected(null) },
            onDismiss = { showDetachDialog = false }
        )
    }
}
