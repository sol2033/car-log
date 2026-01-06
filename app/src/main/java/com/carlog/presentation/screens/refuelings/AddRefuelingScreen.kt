package com.carlog.presentation.screens.refuelings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.*

// Функция для локализации типов топлива
@Composable
fun getFuelTypeLocalized(fuelType: String?): String {
    return when (fuelType?.lowercase()) {
        "бензин" -> stringResource(R.string.fuel_type_benzin)
        "дизель" -> stringResource(R.string.fuel_type_dizel)
        "электро" -> stringResource(R.string.fuel_type_electro)
        "газ" -> stringResource(R.string.fuel_type_gaz)
        "пропан", "lpg" -> stringResource(R.string.fuel_type_lpg)
        "метан", "cng" -> stringResource(R.string.fuel_type_cng)
        else -> fuelType ?: ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRefuelingScreen(
    carId: Long,
    refuelingId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddRefuelingViewModel = hiltViewModel()
) {
    LaunchedEffect(carId, refuelingId) {
        viewModel.setCarId(carId, refuelingId)
    }
    
    val car by viewModel.car.collectAsState()
    val date by viewModel.date
    val mileage by viewModel.mileage
    val mileageError by viewModel.mileageError
    val liters by viewModel.liters
    val litersError by viewModel.litersError
    val fuelType by viewModel.fuelType
    val pricePerLiter by viewModel.pricePerLiter
    val pricePerLiterError by viewModel.pricePerLiterError
    val totalCost by viewModel.totalCost
    val totalCostError by viewModel.totalCostError
    val isFullTank by viewModel.isFullTank
    val stationName by viewModel.stationName
    val notes by viewModel.notes
    val availableFuelTypes by viewModel.availableFuelTypes.collectAsState()
    
    val isElectric = car?.fuelType == "Электро"
    val title = if (refuelingId == null) {
        if (isElectric) "Добавить зарядку" else "Добавить заправку"
    } else {
        if (isElectric) "Редактировать зарядку" else "Редактировать заправку"
    }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showFuelTypeMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveRefueling {
                                onNavigateBack()
                            }
                        }
                    ) {
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
            // Дата
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDatePicker = true }
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
                            text = "Дата",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(Date(date)),
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
            
            // Пробег
            OutlinedTextField(
                value = mileage,
                onValueChange = viewModel::onMileageChanged,
                label = { Text(stringResource(R.string.mileage_km)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = mileageError != null,
                supportingText = mileageError?.let { { Text(it) } }
            )
            
            // Количество литров/кВт·ч
            OutlinedTextField(
                value = liters,
                onValueChange = viewModel::onLitersChanged,
                label = { Text(if (isElectric) "Количество (кВт·ч) *" else "Количество (л) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = litersError != null,
                supportingText = litersError?.let { { Text(it) } }
            )
            
            // Тип топлива
            ExposedDropdownMenuBox(
                expanded = showFuelTypeMenu,
                onExpandedChange = { showFuelTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = fuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fuel_type_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFuelTypeMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    supportingText = {
                        val currentCar = car
                        if (currentCar?.hasGasEquipment == true && !isElectric) {
                            val localizedFuelType = getFuelTypeLocalized(currentCar.fuelType)
                            val localizedGasType = getFuelTypeLocalized(currentCar.gasType)
                            Text(stringResource(R.string.select_fuel_type, localizedFuelType, localizedGasType))
                        }
                    }
                )
                
                ExposedDropdownMenu(
                    expanded = showFuelTypeMenu,
                    onDismissRequest = { showFuelTypeMenu = false }
                ) {
                    val currentCar = car
                    val hasGas = currentCar?.hasGasEquipment == true && currentCar.gasType != null
                    val gasType = currentCar?.gasType
                    
                    availableFuelTypes.forEachIndexed { _, type ->
                        // Добавляем разделитель перед газом
                        if (hasGas && type == gasType) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(
                                            text = "Газ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.onFuelTypeChanged(type)
                                showFuelTypeMenu = false
                            },
                            leadingIcon = if (type == gasType) {
                                { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
            
            // Заголовок для стоимости
            Text(
                text = "Стоимость (укажите один из вариантов) *",
                style = MaterialTheme.typography.titleSmall,
                color = if (pricePerLiterError != null || totalCostError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            // Цена за литр/кВт·ч
            OutlinedTextField(
                value = pricePerLiter,
                onValueChange = viewModel::onPricePerLiterChanged,
                label = { Text(if (isElectric) "Цена за кВт·ч (₽)" else "Цена за литр (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = pricePerLiterError != null,
                supportingText = {
                    pricePerLiterError?.let { 
                        Text(it) 
                    } ?: if (pricePerLiter.isNotEmpty()) {
                        Text(stringResource(R.string.auto_calculated_total))
                    } else {
                        Text("")
                    }
                }
            )
            
            // Общая стоимость
            OutlinedTextField(
                value = totalCost,
                onValueChange = viewModel::onTotalCostChanged,
                label = { Text(stringResource(R.string.total_cost_rub)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = pricePerLiter.isEmpty(),
                isError = totalCostError != null,
                supportingText = {
                    totalCostError?.let {
                        Text(it)
                    } ?: if (pricePerLiter.isEmpty()) {
                        Text(stringResource(R.string.fill_if_no_price_per_liter))
                    } else {
                        Text(stringResource(R.string.disabled_using_price_per_liter))
                    }
                }
            )
            
            // Полный бак (только для не-электро)
            if (!isElectric) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Полный бак",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isFullTank,
                        onCheckedChange = viewModel::onIsFullTankChanged
                    )
                }
                
                if (isFullTank) {
                    Text(
                        text = "Будет рассчитан расход топлива для этой заправки (расстояние от предыдущей заправки)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            // Название заправки
            OutlinedTextField(
                value = stationName,
                onValueChange = viewModel::onStationNameChanged,
                label = { Text(if (isElectric) "Название зарядной станции" else "Название заправки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Опционально") }
            )
            
            // Заметки
            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.notes)) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.optional)) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Кнопка Сохранить
            Button(
                onClick = {
                    viewModel.saveRefueling {
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.save))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            viewModel.onDateChanged(selectedDate)
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
}
