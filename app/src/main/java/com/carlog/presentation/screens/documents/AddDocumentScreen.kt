package com.carlog.presentation.screens.documents

import com.carlog.presentation.util.LocalCurrency
import com.carlog.util.PickerDates
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.presentation.components.SingleAttachmentPicker
import com.carlog.domain.model.DocumentTypes
import com.carlog.util.FileHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddDocumentViewModel = hiltViewModel()
) {
    val type by viewModel.type.collectAsState()
    val customName by viewModel.customName.collectAsState()
    val customNameError by viewModel.customNameError.collectAsState()
    val number by viewModel.number.collectAsState()
    val organization by viewModel.organization.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val expiryDate by viewModel.expiryDate.collectAsState()
    val expiryDateError by viewModel.expiryDateError.collectAsState()
    val cost by viewModel.cost.collectAsState()
    val costError by viewModel.costError.collectAsState()
    val photoPath by viewModel.photoPath.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    val screenTitle = when {
        viewModel.isEditMode -> stringResource(R.string.edit_document)
        viewModel.isRenewMode -> stringResource(R.string.renew_document)
        else -> stringResource(R.string.add_document)
    }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showExpiryDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveDocument() }, enabled = !isSaving) {
                        Icon(Icons.Default.Check, stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Тип документа (при продлении не меняется)
            ExposedDropdownMenuBox(
                expanded = showTypeMenu,
                onExpandedChange = { if (!viewModel.isRenewMode) showTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !viewModel.isRenewMode,
                    label = { Text(stringResource(R.string.document_type)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    DocumentTypes.ALL.forEach { docType ->
                        DropdownMenuItem(
                            text = { Text(docType) },
                            onClick = {
                                viewModel.updateType(docType)
                                showTypeMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Description, null)
                            }
                        )
                    }
                }
            }

            // Название (только для типа «Другое»)
            if (type == DocumentTypes.OTHER) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { viewModel.updateCustomName(it) },
                    label = { Text(stringResource(R.string.document_custom_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = customNameError != null,
                    supportingText = customNameError?.let { { Text(it) } }
                )
            }

            val isTax = type == DocumentTypes.VEHICLE_TAX

            // Номер и организация: у транспортного налога их нет — скрываем
            if (!isTax) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { viewModel.updateNumber(it) },
                    label = { Text(stringResource(R.string.document_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = organization,
                    onValueChange = { viewModel.updateOrganization(it) },
                    label = { Text(stringResource(R.string.document_organization)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Store, null)
                    }
                )
            }

            // Дата начала действия (опционально; у налога нет — там только дата начисления)
            if (!isTax) OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showStartDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.document_start_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = startDate?.let { dateFormat.format(Date(it)) }
                                ?: stringResource(R.string.not_specified),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Дата окончания действия (обязательно)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showExpiryDatePicker = true },
                border = if (expiryDateError != null) {
                    CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                    )
                } else CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(
                                if (isTax) R.string.document_tax_accrual_date
                                else R.string.document_expiry_date
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (expiryDateError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = expiryDate?.let { dateFormat.format(Date(it)) }
                                ?: stringResource(R.string.not_specified),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (isTax) {
                            Text(
                                text = stringResource(R.string.document_tax_accrual_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        expiryDateError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Стоимость
            OutlinedTextField(
                value = cost,
                onValueChange = { viewModel.updateCost(it) },
                label = { Text(stringResource(R.string.document_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = costError != null,
                supportingText = costError?.let { { Text(it) } },
                trailingIcon = {
                    Text(LocalCurrency.current.symbol, style = MaterialTheme.typography.bodyLarge)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Вложение документа: фото полиса или его PDF
            Text(
                text = stringResource(R.string.document_attachment),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SingleAttachmentPicker(
                attachment = photoPath,
                onSelected = viewModel::updatePhoto,
                onRemove = viewModel::removePhoto
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Заметки
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // Кнопка сохранения (дублирует галочку в AppBar)
            Button(
                onClick = { viewModel.saveDocument() },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PickerDates.toPicker(startDate ?: System.currentTimeMillis())
        )

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            viewModel.updateStartDate(PickerDates.fromPicker(selectedDate, startDate ?: System.currentTimeMillis()))
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showExpiryDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PickerDates.toPicker(expiryDate ?: System.currentTimeMillis())
        )

        DatePickerDialog(
            onDismissRequest = { showExpiryDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            viewModel.updateExpiryDate(PickerDates.fromPicker(selectedDate, expiryDate ?: System.currentTimeMillis()))
                        }
                        showExpiryDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text(stringResource(R.string.save_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSaveError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
