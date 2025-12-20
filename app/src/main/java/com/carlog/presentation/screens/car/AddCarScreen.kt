package com.carlog.presentation.screens.car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCarScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddCarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Navigate back when saved
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить автомобиль") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
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
            // Required fields section
            Text(
                text = "Основная информация *",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::updateBrand,
                label = { Text("Марка *") },
                isError = state.brandError != null,
                supportingText = state.brandError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::updateModel,
                label = { Text("Модель *") },
                isError = state.modelError != null,
                supportingText = state.modelError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.currentMileage,
                onValueChange = viewModel::updateCurrentMileage,
                label = { Text("Текущий пробег (км) *") },
                isError = state.mileageError != null,
                supportingText = state.mileageError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Fuel Type Dropdown
            var fuelTypeExpanded by remember { mutableStateOf(false) }
            val fuelTypes = listOf(
                "Бензин АИ-92",
                "Бензин АИ-95",
                "Бензин АИ-98",
                "Дизель",
                "Газ",
                "Электричество",
                "Гибрид"
            )
            
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded }
            ) {
                OutlinedTextField(
                    value = state.fuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип топлива *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = fuelTypeExpanded,
                    onDismissRequest = { fuelTypeExpanded = false }
                ) {
                    fuelTypes.forEach { fuelType ->
                        DropdownMenuItem(
                            text = { Text(fuelType) },
                            onClick = {
                                viewModel.updateFuelType(fuelType)
                                fuelTypeExpanded = false
                            }
                        )
                    }
                }
            }
            
            Divider()
            
            // Optional fields section
            Text(
                text = "Дополнительная информация",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedTextField(
                value = state.year,
                onValueChange = viewModel::updateYear,
                label = { Text("Год выпуска") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.color,
                onValueChange = viewModel::updateColor,
                label = { Text("Цвет") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.licensePlate,
                onValueChange = viewModel::updateLicensePlate,
                label = { Text("Госномер") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.vin,
                onValueChange = viewModel::updateVin,
                label = { Text("VIN код") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Transmission Type Dropdown
            var transmissionExpanded by remember { mutableStateOf(false) }
            val transmissionTypes = listOf(
                "Автомат",
                "Механика",
                "Робот",
                "Вариатор"
            )
            
            ExposedDropdownMenuBox(
                expanded = transmissionExpanded,
                onExpandedChange = { transmissionExpanded = !transmissionExpanded }
            ) {
                OutlinedTextField(
                    value = state.transmissionType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип КПП") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transmissionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = { Text("Не выбрано") }
                )
                
                ExposedDropdownMenu(
                    expanded = transmissionExpanded,
                    onDismissRequest = { transmissionExpanded = false }
                ) {
                    transmissionTypes.forEach { transmission ->
                        DropdownMenuItem(
                            text = { Text(transmission) },
                            onClick = {
                                viewModel.updateTransmissionType(transmission)
                                transmissionExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Drive Type Dropdown
            var driveTypeExpanded by remember { mutableStateOf(false) }
            val driveTypes = listOf(
                "Передний",
                "Задний",
                "Полный"
            )
            
            ExposedDropdownMenuBox(
                expanded = driveTypeExpanded,
                onExpandedChange = { driveTypeExpanded = !driveTypeExpanded }
            ) {
                OutlinedTextField(
                    value = state.driveType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Привод") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driveTypeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = { Text("Не выбрано") }
                )
                
                ExposedDropdownMenu(
                    expanded = driveTypeExpanded,
                    onDismissRequest = { driveTypeExpanded = false }
                ) {
                    driveTypes.forEach { driveType ->
                        DropdownMenuItem(
                            text = { Text(driveType) },
                            onClick = {
                                viewModel.updateDriveType(driveType)
                                driveTypeExpanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = state.engineModel,
                onValueChange = viewModel::updateEngineModel,
                label = { Text("Модель двигателя") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.engineVolume,
                onValueChange = viewModel::updateEngineVolume,
                label = { Text("Объем двигателя (л)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.purchaseMileage,
                onValueChange = viewModel::updatePurchaseMileage,
                label = { Text("Пробег при покупке (км)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Заметки") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Save button
            Button(
                onClick = viewModel::saveCar,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Сохранить")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
