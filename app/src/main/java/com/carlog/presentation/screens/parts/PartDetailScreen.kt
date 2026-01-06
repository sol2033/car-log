package com.carlog.presentation.screens.parts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
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
import com.carlog.domain.model.Part
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartDetailScreen(
    carId: Long,
    partId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long, Long) -> Unit,
    onNavigateToAddPart: (Long) -> Unit,
    viewModel: PartDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showMarkBrokenDialog by viewModel.showMarkBrokenDialog.collectAsState()
    val showReplacePartDialog by viewModel.showReplacePartDialog.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is PartDetailUiState.Success -> Text(state.part.name)
                        else -> Text(stringResource(R.string.part_screen_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState is PartDetailUiState.Success) {
                        IconButton(onClick = { onNavigateToEdit(carId, partId) }) {
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
            is PartDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is PartDetailUiState.Success -> {
                PartDetailContent(
                    part = state.part,
                    onMarkAsBroken = { viewModel.showMarkBrokenDialog() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            is PartDetailUiState.Error -> {
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
    
    if (showDeleteDialog && uiState is PartDetailUiState.Success) {
        val part = (uiState as PartDetailUiState.Success).part
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_part_title)) },
            text = { Text(stringResource(R.string.delete_part_message, part.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePart(part) {
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
    
    // Диалог подтверждения поломки
    if (showMarkBrokenDialog && uiState is PartDetailUiState.Success) {
        val part = (uiState as PartDetailUiState.Success).part
        AlertDialog(
            onDismissRequest = { viewModel.dismissMarkBrokenDialog() },
            title = { Text(stringResource(R.string.mark_broken_title)) },
            text = { Text(stringResource(R.string.mark_broken_message, part.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissMarkBrokenDialog()
                        viewModel.showReplacePartDialog()
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMarkBrokenDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Диалог предложения добавить новую запчасть
    if (showReplacePartDialog && uiState is PartDetailUiState.Success) {
        val part = (uiState as PartDetailUiState.Success).part
        AlertDialog(
            onDismissRequest = { viewModel.dismissReplacePartDialog() },
            title = { Text(stringResource(R.string.add_new_part_title)) },
            text = { Text(stringResource(R.string.add_new_part_message, part.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissReplacePartDialog()
                        viewModel.markPartAsBroken(part, 0)
                        onNavigateToAddPart(carId)
                    }
                ) {
                    Text(stringResource(R.string.yes_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissReplacePartDialog()
                        viewModel.markPartAsBroken(part, 0)
                    }
                ) {
                    Text(stringResource(R.string.no_dont_add))
                }
            }
        )
    }
}

@Composable
private fun PartDetailContent(
    part: Part,
    onMarkAsBroken: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Кнопка отметки сломанной (только для активных)
        if (!part.isBroken) {
            Button(
                onClick = onMarkAsBroken,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.BuildCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mark_part_broken))
            }
        }
        
        // Status Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (part.isBroken) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (part.isBroken) "Сломана" else "Активна",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (part.isBroken) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
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
                
                InfoRow(label = "Название", value = part.name)
                part.manufacturer?.let { InfoRow(label = "Производитель", value = it) }
                part.partNumber?.let { InfoRow(label = "Артикул", value = it) }
            }
        }
        
        // Installation Information
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Информация об установке",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                InfoRow(label = "Дата установки", value = formatDate(part.installDate))
                InfoRow(label = "Пробег при установке", value = "${part.installMileage} км")
                InfoRow(label = "Способ установки", value = part.installationType)
                
                // Выделение если запчасть установлена из-за ДТП
                if (part.installationType == "ДТП") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Запчасть установлена после ДТП",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (part.mileageDriven != null) {
                    InfoRow(label = "Проехала запчасть", value = "${part.mileageDriven} км")
                }
            }
        }
        
        // Breakdown Information (if broken)
        if (part.isBroken && part.breakdownDate != null && part.breakdownMileage != null) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Информация о поломке",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    InfoRow(
                        label = "Дата поломки",
                        value = formatDate(part.breakdownDate),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        valueColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                    InfoRow(
                        label = "Пробег при поломке",
                        value = "${part.breakdownMileage} км",
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        valueColor = MaterialTheme.colorScheme.onErrorContainer
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
                
                InfoRow(label = "Цена запчасти", value = "₽%.2f".format(part.price))
                part.servicePrice?.let {
                    InfoRow(label = "Стоимость работы", value = "₽%.2f".format(it))
                }
                
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                
                val totalCost = part.price + (part.servicePrice ?: 0.0)
                InfoRow(
                    label = "Общая стоимость",
                    value = "₽%.2f".format(totalCost),
                    labelStyle = MaterialTheme.typography.titleSmall,
                    valueStyle = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        // Service Information
        if (!part.serviceName.isNullOrBlank() || !part.serviceAddress.isNullOrBlank()) {
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
                    
                    part.serviceName?.let {
                        InfoRow(
                            label = "Название",
                            value = it,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    part.serviceAddress?.let {
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
        if (!part.photosPaths.isNullOrEmpty()) {
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
                        items(part.photosPaths) { photoPath ->
                            Card(
                                modifier = Modifier.size(150.dp)
                            ) {
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = "Фото запчасти",
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
        if (!part.notes.isNullOrBlank()) {
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
                        text = part.notes,
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
