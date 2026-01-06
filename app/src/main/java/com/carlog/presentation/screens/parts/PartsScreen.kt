package com.carlog.presentation.screens.parts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.domain.model.Part
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddPart: (Long) -> Unit,
    onNavigateToPartDetail: (Long, Long) -> Unit,
    viewModel: PartsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parts)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAddPart(carId) }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_part))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            FilterChips(
                currentFilter = uiState.filter,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Sort chips
            SortChips(
                currentSortOrder = uiState.sortOrder,
                currentSortDirection = uiState.sortDirection,
                onSortOrderSelected = viewModel::setSortOrder,
                onToggleSortDirection = viewModel::toggleSortDirection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            )
            
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.error_loading),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.parts.isEmpty() -> {
                    EmptyPartsState(
                        filter = uiState.filter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.parts,
                            key = { it.id }
                        ) { part ->
                            PartCard(
                                part = part,
                                currentCarMileage = uiState.currentCarMileage,
                                onPartClick = { onNavigateToPartDetail(carId, part.id) },
                                onDeleteClick = { viewModel.deletePart(part) },
                                onMarkAsBroken = { date, mileage ->
                                    viewModel.markPartAsBroken(part, date, mileage)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(
    currentSortOrder: PartSortOrder,
    currentSortDirection: SortDirection,
    onSortOrderSelected: (PartSortOrder) -> Unit,
    onToggleSortDirection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = currentSortOrder == PartSortOrder.ByInstallDate,
            onClick = { onSortOrderSelected(PartSortOrder.ByInstallDate) },
            label = { Text(stringResource(R.string.sort_by_date)) },
            leadingIcon = if (currentSortOrder == PartSortOrder.ByInstallDate) {
                { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) }
            } else null
        )
        FilterChip(
            selected = currentSortOrder == PartSortOrder.ByPrice,
            onClick = { onSortOrderSelected(PartSortOrder.ByPrice) },
            label = { Text(stringResource(R.string.sort_by_price)) },
            leadingIcon = if (currentSortOrder == PartSortOrder.ByPrice) {
                { Icon(Icons.Default.Info, null, Modifier.size(18.dp)) }
            } else null
        )
        FilterChip(
            selected = currentSortOrder == PartSortOrder.ByMileage,
            onClick = { onSortOrderSelected(PartSortOrder.ByMileage) },
            label = { Text(stringResource(R.string.sort_by_mileage)) },
            leadingIcon = if (currentSortOrder == PartSortOrder.ByMileage) {
                { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) }
            } else null
        )
        
        // Кнопка переключения направления сортировки
        IconButton(onClick = onToggleSortDirection) {
            Icon(
                imageVector = if (currentSortDirection == SortDirection.DESCENDING) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.Default.KeyboardArrowUp
                },
                contentDescription = if (currentSortDirection == SortDirection.DESCENDING) {
                    "По убыванию"
                } else {
                    "По возрастанию"
                },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(
    currentFilter: PartFilter,
    onFilterSelected: (PartFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentFilter == PartFilter.All,
            onClick = { onFilterSelected(PartFilter.All) },
            label = { Text(stringResource(R.string.filter_all)) }
        )
        FilterChip(
            selected = currentFilter == PartFilter.Active,
            onClick = { onFilterSelected(PartFilter.Active) },
            label = { Text(stringResource(R.string.filter_active)) }
        )
        FilterChip(
            selected = currentFilter == PartFilter.Broken,
            onClick = { onFilterSelected(PartFilter.Broken) },
            label = { Text(stringResource(R.string.filter_broken)) }
        )
        FilterChip(
            selected = currentFilter == PartFilter.OnlyAccident,
            onClick = { onFilterSelected(PartFilter.OnlyAccident) },
            label = { Text(stringResource(R.string.filter_accident_only)) }
        )
        FilterChip(
            selected = currentFilter == PartFilter.WithoutAccident,
            onClick = { onFilterSelected(PartFilter.WithoutAccident) },
            label = { Text(stringResource(R.string.filter_no_accident)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartCard(
    part: Part,
    currentCarMileage: Int,
    onPartClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMarkAsBroken: (Long, Int) -> Unit = { _, _ -> }
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMarkBrokenDialog by remember { mutableStateOf(false) }
    
    ElevatedCard(
        onClick = onPartClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (part.manufacturer != null) {
                        Text(
                            text = part.manufacturer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (part.isBroken) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.part_status_broken_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.BuildCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.part_status_active_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                
                // Метка "Установлена из-за ДТП"
                if (part.installationType == "ДТП") {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.part_accident_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Дата установки и пробег
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(part.installDate),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.mileage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${part.installMileage} ${stringResource(R.string.km)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Дата поломки для сломанных запчастей
            if (part.isBroken && part.breakdownDate != null && part.breakdownMileage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.broken_on),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = formatDate(part.breakdownDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.mileage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${part.breakdownMileage} ${stringResource(R.string.km)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Пробег запчасти
            if (part.isBroken && part.mileageDriven != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.part_mileage_driven, part.mileageDriven),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            } else if (!part.isBroken && currentCarMileage > part.installMileage) {
                Spacer(modifier = Modifier.height(4.dp))
                val currentMileageDriven = currentCarMileage - part.installMileage
                Text(
                    text = stringResource(R.string.part_mileage_driven, currentMileageDriven),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Информация о сервисе
            if (!part.serviceName.isNullOrBlank() || !part.serviceAddress.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.service),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        if (!part.serviceName.isNullOrBlank()) {
                            Text(
                                text = part.serviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (!part.serviceAddress.isNullOrBlank()) {
                            Text(
                                text = part.serviceAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Отображение фотографий
            if (!part.photosPaths.isNullOrEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.photos),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(part.photosPaths) { photoPath ->
                                Card(
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    AsyncImage(
                                        model = photoPath,
                                        contentDescription = stringResource(R.string.photo_part),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCost = part.price + (part.servicePrice ?: 0.0)
                Text(
                    text = "₽%.2f".format(totalCost),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    if (!part.isBroken) {
                        IconButton(onClick = { showMarkBrokenDialog = true }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = stringResource(R.string.mark_broken_dialog_title),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_part_dialog_title)) },
            text = { Text(stringResource(R.string.delete_part_dialog_message, part.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    if (showMarkBrokenDialog) {
        var breakdownDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var breakdownMileage by remember { mutableStateOf("") }
        var showDatePicker by remember { mutableStateOf(false) }
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        
        AlertDialog(
            onDismissRequest = { showMarkBrokenDialog = false },
            title = { Text(stringResource(R.string.mark_broken_dialog_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.mark_broken_dialog_message, part.name))
                    
                    OutlinedTextField(
                        value = dateFormat.format(Date(breakdownDateMillis)),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.breakdown_date_label)) },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.select_date))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = breakdownMileage,
                        onValueChange = { breakdownMileage = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.breakdown_mileage_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mileage = breakdownMileage.toIntOrNull()
                        if (mileage != null && mileage > 0) {
                            onMarkAsBroken(breakdownDateMillis, mileage)
                            showMarkBrokenDialog = false
                        }
                    },
                    enabled = breakdownMileage.toIntOrNull()?.let { it > 0 } == true
                ) {
                    Text(stringResource(R.string.mark))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkBrokenDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = breakdownDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                breakdownDateMillis = it
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
}

@Composable
private fun EmptyPartsState(
    filter: PartFilter,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            val text = when (filter) {
                PartFilter.All -> stringResource(R.string.no_parts_yet)
                PartFilter.Active -> stringResource(R.string.no_active_parts)
                PartFilter.Broken -> stringResource(R.string.no_broken_parts)
                PartFilter.OnlyAccident -> stringResource(R.string.no_accident_parts)
                PartFilter.WithoutAccident -> stringResource(R.string.no_non_accident_parts)
            }
            
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (filter == PartFilter.All) {
                Text(
                    text = stringResource(R.string.add_first_part),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
