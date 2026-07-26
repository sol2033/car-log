package com.carlog.presentation.screens.documents

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.domain.model.DocumentTypes
import com.carlog.util.DocumentStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long, Long) -> Unit,
    onNavigateToRenew: (Long, Long) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoFullScreen by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.displayName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    document?.let { doc ->
                        IconButton(onClick = { onNavigateToEdit(carId, doc.id) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { padding ->
        document?.let { doc ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Статус (только для активных документов)
                if (doc.isActive) {
                    val statusInfo = DocumentStatus.calculateStatus(doc.expiryDate)
                    val expired = statusInfo.remainingDays < 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (statusInfo.status) {
                                DocumentStatus.Status.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
                                DocumentStatus.Status.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                                DocumentStatus.Status.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = when {
                                    expired && doc.type == DocumentTypes.VEHICLE_TAX ->
                                        stringResource(R.string.document_tax_accrued_days_ago, -statusInfo.remainingDays)
                                    expired ->
                                        stringResource(R.string.document_expired_days, -statusInfo.remainingDays)
                                    // Для налога срок длинный — показываем месяцы, дни только под конец
                                    doc.type == DocumentTypes.VEHICLE_TAX && statusInfo.remainingDays >= 30 ->
                                        stringResource(R.string.document_expires_in_months, statusInfo.remainingDays / 30)
                                    else ->
                                        stringResource(R.string.document_expires_in_days, statusInfo.remainingDays)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    if (doc.type == DocumentTypes.VEHICLE_TAX) {
                                        R.string.document_tax_next_accrual_until
                                    } else {
                                        R.string.document_valid_until
                                    },
                                    dateFormat.format(Date(doc.expiryDate))
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Архивный документ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.document_archived),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Основная информация
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailRow(stringResource(R.string.document_type), doc.type)
                        doc.customName?.let {
                            DetailRow(stringResource(R.string.document_custom_name), it)
                        }
                        doc.number?.let {
                            DetailRow(stringResource(R.string.document_number), it)
                        }
                        doc.organization?.let {
                            DetailRow(stringResource(R.string.document_organization), it)
                        }
                        doc.startDate?.let {
                            DetailRow(
                                stringResource(R.string.document_start_date),
                                dateFormat.format(Date(it))
                            )
                        }
                        DetailRow(
                            stringResource(
                                if (doc.type == DocumentTypes.VEHICLE_TAX) {
                                    R.string.document_tax_next_accrual
                                } else {
                                    R.string.document_expiry_date_plain
                                }
                            ),
                            dateFormat.format(Date(doc.expiryDate))
                        )
                        doc.cost?.let {
                            DetailRow(stringResource(R.string.document_cost), "$it ₽")
                        }
                        doc.notes?.let {
                            DetailRow(stringResource(R.string.notes), it)
                        }
                    }
                }

                // Фото документа
                doc.photoPath?.let { path ->
                    Text(
                        text = stringResource(R.string.document_photo),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AsyncImage(
                        model = Uri.parse(path),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPhotoFullScreen = true },
                        contentScale = ContentScale.Crop
                    )
                }

                // Кнопка продления (только для активных)
                if (doc.isActive) {
                    Button(
                        onClick = { onNavigateToRenew(carId, doc.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.renew_document))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Полноэкранный просмотр фото
            if (showPhotoFullScreen && doc.photoPath != null) {
                Dialog(onDismissRequest = { showPhotoFullScreen = false }) {
                    AsyncImage(
                        model = Uri.parse(doc.photoPath),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPhotoFullScreen = false },
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
