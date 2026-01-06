package com.carlog.presentation.screens.breakdowns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBreakdownScreen(
    carId: Long,
    breakdownId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddBreakdownViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
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
                        if (state.breakdownId != null) stringResource(R.string.edit_breakdown)
                        else stringResource(R.string.add_breakdown)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveBreakdown() },
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
                    text = stringResource(R.string.breakdown_info_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text(stringResource(R.string.breakdown_title)) },
                    isError = state.titleError != null,
                    supportingText = state.titleError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text(stringResource(R.string.breakdown_description)) },
                    minLines = 3,
                    isError = state.descriptionError != null,
                    supportingText = state.descriptionError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                var showDatePicker by remember { mutableStateOf(false) }
                
                OutlinedTextField(
                    value = formatDate(state.breakdownDate),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.breakdown_date)) },
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
                        initialSelectedDateMillis = state.breakdownDate
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        viewModel.updateBreakdownDate(millis)
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
                    value = state.breakdownMileage,
                    onValueChange = viewModel::updateBreakdownMileage,
                    label = { Text(stringResource(R.string.breakdown_mileage)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.breakdownMileageError != null,
                    supportingText = state.breakdownMileageError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.repair_cost_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Переключатель типа ввода запчастей
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.useGeneralPartsCost,
                        onClick = { viewModel.toggleUseGeneralPartsCost(true) }
                    )
                    Text(
                        text = stringResource(R.string.general_parts_cost),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !state.useGeneralPartsCost,
                        onClick = { viewModel.toggleUseGeneralPartsCost(false) }
                    )
                    Text(
                        text = stringResource(R.string.specific_parts_list),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                if (state.useGeneralPartsCost) {
                    OutlinedTextField(
                        value = state.partsCost,
                        onValueChange = viewModel::updatePartsCost,
                        label = { Text(stringResource(R.string.parts_cost)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.partsCostError != null,
                        supportingText = state.partsCostError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Форма добавления конкретной запчасти
                    var newPartName by remember { mutableStateOf("") }
                    var newPartPrice by remember { mutableStateOf("") }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = newPartName,
                            onValueChange = { newPartName = it },
                            label = { Text(stringResource(R.string.part_name_breakdown_label)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newPartPrice,
                            onValueChange = { newPartPrice = it },
                            label = { Text(stringResource(R.string.part_price_breakdown_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val price = newPartPrice.toDoubleOrNull()
                                if (newPartName.isNotBlank() && price != null && price > 0) {
                                    viewModel.addPart(newPartName, price)
                                    newPartName = ""
                                    newPartPrice = ""
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, stringResource(R.string.add))
                        }
                    }
                    
                    if (state.partsCostError != null) {
                        Text(
                            text = state.partsCostError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    
                    // Список добавленных запчастей
                    state.addedParts.forEachIndexed { index, part ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = part.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "₽%.2f".format(part.price),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.removePart(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = if (state.isWarrantyRepair) "ремонт по гарантии" else state.serviceCost,
                    onValueChange = { if (!state.isWarrantyRepair) viewModel.updateServiceCost(it) },
                    label = { Text(stringResource(R.string.service_cost_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    readOnly = state.isWarrantyRepair,
                    enabled = !state.isWarrantyRepair,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Галочка гарантийного ремонта
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.isWarrantyRepair,
                        onCheckedChange = viewModel::toggleWarrantyRepair
                    )
                    Text(
                        text = stringResource(R.string.warranty_repair),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.service_info_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = state.serviceName,
                    onValueChange = viewModel::updateServiceName,
                    label = { Text(stringResource(R.string.service_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.serviceAddress,
                    onValueChange = viewModel::updateServiceAddress,
                    label = { Text(stringResource(R.string.service_address)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                val partsCostValue = if (state.useGeneralPartsCost) {
                    state.partsCost.toDoubleOrNull() ?: 0.0
                } else {
                    state.addedParts.sumOf { it.price }
                }
                val totalCost = partsCostValue + (state.serviceCost.toDoubleOrNull() ?: 0.0)
                
                if (totalCost > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.parts_label),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "₽%.2f".format(partsCostValue),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (state.serviceCost.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.services_label),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "₽%.2f".format(state.serviceCost.toDoubleOrNull() ?: 0.0),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.total_cost_label_long),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "₽%.2f".format(totalCost),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
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
                    onClick = viewModel::saveBreakdown,
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
                            if (state.breakdownId != null) stringResource(R.string.save_changes)
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
