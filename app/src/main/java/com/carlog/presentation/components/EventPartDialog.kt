package com.carlog.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.MaintenanceType

/**
 * Окно добавления или редактирования запчасти внутри события (обслуживание, ДТП).
 *
 * Одно на оба раздела: раньше в ДТП был свой диалог с тремя полями, а в обслуживании —
 * строка с названием и ценой, из-за чего производителя и артикул приходилось дозаполнять
 * потом в самой карточке запчасти.
 *
 * Фото копируются в хранилище сразу при выборе; если пользователь откажется от события,
 * файлы подчистит вызывающая сторона (`orphanPhotosToDelete`).
 *
 * @param showTypeToggle показывать ли выбор «Ремонт / Тюнинг». В ДТП не показывается: там
 * запчасти относятся к аварии, а не к обслуживанию.
 * @param showVisibilityToggle показывать ли галочку «Показывать в разделе „Запчасти"».
 * Для ДТП не показывается: там запчасти всегда идут в раздел.
 * @param initial редактируемая позиция; null — добавление новой.
 * @param onPhotoDiscarded фото убрали из этого окна — файл больше не нужен, если он не
 * принадлежит уже сохранённой записи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPartDialog(
    initial: EventPart?,
    onDismiss: () -> Unit,
    onConfirm: (EventPart) -> Unit,
    onPhotoDiscarded: (String) -> Unit = {},
    showVisibilityToggle: Boolean = false,
    showTypeToggle: Boolean = false
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var manufacturer by remember { mutableStateOf(initial?.manufacturer ?: "") }
    var partNumber by remember { mutableStateOf(initial?.partNumber ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var photos by remember { mutableStateOf(initial?.photosPaths ?: emptyList()) }
    var showInPartsList by remember { mutableStateOf(initial?.showInPartsList ?: true) }
    var maintenanceType by remember {
        mutableStateOf(initial?.maintenanceType ?: MaintenanceType.REPAIR)
    }

    var nameTouched by remember { mutableStateOf(false) }
    var priceTouched by remember { mutableStateOf(false) }

    val priceValue = price.replace(',', '.').toDoubleOrNull()
    val nameError = nameTouched && name.isBlank()
    val priceError = priceTouched && (priceValue == null || priceValue <= 0)
    val canConfirm = name.isNotBlank() && priceValue != null && priceValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.event_part_add_title
                    else R.string.event_part_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameTouched = true },
                    label = { Text(stringResource(R.string.event_part_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_required_field)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text(stringResource(R.string.event_part_manufacturer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = partNumber,
                    onValueChange = { partNumber = it },
                    label = { Text(stringResource(R.string.event_part_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; priceTouched = true },
                    label = { Text(stringResource(R.string.event_part_price)) },
                    isError = priceError,
                    supportingText = if (priceError) {
                        { Text(stringResource(R.string.error_required_field)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Ремонт или тюнинг — у каждой позиции свой: за один заезд могут и починить
                // сломанное, и поставить допоборудование
                if (showTypeToggle) {
                    Text(
                        text = stringResource(R.string.event_part_type),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PART_TYPES.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = maintenanceType == type,
                                onClick = { maintenanceType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, PART_TYPES.size)
                            ) {
                                Text(type.getDisplayName())
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.event_part_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showVisibilityToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showInPartsList,
                            onCheckedChange = { showInPartsList = it }
                        )
                        Text(
                            text = stringResource(R.string.event_part_show_in_list),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (!showInPartsList) {
                        Text(
                            text = stringResource(R.string.event_part_hidden_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.attachments_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                AttachmentsPicker(
                    attachments = photos,
                    onAdd = { path -> photos = photos + path },
                    onRemove = { path ->
                        photos = photos - path
                        onPhotoDiscarded(path)
                    },
                    tileSize = 72.dp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        EventPart(
                            name = name.trim(),
                            manufacturer = manufacturer.trim(),
                            partNumber = partNumber.trim(),
                            price = priceValue ?: 0.0,
                            notes = notes.trim(),
                            photosPaths = photos,
                            showInPartsList = showInPartsList,
                            maintenanceType = maintenanceType,
                            partId = initial?.partId
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Типы, которые может иметь запчасть события. ТО задаётся расходниками, а не запчастью */
private val PART_TYPES = listOf(MaintenanceType.REPAIR, MaintenanceType.MODIFICATION)
