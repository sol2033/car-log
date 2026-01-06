package com.carlog.presentation.screens.consumables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.Consumable
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryHistoryScreen(
    carId: Long,
    category: String,
    onNavigateBack: () -> Unit,
    onConsumableClick: (Long) -> Unit,
    viewModel: CategoryHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортировка")
                    }
                    
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_date_newest)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.DATE_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = if (sortOrder == SortOrder.DATE_DESC) {
                                { Icon(Icons.Default.Done, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_date_oldest)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.DATE_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = if (sortOrder == SortOrder.DATE_ASC) {
                                { Icon(Icons.Default.Done, null) }
                            } else null
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_mileage_high)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.MILEAGE_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = if (sortOrder == SortOrder.MILEAGE_DESC) {
                                { Icon(Icons.Default.Done, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_mileage_low)) },
                            onClick = {
                                viewModel.setSortOrder(SortOrder.MILEAGE_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = if (sortOrder == SortOrder.MILEAGE_ASC) {
                                { Icon(Icons.Default.Done, null) }
                            } else null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CategoryHistoryUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is CategoryHistoryUiState.Success -> {
                if (state.consumables.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "История пуста",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.consumables) { consumable ->
                            HistoryConsumableCard(
                                consumable = consumable,
                                onClick = { onConsumableClick(consumable.id) }
                            )
                        }
                    }
                }
            }
            is CategoryHistoryUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryConsumableCard(
    consumable: Consumable,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active status badge
            if (consumable.isActive) {
                AssistChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.installed_now)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    consumable.manufacturer?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    consumable.articleNumber?.let {
                        Text(
                            text = "Артикул: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                consumable.cost?.let {
                    Text(
                        text = "$it ₽",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Установлен",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${consumable.installationMileage} км",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dateFormat.format(Date(consumable.installationDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                consumable.replacementMileage?.let { replacementMileage ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Заменен",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$replacementMileage км",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        consumable.replacementDate?.let {
                            Text(
                                text = dateFormat.format(Date(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            if (consumable.replacementMileage != null) {
                val used = consumable.replacementMileage - consumable.installationMileage
                Text(
                    text = "Использовано: $used км",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
