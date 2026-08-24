package com.carlog.presentation.screens.breakdowns

import androidx.compose.foundation.clickable
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
import com.carlog.domain.model.ConsumableCategories
import com.carlog.domain.model.ConsumableFormRules
import com.carlog.domain.model.MaintenanceType
import com.carlog.domain.model.WorkItem
import com.carlog.domain.model.totalCost
import com.carlog.presentation.components.EventPartDialog
import com.carlog.presentation.components.WorkItemDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBreakdownScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddBreakdownViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val consumableDefaults by viewModel.consumableDefaults.collectAsState()
    var showAddConsumableDialog by remember { mutableStateOf(false) }
    // Окно запчасти: null — закрыто, -1 — добавление новой, иначе индекс редактируемой
    var editedPartIndex by remember { mutableStateOf<Int?>(null) }
    var editedWorkIndex by remember { mutableStateOf<Int?>(null) }
    
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
                // Динамические заголовки в зависимости от типа обслуживания
                val infoLabel = when (state.maintenanceType) {
                    MaintenanceType.REPAIR -> "Информация о поломке *"
                    MaintenanceType.SCHEDULED_SERVICE -> "Информация о ТО *"
                    MaintenanceType.MODIFICATION -> "Информация о тюнинге *"
                    null -> "Информация о поломке *"
                }
                
                val titleLabel = when (state.maintenanceType) {
                    MaintenanceType.REPAIR -> "Название поломки *"
                    MaintenanceType.SCHEDULED_SERVICE -> "Название ТО *"
                    MaintenanceType.MODIFICATION -> "Название тюнинга *"
                    null -> "Название поломки *"
                }
                
                val dateLabel = when (state.maintenanceType) {
                    MaintenanceType.REPAIR -> "Дата поломки *"
                    MaintenanceType.SCHEDULED_SERVICE -> "Дата ТО *"
                    MaintenanceType.MODIFICATION -> "Дата тюнинга *"
                    null -> "Дата поломки *"
                }
                
                val mileageLabel = when (state.maintenanceType) {
                    MaintenanceType.REPAIR -> "Пробег при поломке (км) *"
                    MaintenanceType.SCHEDULED_SERVICE -> "Пробег на момент ТО (км) *"
                    MaintenanceType.MODIFICATION -> "Пробег при тюнинге (км) *"
                    null -> "Пробег при поломке (км) *"
                }
                
                val costLabel = when (state.maintenanceType) {
                    MaintenanceType.REPAIR -> "Стоимость ремонта *"
                    MaintenanceType.SCHEDULED_SERVICE -> "Стоимость ремонта *" // Не показывается для ТО
                    MaintenanceType.MODIFICATION -> "Стоимость тюнинга *"
                    null -> "Стоимость ремонта *"
                }
                
                Text(
                    text = infoLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Maintenance Type Selector
                Text(
                    text = stringResource(R.string.maintenance_type),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceType.values().forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.maintenanceType == type,
                                onClick = { viewModel.updateMaintenanceType(type) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = type.getDisplayName(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                
                state.maintenanceTypeError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text(titleLabel) },
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
                    label = { Text(dateLabel) },
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
                    label = { Text(mileageLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.breakdownMileageError != null,
                    supportingText = state.breakdownMileageError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Для ТО - только расходники, для Ремонтов и Модификаций - стоимость ремонта
                if (state.maintenanceType != MaintenanceType.SCHEDULED_SERVICE) {
                    Text(
                        text = costLabel,
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
                    // Кнопка открывает то же окно, что и создание отдельной запчасти:
                    // раньше здесь была строка из двух полей, и производителя с артикулом
                    // приходилось дозаполнять потом в самой карточке запчасти
                    Button(
                        onClick = { editedPartIndex = -1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.event_part_add_title))
                    }

                    Text(
                        text = stringResource(R.string.event_part_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.partsCostError != null) {
                        Text(
                            text = state.partsCostError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    // Список добавленных запчастей — тап открывает окно на редактирование
                    state.addedParts.forEachIndexed { index, part ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { editedPartIndex = index }
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
                                    val details = listOfNotNull(
                                        part.manufacturer.ifBlank { null },
                                        part.partNumber.ifBlank { null }
                                    ).joinToString(" · ")
                                    if (details.isNotBlank()) {
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "₽%.2f".format(part.price),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (part.photosPaths.isNotEmpty()) {
                                        Text(
                                            text = stringResource(
                                                R.string.event_part_photos_count,
                                                part.photosPaths.size
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
                } // Конец if (state.maintenanceType != MaintenanceType.SCHEDULED_SERVICE)
                
                // Секция расходников для ТО
                if (state.maintenanceType == MaintenanceType.SCHEDULED_SERVICE) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = stringResource(R.string.linked_consumables),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Кнопка "Добавить расходник"
                    Button(
                        onClick = { showAddConsumableDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isEditMode // Только при создании нового ТО
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_consumable))
                    }
                    
                    if (state.consumablesError != null) {
                        Text(
                            text = state.consumablesError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    
                    // Список добавленных расходников
                    state.temporaryConsumables.forEachIndexed { index, consumable ->
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
                                        // У разовой позиции «Другое» имя задаёт пользователь
                                        text = consumable.customName ?: consumable.category,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    consumable.manufacturer?.let { manufacturer ->
                                        Text(
                                            text = manufacturer,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    consumable.cost?.let { cost ->
                                        Text(
                                            text = "₽%.2f".format(cost),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                // Кнопка удаления (всегда доступна)
                                IconButton(onClick = { viewModel.removeTemporaryConsumable(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    
                    // Показываем рассчитанную стоимость запчастей
                    if (state.temporaryConsumables.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.calculated_parts_cost),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "₽%.2f".format(state.calculatedPartsCost),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // Галочка "В сервисе"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.isServiceMaintenance,
                        onCheckedChange = viewModel::toggleServiceMaintenance
                    )
                    Text(
                        text = stringResource(R.string.service_maintenance),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // Стоимость услуг сервиса - показывается только при галочке "В сервисе"
                if (state.isServiceMaintenance) {
                    if (state.isWarrantyRepair) {
                        // Гарантийный ремонт: работ к оплате нет, расписывать нечего
                        OutlinedTextField(
                            value = "ремонт по гарантии",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.service_cost_label)) },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.useGeneralServiceCost,
                                onClick = { viewModel.toggleUseGeneralServiceCost(true) }
                            )
                            Text(
                                text = stringResource(R.string.general_service_cost),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !state.useGeneralServiceCost,
                                onClick = { viewModel.toggleUseGeneralServiceCost(false) }
                            )
                            Text(
                                text = stringResource(R.string.specific_works_list),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (state.useGeneralServiceCost) {
                            OutlinedTextField(
                                value = state.serviceCost,
                                onValueChange = viewModel::updateServiceCost,
                                label = { Text(stringResource(R.string.service_cost_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Button(
                                onClick = { editedWorkIndex = -1 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.work_item_add_title))
                            }

                            Text(
                                text = stringResource(R.string.work_item_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            state.workItems.forEachIndexed { index, work ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { editedWorkIndex = index }
                                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = work.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (!work.notes.isNullOrBlank()) {
                                                Text(
                                                    text = work.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = "₽%.2f".format(work.cost),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(onClick = { viewModel.removeWorkItem(index) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.workItems.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.work_items_total),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "₽%.2f".format(state.workItems.totalCost()),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Галочка гарантийного ремонта - только для REPAIR
                if (state.maintenanceType == MaintenanceType.REPAIR) {
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
                }
                
                // Название и адрес сервиса - только при галочке "В сервисе"
                if (state.isServiceMaintenance) {
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
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                val partsCostValue = if (state.useGeneralPartsCost) {
                    state.partsCost.toDoubleOrNull() ?: 0.0
                } else {
                    state.addedParts.sumOf { it.price }
                }
                val serviceCostValue = state.calculatedServiceCost ?: 0.0
                val totalCost = partsCostValue + serviceCostValue
                
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
                            if (serviceCostValue > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.services_label),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "₽%.2f".format(serviceCostValue),
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
    
    // Dialog для добавления расходника
    editedPartIndex?.let { index ->
        EventPartDialog(
            initial = state.addedParts.getOrNull(index),
            onDismiss = { editedPartIndex = null },
            onConfirm = { part ->
                if (index >= 0) viewModel.updatePart(index, part) else viewModel.addPart(part)
                editedPartIndex = null
            },
            onPhotoDiscarded = viewModel::onPartPhotoDiscarded,
            showVisibilityToggle = true
        )
    }

    editedWorkIndex?.let { index ->
        WorkItemDialog(
            initial = state.workItems.getOrNull(index),
            onDismiss = { editedWorkIndex = null },
            onConfirm = { work ->
                if (index >= 0) viewModel.updateWorkItem(index, work) else viewModel.addWorkItem(work)
                editedWorkIndex = null
            }
        )
    }

    if (showAddConsumableDialog) {
        AddConsumableDialog(
            availableCategories = availableCategories,
            defaults = consumableDefaults,
            onCategorySelected = viewModel::loadConsumableDefaults,
            onDismiss = { showAddConsumableDialog = false },
            onAdd = { consumable ->
                val success = viewModel.addTemporaryConsumable(consumable)
                if (success) {
                    showAddConsumableDialog = false
                }
            },
            errorMessage = state.consumablesError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConsumableDialog(
    availableCategories: List<String>,
    defaults: ConsumableDefaults?,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (TemporaryConsumable) -> Unit,
    errorMessage: String?
) {
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var manufacturer by remember { mutableStateOf("") }
    var articleNumber by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var replacementIntervalMileage by remember { mutableStateOf("") }
    var replacementIntervalDays by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Интервалы и объём подставляются по категории — как в отдельной форме расходника,
    // где они берутся из настроек пользователя или из дефолтов категории
    LaunchedEffect(defaults) {
        val prefill = defaults ?: return@LaunchedEffect
        if (prefill.category != category) return@LaunchedEffect
        replacementIntervalMileage = prefill.intervalMileage?.toString() ?: ""
        replacementIntervalDays = prefill.intervalDays?.toString() ?: ""
        volume = prefill.volume?.toString() ?: ""
    }

    val needsCustomName = ConsumableFormRules.requiresCustomName(category)
    val needsVolume = ConsumableFormRules.requiresVolume(category)
    val hasReminders = ConsumableFormRules.supportsReminders(category)
    val canAdd = ConsumableFormRules.canAdd(
        category = category,
        customName = customName,
        cost = cost,
        volume = volume,
        intervalMileage = replacementIntervalMileage,
        intervalDays = replacementIntervalDays
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.to_consumable_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Dropdown для категории
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.to_consumable_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        isError = errorMessage != null && category.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        // «Другое» идёт последним: это не категория, а разовая позиция
                        (availableCategories + ConsumableCategories.OTHER).forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                    onCategorySelected(cat)
                                }
                            )
                        }
                    }
                }

                if (needsCustomName) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(stringResource(R.string.to_consumable_custom_name)) },
                        singleLine = true,
                        isError = errorMessage != null && customName.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.to_consumable_other_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text(stringResource(R.string.to_consumable_manufacturer)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = articleNumber,
                    onValueChange = { articleNumber = it },
                    label = { Text(stringResource(R.string.to_consumable_article)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text(stringResource(R.string.to_consumable_cost)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = cost.isNotBlank() && cost.toDoubleOrNull() == null
                )

                // Объём — только у жидкостей: у фильтра или колодок литров не бывает
                if (needsVolume) {
                    OutlinedTextField(
                        value = volume,
                        onValueChange = { volume = it },
                        label = { Text(stringResource(R.string.to_consumable_volume)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = volume.isNotBlank() && volume.toDoubleOrNull() == null
                    )
                }

                // У разовой позиции «Другое» замен по расписанию не бывает
                if (hasReminders) {
                    OutlinedTextField(
                        value = replacementIntervalMileage,
                        onValueChange = { replacementIntervalMileage = it },
                        label = { Text(stringResource(R.string.to_consumable_interval_mileage)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replacementIntervalDays,
                        onValueChange = { replacementIntervalDays = it },
                        label = { Text(stringResource(R.string.to_consumable_interval_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.to_consumable_interval_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.to_consumable_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canAdd) {
                        onAdd(
                            TemporaryConsumable(
                                category = category,
                                customName = customName.trim().ifBlank { null },
                                manufacturer = manufacturer.ifBlank { null },
                                articleNumber = articleNumber.ifBlank { null },
                                cost = cost.toDoubleOrNull(),
                                isInstalledAtService = false,
                                serviceCost = null,
                                volume = if (needsVolume) volume.toDoubleOrNull() else null,
                                replacementIntervalMileage =
                                    if (hasReminders) replacementIntervalMileage.toIntOrNull() else null,
                                replacementIntervalDays =
                                    if (hasReminders) replacementIntervalDays.toIntOrNull() else null,
                                notes = notes.ifBlank { null }
                            )
                        )
                    }
                },
                enabled = canAdd
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
