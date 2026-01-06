package com.carlog.presentation.screens.accidents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccidentScreen(
    carId: Long,
    accidentId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddAccidentViewModel = hiltViewModel()
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
                    Text(if (accidentId != null) stringResource(R.string.edit_accident) else stringResource(R.string.add_accident))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveAccident() }
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Дата и пробег
            DateAndMileageSection(
                date = state.date,
                mileage = state.mileage,
                mileageError = state.mileageError,
                onDateChange = viewModel::updateDate,
                onMileageChange = viewModel::updateMileage
            )
            
            // Место ДТП
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text(stringResource(R.string.accident_location)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Place, contentDescription = null)
                }
            )
            
            // Описание повреждений
            OutlinedTextField(
                value = state.damageDescription,
                onValueChange = viewModel::updateDamageDescription,
                label = { Text(stringResource(R.string.damage_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                isError = state.damageDescriptionError != null,
                supportingText = state.damageDescriptionError?.let { { Text(it) } }
            )
            
            // Серьезность
            SeveritySection(
                severity = state.severity,
                onSeverityChange = viewModel::updateSeverity
            )
            
            HorizontalDivider()
            
            // Роль в ДТП (виновник/пострадавший)
            UserRoleSection(
                isUserAtFault = state.isUserAtFault,
                onToggle = viewModel::toggleUserAtFault
            )
            
            HorizontalDivider()
            
            // Выплаты
            PayoutsSection(
                isUserAtFault = state.isUserAtFault,
                hasOsagoPayout = state.hasOsagoPayout,
                osagoPayout = state.osagoPayout,
                hasKaskoPayout = state.hasKaskoPayout,
                kaskoPayout = state.kaskoPayout,
                hasCulpritPayout = state.hasCulpritPayout,
                culpritPayout = state.culpritPayout,
                onToggleOsago = viewModel::toggleOsagoPayout,
                onOsagoAmountChange = viewModel::updateOsagoPayout,
                onToggleKasko = viewModel::toggleKaskoPayout,
                onKaskoAmountChange = viewModel::updateKaskoPayout,
                onToggleCulprit = viewModel::toggleCulpritPayout,
                onCulpritAmountChange = viewModel::updateCulpritPayout
            )
            
            HorizontalDivider()
            
            // Ремонт
            RepairSection(
                usePartsForRepair = state.usePartsForRepair,
                addedParts = state.addedParts,
                serviceCost = state.serviceCost,
                totalRepairCost = state.totalRepairCost,
                onToggleRepairMethod = viewModel::toggleRepairMethod,
                onAddPart = { name, manufacturer, price ->
                    viewModel.addPart(name, manufacturer, price)
                },
                onRemovePart = { index ->
                    state.addedParts.getOrNull(index)?.let { viewModel.removePart(it) }
                },
                onServiceCostChange = viewModel::updateServiceCost,
                onTotalRepairCostChange = viewModel::updateTotalRepairCost
            )
            
            HorizontalDivider()
            
            // Фотографии
            PhotosSection(
                photosPaths = state.photosPaths,
                onAddPhoto = viewModel::addPhoto,
                onRemovePhoto = viewModel::removePhoto
            )
            
            HorizontalDivider()
            
            // Документ PDF
            DocumentSection(
                documentPath = state.documentPath,
                onDocumentSelected = viewModel::updateDocumentPath
            )
            
            // Заметки
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            // Кнопка сохранения
            Button(
                onClick = viewModel::saveAccident,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (accidentId != null) "Сохранить изменения" else "Добавить ДТП")
                }
            }
            
            // Ошибка
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAndMileageSection(
    date: Long,
    mileage: String,
    mileageError: String?,
    onDateChange: (Long) -> Unit,
    onMileageChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Дата
        OutlinedTextField(
            value = formatDate(date),
            onValueChange = {},
            label = { Text(stringResource(R.string.accident_date)) },
            readOnly = true,
            modifier = Modifier.weight(1f),
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, "Выбрать дату")
                }
            }
        )
        
        // Пробег
        OutlinedTextField(
            value = mileage,
            onValueChange = onMileageChange,
            label = { Text(stringResource(R.string.mileage)) },
            modifier = Modifier.weight(1f),
            isError = mileageError != null,
            supportingText = mileageError?.let { { Text(it) } },
            suffix = { Text(stringResource(R.string.km)) }
        )
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(onDateChange)
                    showDatePicker = false
                }) {
                    Text("OK")
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
}

@Composable
private fun SeveritySection(
    severity: String,
    onSeverityChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.damage_severity),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        val severities = listOf(
            stringResource(R.string.severity_minor),
            stringResource(R.string.severity_moderate),
            stringResource(R.string.severity_serious),
            stringResource(R.string.severity_total)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            severities.forEach { sev ->
                FilterChip(
                    selected = severity == sev,
                    onClick = { onSeverityChange(sev) },
                    label = { Text(sev) }
                )
            }
        }
    }
}

@Composable
private fun UserRoleSection(
    isUserAtFault: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.my_role_in_accident),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = !isUserAtFault,
                onClick = { onToggle(false) }
            )
            Text(stringResource(R.string.i_am_victim), modifier = Modifier.padding(start = 8.dp))
            
            Spacer(modifier = Modifier.width(16.dp))
            
            RadioButton(
                selected = isUserAtFault,
                onClick = { onToggle(true) }
            )
            Text(stringResource(R.string.i_am_at_fault), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
