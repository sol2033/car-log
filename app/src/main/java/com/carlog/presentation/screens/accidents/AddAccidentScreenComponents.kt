package com.carlog.presentation.screens.accidents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.carlog.util.FileHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.presentation.screens.accidents.AddAccidentViewModel
import com.carlog.presentation.screens.accidents.AddedPart

@Composable
fun PayoutsSection(
    isUserAtFault: Boolean,
    hasOsagoPayout: Boolean,
    osagoPayout: String,
    hasKaskoPayout: Boolean,
    kaskoPayout: String,
    hasCulpritPayout: Boolean,
    culpritPayout: String,
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
            onCheckedChange = onToggleOsago,
            onAmountChange = onOsagoAmountChange
        )
        
        // КАСКО
        PayoutItem(
            title = "По КАСКО",
            enabled = true,
            checked = hasKaskoPayout,
            amount = kaskoPayout,
            onCheckedChange = onToggleKasko,
            onAmountChange = onKaskoAmountChange
        )
        
        // От виновника
        PayoutItem(
            title = "От виновника ДТП",
            enabled = !isUserAtFault,
            checked = hasCulpritPayout,
            amount = culpritPayout,
            onCheckedChange = onToggleCulprit,
            onAmountChange = onCulpritAmountChange
        )
    }
}

@Composable
private fun PayoutItem(
    title: String,
    enabled: Boolean,
    checked: Boolean,
    amount: String,
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
                suffix = { Text("₽") },
                singleLine = true
            )
        }
    }
}

@Composable
fun RepairSection(
    usePartsForRepair: Boolean,
    addedParts: List<AddedPart>,
    serviceCost: String,
    totalRepairCost: String,
    onToggleRepairMethod: (Boolean) -> Unit,
    onAddPart: (name: String, manufacturer: String, price: Double) -> Unit,
    onRemovePart: (Int) -> Unit,
    onServiceCostChange: (String) -> Unit,
    onTotalRepairCostChange: (String) -> Unit
) {
    var showAddPartDialog by remember { mutableStateOf(false) }
    
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = part.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (part.manufacturer.isNotBlank()) {
                                        Text(
                                            text = part.manufacturer,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${part.price} ₽",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { onRemovePart(index) }) {
                                    Icon(Icons.Default.Delete, "Удалить")
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
                                text = "${addedParts.sumOf { part -> part.price }} ₽",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Кнопка добавления запчасти
            OutlinedButton(
                onClick = { showAddPartDialog = true },
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
                suffix = { Text("₽") },
                leadingIcon = {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
            )
            
            // Итоговая стоимость
            val partsTotal = addedParts.sumOf { part -> part.price }
            val serviceTotal = serviceCost.toDoubleOrNull() ?: 0.0
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
                            text = "$total ₽",
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
                suffix = { Text("₽") },
                leadingIcon = {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
            )
        }
    }
    
    if (showAddPartDialog) {
        AddPartDialog(
            onDismiss = { showAddPartDialog = false },
            onConfirm = { name, manufacturer, price ->
                onAddPart(name, manufacturer, price)
                showAddPartDialog = false
            }
        )
    }
}

@Composable
private fun AddPartDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, manufacturer: String, price: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var manufacturer by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_part_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.part_name_required)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text(stringResource(R.string.part_manufacturer_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.part_price_required)) },
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("₽") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val priceDouble = price.toDoubleOrNull()
                    if (name.isNotBlank() && priceDouble != null) {
                        onConfirm(name, manufacturer, priceDouble)
                    }
                },
                enabled = name.isNotBlank() && price.toDoubleOrNull() != null
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PhotosSection(
    photosPaths: List<String>,
    onAddPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            FileHelper.saveImageToInternalStorage(context, it)?.let { savedPath ->
                onAddPhoto(savedPath)
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
                text = "Фотографии",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Добавить")
            }
        }
        
        if (photosPaths.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photosPaths) { photoPath ->
                    Box {
                        AsyncImage(
                            model = Uri.parse(photoPath),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        IconButton(
                            onClick = { onRemovePhoto(photoPath) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    modifier = Modifier.padding(4.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Фотографии не добавлены",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DocumentSection(
    documentPath: String?,
    onDocumentSelected: (String?) -> Unit
) {
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onDocumentSelected(uri?.toString())
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
                    
                    IconButton(onClick = { onDocumentSelected(null) }) {
                        Icon(Icons.Default.Delete, "Удалить")
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
}
