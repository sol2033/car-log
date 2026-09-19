package com.carlog.presentation.screens.fines

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
import com.carlog.domain.model.FineViolationTypes
import com.carlog.presentation.components.AttachmentsPicker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFineScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddFineViewModel = hiltViewModel()
) {
    val date by viewModel.date.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val amountError by viewModel.amountError.collectAsState()
    val violationType by viewModel.violationType.collectAsState()
    val customViolation by viewModel.customViolation.collectAsState()
    val customViolationError by viewModel.customViolationError.collectAsState()
    val location by viewModel.location.collectAsState()
    val decisionNumber by viewModel.decisionNumber.collectAsState()
    val issuer by viewModel.issuer.collectAsState()
    val discountUntil by viewModel.discountUntil.collectAsState()
    val isPaid by viewModel.isPaid.collectAsState()
    val paidDate by viewModel.paidDate.collectAsState()
    val paidAmount by viewModel.paidAmount.collectAsState()
    val paidAmountError by viewModel.paidAmountError.collectAsState()
    val photosPaths by viewModel.photosPaths.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    var showTypeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscountPicker by remember { mutableStateOf(false) }
    var showPaidDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isEditMode) R.string.edit_fine else R.string.add_fine
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveFine() }) {
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
            // Дата постановления
            DateCard(
                label = stringResource(R.string.fine_date),
                value = dateFormat.format(Date(date)),
                onClick = { showDatePicker = true }
            )

            // Сумма по постановлению
            OutlinedTextField(
                value = amount,
                onValueChange = viewModel::updateAmount,
                label = { Text(stringResource(R.string.fine_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } }
            )

            // Тип нарушения
            ExposedDropdownMenuBox(
                expanded = showTypeMenu,
                onExpandedChange = { showTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = violationType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fine_violation_type)) },
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
                    FineViolationTypes.ALL.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.updateViolationType(type)
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Своя формулировка — только для «Другого»
            if (violationType == FineViolationTypes.OTHER) {
                OutlinedTextField(
                    value = customViolation,
                    onValueChange = viewModel::updateCustomViolation,
                    label = { Text(stringResource(R.string.fine_violation_custom)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = customViolationError != null,
                    supportingText = customViolationError?.let { { Text(it) } }
                )
            }

            OutlinedTextField(
                value = location,
                onValueChange = viewModel::updateLocation,
                label = { Text(stringResource(R.string.fine_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Place, null) }
            )

            OutlinedTextField(
                value = decisionNumber,
                onValueChange = viewModel::updateDecisionNumber,
                label = { Text(stringResource(R.string.fine_decision_number)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.fine_decision_number_hint)) }
            )

            OutlinedTextField(
                value = issuer,
                onValueChange = viewModel::updateIssuer,
                label = { Text(stringResource(R.string.fine_issuer)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AccountBalance, null) }
            )

            // Льготный срок: по нему считается светофор в списке
            DateCard(
                label = stringResource(R.string.fine_discount_until),
                value = discountUntil?.let { dateFormat.format(Date(it)) }
                    ?: stringResource(R.string.not_specified),
                hint = stringResource(R.string.fine_discount_hint),
                onClick = { showDiscountPicker = true },
                onClear = if (discountUntil != null) {
                    { viewModel.updateDiscountUntil(null) }
                } else null
            )

            HorizontalDivider()

            // Оплата
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fine_is_paid),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.fine_is_paid_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isPaid, onCheckedChange = viewModel::updateIsPaid)
            }

            if (isPaid) {
                DateCard(
                    label = stringResource(R.string.fine_paid_date),
                    value = paidDate?.let { dateFormat.format(Date(it)) }
                        ?: stringResource(R.string.not_specified),
                    onClick = { showPaidDatePicker = true }
                )

                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = viewModel::updatePaidAmount,
                    label = { Text(stringResource(R.string.fine_paid_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = paidAmountError != null,
                    supportingText = {
                        Text(
                            paidAmountError ?: stringResource(R.string.fine_paid_amount_hint)
                        )
                    }
                )
            }

            HorizontalDivider()

            AttachmentsPicker(
                attachments = photosPaths,
                onAdd = viewModel::addPhoto,
                onRemove = viewModel::removePhoto,
                title = stringResource(R.string.fine_attachments),
                emptyHint = stringResource(R.string.fine_attachments_hint)
            )

            HorizontalDivider()

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Button(
                onClick = { viewModel.saveFine() },
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

    if (showDatePicker) {
        FineDatePicker(
            initial = date,
            onDismiss = { showDatePicker = false },
            onSelected = viewModel::updateDate
        )
    }

    if (showDiscountPicker) {
        FineDatePicker(
            initial = discountUntil ?: date,
            onDismiss = { showDiscountPicker = false },
            onSelected = { viewModel.updateDiscountUntil(it) }
        )
    }

    if (showPaidDatePicker) {
        FineDatePicker(
            initial = paidDate ?: System.currentTimeMillis(),
            onDismiss = { showPaidDatePicker = false },
            onSelected = viewModel::updatePaidDate
        )
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

/** Поле-дата: карточка с подписью, как в форме документа */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    hint: String? = null,
    onClear: (() -> Unit)? = null
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
                hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FineDatePicker(
    initial: Long,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let(onSelected)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}
