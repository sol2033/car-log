package com.carlog.presentation.screens.car

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCarScreen(
    carId: Long? = null,
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
                title = { 
                    Text(
                        if (state.carId != null) "Редактировать автомобиль" 
                        else "Добавить автомобиль"
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveCar() },
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Check, "Сохранить")
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
            // Required fields section
            Text(
                text = "Основная информация *",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::updateBrand,
                label = { Text(stringResource(R.string.car_brand_required)) },
                isError = state.brandError != null,
                supportingText = state.brandError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::updateModel,
                label = { Text(stringResource(R.string.car_model_required)) },
                isError = state.modelError != null,
                supportingText = state.modelError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.currentMileage,
                onValueChange = viewModel::updateCurrentMileage,
                label = { Text(stringResource(R.string.current_mileage_required)) },
                isError = state.mileageError != null,
                supportingText = state.mileageError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Fuel Type Dropdown
            var fuelTypeExpanded by remember { mutableStateOf(false) }
            val fuelTypes = listOf(
                "Бензин",
                "Дизель",
                "Электро"
            )
            
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded }
            ) {
                OutlinedTextField(
                    value = state.fuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fuel_type_required)) },
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
            
            // Gas Equipment (ГБО)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Установлено ГБО (газовое оборудование)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Switch(
                    checked = state.hasGasEquipment,
                    onCheckedChange = { viewModel.updateHasGasEquipment(it) }
                )
            }
            
            // Gas Type (показывается только если ГБО установлено)
            if (state.hasGasEquipment) {
                var gasTypeExpanded by remember { mutableStateOf(false) }
                val gasTypes = listOf("Метан (CNG)", "Пропан-бутан")
                
                ExposedDropdownMenuBox(
                    expanded = gasTypeExpanded,
                    onExpandedChange = { gasTypeExpanded = !gasTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = state.gasType ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.gas_type_required)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gasTypeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = gasTypeExpanded,
                        onDismissRequest = { gasTypeExpanded = false }
                    ) {
                        gasTypes.forEach { gasType ->
                            DropdownMenuItem(
                                text = { Text(gasType) },
                                onClick = {
                                    viewModel.updateGasType(gasType)
                                    gasTypeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Optional fields section
            Text(
                text = "Дополнительная информация",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedTextField(
                value = state.year,
                onValueChange = viewModel::updateYear,
                label = { Text(stringResource(R.string.car_year)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.color,
                onValueChange = viewModel::updateColor,
                label = { Text(stringResource(R.string.car_color)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.licensePlate,
                onValueChange = viewModel::updateLicensePlate,
                label = { Text(stringResource(R.string.license_plate)) },
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
                    label = { Text(stringResource(R.string.transmission_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transmissionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = { Text(stringResource(R.string.not_selected)) }
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
                    label = { Text(stringResource(R.string.drive_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driveTypeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = { Text(stringResource(R.string.not_selected)) }
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
            
            // Body Type Dropdown
            var bodyTypeExpanded by remember { mutableStateOf(false) }
            val bodyTypes = listOf(
                "Седан",
                "Хэтчбек",
                "Универсал",
                "Купе",
                "Кабриолет",
                "Родстер",
                "Тарга",
                "Лимузин",
                "Внедорожник",
                "Кроссовер",
                "Пикап",
                "Фургон",
                "Минивэн",
                "Микроавтобус",
                "Компактвэн"
            )
            
            ExposedDropdownMenuBox(
                expanded = bodyTypeExpanded,
                onExpandedChange = { bodyTypeExpanded = !bodyTypeExpanded }
            ) {
                OutlinedTextField(
                    value = state.bodyType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.body_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bodyTypeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = { Text(stringResource(R.string.not_selected)) }
                )
                
                ExposedDropdownMenu(
                    expanded = bodyTypeExpanded,
                    onDismissRequest = { bodyTypeExpanded = false }
                ) {
                    bodyTypes.forEach { bodyType ->
                        DropdownMenuItem(
                            text = { Text(bodyType) },
                            onClick = {
                                viewModel.updateBodyType(bodyType)
                                bodyTypeExpanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = state.engineModel,
                onValueChange = viewModel::updateEngineModel,
                label = { Text(stringResource(R.string.engine_model_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = state.engineVolume,
                onValueChange = viewModel::updateEngineVolume,
                label = { Text(stringResource(R.string.engine_volume_label)) },
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
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                text = "Фотографии автомобиля",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            val photoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.toString()?.let { photoPath ->
                    viewModel.addPhoto(photoPath)
                }
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .size(120.dp)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить фото",
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Добавить",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                items(state.photosPaths) { photoPath ->
                    Box(
                        modifier = Modifier.size(120.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { viewModel.setMainPhoto(photoPath) }
                                .then(
                                    if (photoPath == state.mainPhotoPath) {
                                        Modifier.border(
                                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                    } else Modifier
                                )
                        ) {
                            Box {
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = "Фото автомобиля",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Индикатор основного фото
                                if (photoPath == state.mainPhotoPath) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Основное фото",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(24.dp)
                                    )
                                }
                            }
                        }
                        
                        // Кнопка удаления
                        IconButton(
                            onClick = { viewModel.removePhoto(photoPath) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить фото",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            if (state.photosPaths.isNotEmpty()) {
                Text(
                    text = "Нажмите на фото, чтобы сделать его основным",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
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
                    Text(
                        if (state.carId != null) "Сохранить изменения" 
                        else "Сохранить"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
