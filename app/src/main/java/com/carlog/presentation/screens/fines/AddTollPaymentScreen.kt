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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.presentation.components.NameSuggestionChips
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTollPaymentScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddTollPaymentViewModel = hiltViewModel()
) {
    val date by viewModel.date.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val amountError by viewModel.amountError.collectAsState()
    val roadName by viewModel.roadName.collectAsState()
    val roadSuggestions by viewModel.roadSuggestions.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

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
                            if (viewModel.isEditMode) R.string.edit_toll_payment else R.string.add_toll_payment
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !isSaving) {
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
            DateCard(
                label = stringResource(R.string.toll_payment_date),
                value = dateFormat.format(Date(date)),
                onClick = { showDatePicker = true }
            )

            OutlinedTextField(
                value = amount,
                onValueChange = viewModel::updateAmount,
                label = { Text(stringResource(R.string.toll_payment_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } }
            )

            Column {
                OutlinedTextField(
                    value = roadName,
                    onValueChange = viewModel::updateRoadName,
                    label = { Text(stringResource(R.string.toll_road_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AddRoad, null) }
                )

                NameSuggestionChips(
                    suggestions = roadSuggestions,
                    onPick = viewModel::updateRoadName
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Button(
                onClick = { viewModel.save() },
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

    if (showDatePicker) {
        FineDatePicker(
            initial = date,
            onDismiss = { showDatePicker = false },
            onSelected = viewModel::updateDate
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
