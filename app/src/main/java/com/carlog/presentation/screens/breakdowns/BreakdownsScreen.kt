package com.carlog.presentation.screens.breakdowns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.MaintenanceType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownsScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddBreakdown: (Long) -> Unit,
    onNavigateToBreakdownDetail: (Long, Long) -> Unit,
    viewModel: BreakdownsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.breakdowns)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAddBreakdown(carId) }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_breakdown))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs for filtering
            ScrollableTabRow(
                selectedTabIndex = when (uiState.selectedMaintenanceType) {
                    null -> 0
                    MaintenanceType.REPAIR -> 1
                    MaintenanceType.SCHEDULED_SERVICE -> 2
                    MaintenanceType.MODIFICATION -> 3
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.selectedMaintenanceType == null,
                    onClick = { viewModel.selectMaintenanceType(null) },
                    text = { Text(stringResource(R.string.tab_all)) }
                )
                Tab(
                    selected = uiState.selectedMaintenanceType == MaintenanceType.REPAIR,
                    onClick = { viewModel.selectMaintenanceType(MaintenanceType.REPAIR) },
                    text = { Text(stringResource(R.string.tab_repairs)) }
                )
                Tab(
                    selected = uiState.selectedMaintenanceType == MaintenanceType.SCHEDULED_SERVICE,
                    onClick = { viewModel.selectMaintenanceType(MaintenanceType.SCHEDULED_SERVICE) },
                    text = { Text(stringResource(R.string.tab_service)) }
                )
                Tab(
                    selected = uiState.selectedMaintenanceType == MaintenanceType.MODIFICATION,
                    onClick = { viewModel.selectMaintenanceType(MaintenanceType.MODIFICATION) },
                    text = { Text(stringResource(R.string.tab_modifications)) }
                )
            }
            
            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Ошибка загрузки",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.filteredBreakdowns.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
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
                            Text(
                                text = if (uiState.selectedMaintenanceType == null) {
                                    "Записей пока нет"
                                } else {
                                    "Записей этого типа пока нет"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.selectedMaintenanceType == null) {
                                Text(
                                    text = "Нажмите +, чтобы добавить первую запись",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.filteredBreakdowns,
                            key = { it.id }
                        ) { breakdown ->
                            BreakdownCard(
                                breakdown = breakdown,
                                onBreakdownClick = { onNavigateToBreakdownDetail(carId, breakdown.id) },
                                onDeleteClick = { viewModel.deleteBreakdown(breakdown) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(
    breakdown: Breakdown,
    onBreakdownClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Определяем цвет фона карточки на основе типа обслуживания
    val maintenanceType = breakdown.maintenanceType?.let { MaintenanceType.fromString(it) } 
        ?: MaintenanceType.REPAIR  // Старые записи без типа = Ремонты
    
    val cardColors = when (maintenanceType) {
        MaintenanceType.REPAIR -> CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        MaintenanceType.SCHEDULED_SERVICE -> CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
        MaintenanceType.MODIFICATION -> CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    }
    
    ElevatedCard(
        onClick = onBreakdownClick,
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors
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
                    // Показываем тип обслуживания
                    breakdown.maintenanceType?.let { typeString ->
                        MaintenanceType.fromString(typeString)?.let { type ->
                            Text(
                                text = type.getDisplayName(),
                                style = MaterialTheme.typography.labelMedium,
                                color = when (type) {
                                    MaintenanceType.REPAIR -> MaterialTheme.colorScheme.error
                                    MaintenanceType.SCHEDULED_SERVICE -> MaterialTheme.colorScheme.primary
                                    MaintenanceType.MODIFICATION -> MaterialTheme.colorScheme.tertiary
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = breakdown.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (breakdown.isWarrantyRepair) {
                        Text(
                            text = "Гарантийный ремонт",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = breakdown.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Дата:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(breakdown.breakdownDate),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Пробег:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${breakdown.breakdownMileage} км",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Запчасти:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "₽%.2f".format(breakdown.partsCost),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Работа:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (breakdown.isWarrantyRepair) "ремонт по гарантии" else "₽%.2f".format(breakdown.serviceCost ?: 0.0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Итого:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₽%.2f".format(breakdown.totalCost),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Информация о сервисе
            if (!breakdown.serviceName.isNullOrBlank() || !breakdown.serviceAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                
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
                            text = "Сервис:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        if (!breakdown.serviceName.isNullOrBlank()) {
                            Text(
                                text = breakdown.serviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (!breakdown.serviceAddress.isNullOrBlank()) {
                            Text(
                                text = breakdown.serviceAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_breakdown_dialog_title)) },
            text = { Text(stringResource(R.string.delete_breakdown_dialog_message, breakdown.title)) },
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
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
