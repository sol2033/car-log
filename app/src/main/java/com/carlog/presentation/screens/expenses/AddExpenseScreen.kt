package com.carlog.presentation.screens.expenses

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
import com.carlog.domain.model.ExpenseCategories
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    carId: Long,
    expenseId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val car by viewModel.car.collectAsState()
    val expense by viewModel.expense.collectAsState()
    val date by viewModel.date.collectAsState()
    val mileage by viewModel.mileage.collectAsState()
    val mileageError by viewModel.mileageError.collectAsState()
    val category by viewModel.category.collectAsState()
    val categoryError by viewModel.categoryError.collectAsState()
    val cost by viewModel.cost.collectAsState()
    val costError by viewModel.costError.collectAsState()
    val serviceName by viewModel.serviceName.collectAsState()
    val serviceAddress by viewModel.serviceAddress.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    
    val title = if (viewModel.isEditMode) "Редактировать расход" else "Добавить расход"
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onNavigateBack()
        }
    }
    
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
                    IconButton(onClick = { viewModel.saveExpense() }) {
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
                            text = "Дата *",
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
                onValueChange = { viewModel.updateMileage(it) },
                label = { Text(stringResource(R.string.mileage_km)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = mileageError != null,
                supportingText = mileageError?.let { { Text(it) } }
            )
            
            // Категория
            ExposedDropdownMenuBox(
                expanded = showCategoryMenu,
                onExpandedChange = { showCategoryMenu = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.expense_category)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    isError = categoryError != null,
                    supportingText = categoryError?.let { { Text(it) } }
                )
                
                ExposedDropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false }
                ) {
                    ExpenseCategories.ALL.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                viewModel.updateCategory(cat)
                                showCategoryMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ShoppingCart, null)
                            }
                        )
                    }
                }
            }
            
            // Стоимость
            OutlinedTextField(
                value = cost,
                onValueChange = { viewModel.updateCost(it) },
                label = { Text(stringResource(R.string.expense_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = costError != null,
                supportingText = costError?.let { { Text(it) } },
                trailingIcon = {
                    Text("₽", style = MaterialTheme.typography.bodyLarge)
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(
                text = "Информация о сервисе (опционально)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Название сервиса
            OutlinedTextField(
                value = serviceName,
                onValueChange = { viewModel.updateServiceName(it) },
                label = { Text(stringResource(R.string.service_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Store, null)
                }
            )
            
            // Адрес сервиса
            OutlinedTextField(
                value = serviceAddress,
                onValueChange = { viewModel.updateServiceAddress(it) },
                label = { Text(stringResource(R.string.service_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, null)
                }
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
                onClick = { viewModel.saveExpense() },
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
                            viewModel.updateDate(selectedDate)
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
