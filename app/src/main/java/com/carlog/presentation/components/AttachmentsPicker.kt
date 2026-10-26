package com.carlog.presentation.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.util.Attachments
import com.carlog.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Вложения записи — фотографии и PDF в одном ряду.
 *
 * Один компонент на все формы: раньше каждый экран нёс свою копию «плитка плюс пикер»,
 * и поддержку PDF пришлось бы дописывать в пять мест по-разному. Файл копируется во
 * внутреннее хранилище сразу при выборе, поэтому [onAdd] получает уже готовый путь —
 * чистка осиротевших файлов остаётся за вызывающей стороной (см. `orphanPhotosToDelete`).
 *
 * @param title заголовок над рядом; null — заголовок рисует сам экран, а кнопку добавления
 * заменяет первая плитка ряда.
 * @param mainAttachment вложение, выделенное рамкой (обложка автомобиля).
 * @param onImageClick нажатие на картинку; null — картинка ни на что не реагирует.
 * PDF всегда открывается во внешнем просмотрщике, [onImageClick] на него не действует.
 */
@Composable
fun AttachmentsPicker(
    attachments: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tileSize: Dp = 120.dp,
    emptyHint: String? = null,
    mainAttachment: String? = null,
    onImageClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val saveAttachment = rememberAttachmentSaver()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { saveAttachment(it, false, onAdd) }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // PDF копируем в хранилище так же, как фото: сырой content:// URI со временем
        // протухает и не попадает в бэкапы
        uri?.let { saveAttachment(it, true, onAdd) }
    }

    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val launchPdfPicker = { pdfPickerLauncher.launch(PDF_MIME_TYPE) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box {
                    OutlinedButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.attachment_add))
                    }
                    AttachmentSourceMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onPickImage = launchPhotoPicker,
                        onPickPdf = launchPdfPicker
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Без заголовка кнопки «Добавить» нет — её роль играет первая плитка ряда
            if (title == null) {
                item {
                    Box {
                        OutlinedIconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(tileSize)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.attachment_add)
                            )
                        }
                        AttachmentSourceMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onPickImage = launchPhotoPicker,
                            onPickPdf = launchPdfPicker
                        )
                    }
                }
            }

            items(attachments, key = { it }) { path ->
                Box(modifier = Modifier.size(tileSize)) {
                    AttachmentTile(
                        path = path,
                        size = tileSize,
                        isMain = path == mainAttachment,
                        onImageClick = onImageClick
                    )
                    IconButton(
                        onClick = { onRemove(path) },
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

        if (attachments.isEmpty() && emptyHint != null) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Вложения только для чтения — экраны деталей и списков.
 * PDF открывается нажатием, картинка ни на что не реагирует.
 */
@Composable
fun AttachmentsRow(
    attachments: List<String>,
    modifier: Modifier = Modifier,
    tileSize: Dp = 150.dp
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(attachments, key = { it }) { path ->
            AttachmentTile(path = path, size = tileSize)
        }
    }
}

/**
 * Одиночное вложение — там, где запись хранит ровно один файл (фото документа).
 * Пустой слот и нажатие на заполненный открывают тот же выбор источника, что и ряд вложений.
 */
@Composable
fun SingleAttachmentPicker(
    attachment: String?,
    onSelected: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    emptySize: Dp = 100.dp,
    filledSize: Dp = 150.dp
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val saveAttachment = rememberAttachmentSaver()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { saveAttachment(it, false, onSelected) }
    }
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAttachment(it, true, onSelected) }
    }

    Box(modifier = modifier) {
        if (attachment == null) {
            Card(
                modifier = Modifier
                    .size(emptySize)
                    .clickable { menuExpanded = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.attachment_add),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(filledSize)) {
                // Нажатие по вложению заменяет его — как раньше нажатие по фото
                AttachmentTile(
                    path = attachment,
                    size = filledSize,
                    onImageClick = { menuExpanded = true }
                )
                IconButton(
                    onClick = onRemove,
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

        AttachmentSourceMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onPickImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickPdf = { pdfPickerLauncher.launch(PDF_MIME_TYPE) }
        )
    }
}

/** Плитка вложения: миниатюра для картинки, карточка с иконкой для PDF */
@Composable
fun AttachmentTile(
    path: String,
    size: Dp,
    isMain: Boolean = false,
    onImageClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val borderModifier = if (isMain) {
        Modifier.border(
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(8.dp)
        )
    } else {
        Modifier
    }

    if (Attachments.isPdf(path)) {
        Card(
            modifier = Modifier
                .size(size)
                .clickable {
                    if (!FileHelper.openPdf(context, path)) {
                        Toast.makeText(context, R.string.attachment_open_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                .then(borderModifier),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = stringResource(R.string.attachment_pdf),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(size / 3)
                )
                Text(
                    text = stringResource(R.string.attachment_pdf),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    } else {
        Box(modifier = Modifier.size(size)) {
            AsyncImage(
                model = path,
                contentDescription = stringResource(R.string.attachment_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .then(borderModifier)
                    .then(
                        if (onImageClick != null) {
                            Modifier.clickable { onImageClick(path) }
                        } else {
                            Modifier
                        }
                    )
            )
            if (isMain) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.attachment_main_photo),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentSourceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.attachment_source_photo)) },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            onClick = {
                onDismiss()
                onPickImage()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.attachment_source_pdf)) },
            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
            onClick = {
                onDismiss()
                onPickPdf()
            }
        )
    }
}

/**
 * Копирует выбранный файл во внутреннее хранилище **в фоне** и отдаёт путь на главном потоке.
 *
 * Раньше копирование шло прямо в колбэке выбора, то есть в UI-потоке: декодирование,
 * поворот и пережатие фото, копирование PDF. Файл из облачного провайдера ещё и скачивается
 * при чтении — экран замирал, вплоть до ANR. Неудача с фото к тому же проходила молча.
 *
 * @return (uri, это PDF, куда отдать сохранённый путь)
 */
@Composable
internal fun rememberAttachmentSaver(): (Uri, Boolean, (String) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { uri: Uri, isPdf: Boolean, onSaved: (String) -> Unit ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    if (isPdf) {
                        FileHelper.saveDocumentToInternalStorage(context, uri)
                    } else {
                        FileHelper.saveImageToInternalStorage(context, uri)
                    }
                }
                if (saved != null) {
                    onSaved(saved)
                } else {
                    val message = if (isPdf) R.string.attachment_save_failed else R.string.photo_save_failed
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
