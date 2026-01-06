package com.carlog.presentation.screens.consumables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.ConsumableCategories
import com.carlog.util.ConsumableStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConsumableScreen(
    carId: Long,
    category: String?,
    consumableId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AddConsumableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (consumableId != null) "Редактировать расходник" else "Добавить расходник") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveConsumable() },
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
                contentAlignment = Alignment.Center
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
                    text = "Основная информация",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Category Dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        isError = state.categoryError != null,
                        supportingText = state.categoryError?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    viewModel.updateCategory(cat)
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = state.manufacturer,
                    onValueChange = viewModel::updateManufacturer,
                    label = { Text(stringResource(R.string.manufacturer)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.articleNumber,
                    onValueChange = viewModel::updateArticleNumber,
                    label = { Text(stringResource(R.string.article_number)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.installationMileage,
                    onValueChange = viewModel::updateInstallationMileage,
                    label = { Text(stringResource(R.string.installation_mileage)) },
                    isError = state.installationMileageError != null,
                    supportingText = state.installationMileageError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.cost,
                    onValueChange = viewModel::updateCost,
                    label = { Text(stringResource(R.string.cost_rub)) },
                    isError = state.costError != null,
                    supportingText = state.costError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.service_installed))
                    Switch(
                        checked = state.isInstalledAtService,
                        onCheckedChange = viewModel::updateIsInstalledAtService
                    )
                }
                
                if (state.isInstalledAtService) {
                    OutlinedTextField(
                        value = state.serviceCost,
                        onValueChange = viewModel::updateServiceCost,
                        label = { Text(stringResource(R.string.service_cost_consumable)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (ConsumableCategories.FLUID_CATEGORIES.contains(state.category)) {
                    OutlinedTextField(
                        value = state.volume,
                        onValueChange = viewModel::updateVolume,
                        label = { Text(stringResource(R.string.volume_liters)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                HorizontalDivider()
                
                Text(
                    text = "Интервалы замены",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = state.replacementIntervalMileage,
                    onValueChange = viewModel::updateReplacementIntervalMileage,
                    label = { Text(stringResource(R.string.mileage_interval_km)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.replacementIntervalDays,
                    onValueChange = viewModel::updateReplacementIntervalDays,
                    label = { Text(stringResource(R.string.days_interval_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = viewModel::saveConsumable,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.save))
                    }
                }
                
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumableDetailScreen(
    carId: Long,
    consumableId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long, Long) -> Unit,
    onNavigateToAddConsumable: (Long, String?) -> Unit,
    viewModel: ConsumableDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showReplaceDialog by viewModel.showReplaceDialog.collectAsState()
    val showReplaceSameDialog by viewModel.showReplaceSameDialog.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.consumable_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState is ConsumableDetailUiState.Success) {
                        IconButton(onClick = { onNavigateToEdit(carId, consumableId) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit))
                        }
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ConsumableDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ConsumableDetailUiState.Success -> {
                ConsumableDetailContent(
                    consumable = state.consumable,
                    statusInfo = state.statusInfo,
                    currentMileage = state.currentMileage,
                    onReplaceClick = { viewModel.showReplaceDialog() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            is ConsumableDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    
    // Delete Dialog
    if (showDeleteDialog && uiState is ConsumableDetailUiState.Success) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_consumable_title)) },
            text = { Text(stringResource(R.string.delete_consumable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConsumable { onNavigateBack() }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Replace Dialog - выбор варианта
    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReplaceDialog() },
            title = { Text(stringResource(R.string.replace_consumable_title)) },
            text = { Text(stringResource(R.string.replace_consumable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissReplaceDialog()
                    viewModel.showReplaceSameDialog()
                }) {
                    Text(stringResource(R.string.same_consumable))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        viewModel.dismissReplaceDialog()
                        val state = uiState
                        if (state is ConsumableDetailUiState.Success) {
                            viewModel.replaceWithDifferentConsumable {
                                onNavigateToAddConsumable(carId, state.consumable.category)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.different_consumable))
                    }
                    TextButton(onClick = { viewModel.dismissReplaceDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Replace same consumable dialog - ввод параметров
    if (showReplaceSameDialog && uiState is ConsumableDetailUiState.Success) {
        val currentState = uiState as ConsumableDetailUiState.Success
        var cost by remember { mutableStateOf(currentState.consumable.cost?.toString() ?: "") }
        var isService by remember { mutableStateOf(currentState.consumable.isInstalledAtService) }
        var serviceCost by remember { mutableStateOf(currentState.consumable.serviceCost?.toString() ?: "") }
        
        AlertDialog(
            onDismissRequest = { viewModel.dismissReplaceSameDialog() },
            title = { Text(stringResource(R.string.replace_same_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = { Text(stringResource(R.string.consumable_cost_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.service_installed))
                        Switch(
                            checked = isService,
                            onCheckedChange = { isService = it }
                        )
                    }
                    
                    if (isService) {
                        OutlinedTextField(
                            value = serviceCost,
                            onValueChange = { serviceCost = it },
                            label = { Text(stringResource(R.string.service_cost_consumable)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.replaceSameConsumable(
                        newCost = cost.toDoubleOrNull(),
                        isInstalledAtService = isService,
                        serviceCost = if (isService) serviceCost.toDoubleOrNull() else null,
                        onReplaced = { onNavigateBack() }
                    )
                    viewModel.dismissReplaceSameDialog()
                }) {
                    Text(stringResource(R.string.replace_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReplaceSameDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ConsumableDetailContent(
    consumable: Consumable,
    statusInfo: ConsumableStatus.StatusInfo,
    currentMileage: Int,
    onReplaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (consumable.isActive) {
            Button(
                onClick = onReplaceClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (statusInfo.status) {
                        ConsumableStatus.Status.CRITICAL -> MaterialTheme.colorScheme.error
                        ConsumableStatus.Status.WARNING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    when (statusInfo.status) {
                        ConsumableStatus.Status.CRITICAL -> "Заменить срочно!"
                        ConsumableStatus.Status.WARNING -> "Заменить расходник"
                        else -> "Заменить расходник"
                    }
                )
            }
        }
        
        // Status Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (consumable.isActive) "Активен" else "Заменен",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (consumable.isActive) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (consumable.isActive && statusInfo.remainingMileage != null) {
                    Text(
                        text = "Осталось: ${statusInfo.remainingMileage} км",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (consumable.isActive && statusInfo.remainingDays != null) {
                    Text(
                        text = "Осталось: ${statusInfo.remainingDays} дн",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Main Info
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Основная информация",
                    style = MaterialTheme.typography.titleMedium
                )
                DetailRow("Категория", consumable.category)
                consumable.manufacturer?.let { DetailRow("Производитель", it) }
                consumable.articleNumber?.let { DetailRow("Артикул", it) }
                consumable.cost?.let { DetailRow("Стоимость", "$it ₽") }
                DetailRow("Установлен в сервисе", if (consumable.isInstalledAtService) "Да" else "Нет")
                consumable.volume?.let { DetailRow("Объем", "$it л") }
            }
        }
        
        // Mileage Info
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Пробег",
                    style = MaterialTheme.typography.titleMedium
                )
                DetailRow("Установлен", "${consumable.installationMileage} км")
                consumable.replacementMileage?.let { DetailRow("Заменен", "$it км") }
                if (consumable.isActive) {
                    val used = currentMileage - consumable.installationMileage
                    DetailRow("Использовано", "$used км")
                }
            }
        }
        
        // Intervals
        if (consumable.replacementIntervalMileage != null || consumable.replacementIntervalDays != null) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Интервалы замены",
                        style = MaterialTheme.typography.titleMedium
                    )
                    consumable.replacementIntervalMileage?.let { 
                        DetailRow("По пробегу", "$it км") 
                    }
                    consumable.replacementIntervalDays?.let { 
                        DetailRow("По времени", "$it дн") 
                    }
                }
            }
        }
        
        // Notes
        consumable.notes?.let { notes ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Заметки",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = notes)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesHistoryScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryHistory: (Long, String) -> Unit,
    viewModel: ConsumablesHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.consumable_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет расходников для отображения истории",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Выберите категорию для просмотра истории",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(state.categories) { category ->
                    OutlinedCard(
                        onClick = { onNavigateToCategoryHistory(carId, category) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesSettingsScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ConsumableSettingsViewModel = hiltViewModel()
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
                title = { Text(stringResource(R.string.consumable_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                contentAlignment = Alignment.Center
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
                    text = "Дополнительные категории",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Выберите дополнительные категории расходников:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                ConsumableCategories.ADDITIONAL_CATEGORIES.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Checkbox(
                            checked = state.selectedCategories.contains(category),
                            onCheckedChange = { viewModel.toggleCategory(category) }
                        )
                    }
                }
                
                HorizontalDivider()
                
                Text(
                    text = "Интервалы замены и объемы",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Настройте интервалы по умолчанию для каждой категории:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                val allCategories = ConsumableCategories.STANDARD_CATEGORIES + state.selectedCategories
                
                allCategories.forEach { category ->
                    var expanded by remember { mutableStateOf(false) }
                    
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expanded = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            
                            if (expanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val (mileage, days) = viewModel.getInterval(category)
                                
                                OutlinedTextField(
                                    value = mileage,
                                    onValueChange = { viewModel.updateIntervalMileage(category, it) },
                                    label = { Text(stringResource(R.string.mileage_interval_km)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = days,
                                    onValueChange = { viewModel.updateIntervalDays(category, it) },
                                    label = { Text(stringResource(R.string.days_interval_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                if (ConsumableCategories.FLUID_CATEGORIES.contains(category)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedTextField(
                                        value = viewModel.getVolume(category),
                                        onValueChange = { viewModel.updateVolume(category, it) },
                                        label = { Text(stringResource(R.string.default_volume_label)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = { viewModel.saveSettings() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.save_settings))
                    }
                }
                
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
}
