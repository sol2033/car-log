package com.carlog.presentation.screens.parts

import com.carlog.presentation.util.LocalCurrency
import com.carlog.util.formatCurrency
import com.carlog.util.PickerDates
import com.carlog.util.toDecimalOrNull
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.presentation.components.AttachmentsPicker
import com.carlog.presentation.components.NameSuggestionChips
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddPartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val serviceNameSuggestions by viewModel.serviceNameSuggestions.collectAsState()
    
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.partId != null) stringResource(R.string.edit_part)
                        else stringResource(R.string.add_part)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.savePart() },
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.basic_info),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text(stringResource(R.string.part_name)) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.manufacturer,
                    onValueChange = viewModel::updateManufacturer,
                    label = { Text(stringResource(R.string.manufacturer)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.partNumber,
                    onValueChange = viewModel::updatePartNumber,
                    label = { Text(stringResource(R.string.part_number)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.installation_info),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                var showDatePicker by remember { mutableStateOf(false) }
                
                OutlinedTextField(
                    value = formatDate(state.installDate),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.install_date)) },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, stringResource(R.string.select_date))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = PickerDates.toPicker(state.installDate)
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        viewModel.updateInstallDate(PickerDates.fromPicker(millis, state.installDate))
                                    }
                                    showDatePicker = false
                                }
                            ) {
                                Text(stringResource(R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                
                OutlinedTextField(
                    value = state.installMileage,
                    onValueChange = viewModel::updateInstallMileage,
                    label = { Text(stringResource(R.string.install_mileage)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.installMileageError != null,
                    supportingText = state.installMileageError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = stringResource(R.string.installation_type_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.installationType == "Самостоятельно",
                        onClick = { viewModel.updateInstallationType("Самостоятельно") },
                        label = { Text(stringResource(R.string.self_installation)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.installationType == "Сервис",
                        onClick = { viewModel.updateInstallationType("Сервис") },
                        label = { Text(stringResource(R.string.service_installation)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.cost_info),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::updatePrice,
                    label = { Text(stringResource(R.string.part_price, LocalCurrency.current.symbol)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.priceError != null,
                    supportingText = state.priceError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (state.installationType == "Сервис") {
                    OutlinedTextField(
                        value = state.servicePrice,
                        onValueChange = viewModel::updateServicePrice,
                        label = { Text(stringResource(R.string.service_price, LocalCurrency.current.symbol)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.servicePriceError != null,
                        supportingText = state.servicePriceError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Column {
                        OutlinedTextField(
                            value = state.serviceName,
                            onValueChange = viewModel::updateServiceName,
                            label = { Text(stringResource(R.string.service_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        NameSuggestionChips(
                            suggestions = serviceNameSuggestions,
                            onPick = viewModel::pickServiceNameSuggestion
                        )
                    }
                    
                    OutlinedTextField(
                        value = state.serviceAddress,
                        onValueChange = viewModel::updateServiceAddress,
                        label = { Text(stringResource(R.string.service_address)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                val totalCost = (state.price.toDecimalOrNull() ?: 0.0) +
                        (state.servicePrice.toDecimalOrNull() ?: 0.0)
                
                if (totalCost > 0) {
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.total_cost_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = formatCurrency(totalCost),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                AttachmentsPicker(
                    attachments = state.photosPaths,
                    onAdd = viewModel::addPhoto,
                    onRemove = viewModel::removePhoto,
                    title = stringResource(R.string.attachments_title),
                    tileSize = 100.dp,
                    emptyHint = stringResource(R.string.attachments_empty)
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Button(
                    onClick = viewModel::savePart,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            if (state.partId != null) stringResource(R.string.save_changes)
                            else stringResource(R.string.save)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
