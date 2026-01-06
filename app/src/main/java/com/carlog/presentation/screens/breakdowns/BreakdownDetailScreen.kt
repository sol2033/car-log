package com.carlog.presentation.screens.breakdowns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.carlog.domain.model.Breakdown
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownDetailScreen(
    carId: Long,
    breakdownId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long, Long) -> Unit,
    viewModel: BreakdownDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is BreakdownDetailUiState.Success -> Text(state.breakdown.title)
                        else -> Text(stringResource(R.string.breakdown_screen_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState is BreakdownDetailUiState.Success) {
                        IconButton(onClick = { onNavigateToEdit(carId, breakdownId) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit))
                        }
                        IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is BreakdownDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is BreakdownDetailUiState.Success -> {
                BreakdownDetailContent(
                    breakdown = state.breakdown,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            is BreakdownDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog && uiState is BreakdownDetailUiState.Success) {
        val breakdown = (uiState as BreakdownDetailUiState.Success).breakdown
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_breakdown_dialog_title)) },
            text = { Text(stringResource(R.string.delete_breakdown_dialog_message, breakdown.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBreakdown(breakdown) {
                            onNavigateBack()
                        }
                    }
                ) {
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
}

@Composable
private fun BreakdownDetailContent(
    breakdown: Breakdown,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warranty Status Card (if applicable)
        if (breakdown.isWarrantyRepair) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Гарантийный ремонт",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Basic Information
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Основная информация",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                InfoRow(label = "Название", value = breakdown.title)
                
                if (!breakdown.description.isNullOrBlank()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Описание",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = breakdown.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Breakdown Details
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Детали поломки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                InfoRow(label = "Дата поломки", value = formatDate(breakdown.breakdownDate))
                InfoRow(label = "Пробег при поломке", value = "${breakdown.breakdownMileage} км")
            }
        }
        
        // Parts Information (if parts were used)
        if (!breakdown.installedPartIds.isNullOrEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Замененные запчасти",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Количество: ${breakdown.installedPartIds.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Cost Information
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Стоимость",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                InfoRow(label = "Стоимость запчастей", value = "₽%.2f".format(breakdown.partsCost))
                InfoRow(label = "Стоимость работы", value = "₽%.2f".format(breakdown.serviceCost))
                
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                
                InfoRow(
                    label = "Общая стоимость",
                    value = "₽%.2f".format(breakdown.totalCost),
                    labelStyle = MaterialTheme.typography.titleSmall,
                    valueStyle = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        // Service Information
        if (!breakdown.serviceName.isNullOrBlank() || !breakdown.serviceAddress.isNullOrBlank()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Информация о сервисе",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    breakdown.serviceName?.let {
                        InfoRow(
                            label = "Название",
                            value = it,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    breakdown.serviceAddress?.let {
                        InfoRow(
                            label = "Адрес",
                            value = it,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        
        // Photos
        if (!breakdown.photosPaths.isNullOrEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Фотографии",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(breakdown.photosPaths) { photoPath ->
                            Card(
                                modifier = Modifier.size(150.dp)
                            ) {
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = "Фото поломки",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Notes
        if (!breakdown.notes.isNullOrBlank()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Заметки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = breakdown.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = labelColor
        )
        Text(
            text = value,
            style = valueStyle,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
