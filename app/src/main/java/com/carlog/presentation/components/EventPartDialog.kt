package com.carlog.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.util.FileHelper

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
 * @param initial редактируемая позиция; null — добавление новой.
 * @param onPhotoDiscarded фото убрали из этого окна — файл больше не нужен, если он не
 * принадлежит уже сохранённой записи.
 */
@Composable
fun EventPartDialog(
    initial: EventPart?,
    onDismiss: () -> Unit,
    onConfirm: (EventPart) -> Unit,
    onPhotoDiscarded: (String) -> Unit = {}
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var manufacturer by remember { mutableStateOf(initial?.manufacturer ?: "") }
    var partNumber by remember { mutableStateOf(initial?.partNumber ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var photos by remember { mutableStateOf(initial?.photosPaths ?: emptyList()) }

    var nameTouched by remember { mutableStateOf(false) }
    var priceTouched by remember { mutableStateOf(false) }

    val priceValue = price.replace(',', '.').toDoubleOrNull()
    val nameError = nameTouched && name.isBlank()
    val priceError = priceTouched && (priceValue == null || priceValue <= 0)
    val canConfirm = name.isNotBlank() && priceValue != null && priceValue > 0

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            FileHelper.saveImageToInternalStorage(context, it)?.let { savedPath ->
                photos = photos + savedPath
            }
        }
    }

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

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.event_part_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.event_part_photos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedIconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = stringResource(R.string.event_part_add_photo)
                            )
                        }
                    }

                    items(photos, key = { it }) { path ->
                        Box(modifier = Modifier.size(72.dp)) {
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            IconButton(
                                onClick = {
                                    photos = photos - path
                                    onPhotoDiscarded(path)
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
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
