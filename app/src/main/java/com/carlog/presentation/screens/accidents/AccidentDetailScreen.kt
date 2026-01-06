package com.carlog.presentation.screens.accidents

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.domain.model.Accident
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentDetailScreen(
    accidentId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: AccidentDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state is AccidentDetailState.Success) {
                        IconButton(onClick = { onNavigateToEdit(accidentId) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit))
                        }
                        IconButton(onClick = viewModel::showDeleteDialog) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val currentState = state) {
            is AccidentDetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AccidentDetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is AccidentDetailState.Success -> {
                AccidentDetailContent(
                    accident = currentState.accident,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text(stringResource(R.string.delete_accident_detail_title)) },
            text = { Text(stringResource(R.string.delete_accident_detail_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccident(onNavigateBack)
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AccidentDetailContent(
    accident: Accident,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Карточка статуса
        StatusCard(accident)
        
        // Основная информация
        InfoSection(
            title = "Основная информация",
            icon = Icons.Default.Info
        ) {
            InfoRow("Дата", formatDate(accident.date))
            InfoRow("Пробег", "${accident.mileage} км")
            if (!accident.location.isNullOrBlank()) {
                InfoRow("Место", accident.location)
            }
            InfoRow("Описание повреждений", accident.damageDescription)
        }
        
        // Выплаты
        if (accident.osagoPayout != null || accident.kaskoPayout != null || accident.culpritPayout != null) {
            PayoutsInfoSection(accident)
        }
        
        // Ремонт
        if (accident.repairCost != null) {
            RepairInfoSection(accident)
        }
        
        // Фотографии
        if (!accident.photosPaths.isNullOrEmpty()) {
            PhotosInfoSection(accident.photosPaths)
        }
        
        // Документ
        if (accident.documentPath != null) {
            DocumentInfoSection()
        }
        
        // Заметки
        if (!accident.notes.isNullOrBlank()) {
            InfoSection(
                title = "Заметки",
                icon = Icons.Default.Edit
            ) {
                Text(text = accident.notes)
            }
        }
    }
}

@Composable
private fun StatusCard(accident: Accident) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (accident.severity) {
                "Незначительная" -> MaterialTheme.colorScheme.primaryContainer
                "Средняя" -> MaterialTheme.colorScheme.tertiaryContainer
                "Серьезная" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                "Тотальная" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Серьезность:",
                    style = MaterialTheme.typography.titleMedium
                )
                
                AssistChip(
                    onClick = {},
                    label = { Text(accident.severity) },
                    leadingIcon = {
                        Icon(
                            when (accident.severity) {
                                "Незначительная" -> Icons.Default.Check
                                "Средняя" -> Icons.Default.Info
                                "Серьезная" -> Icons.Default.Warning
                                "Тотальная" -> Icons.Default.Close
                                else -> Icons.Default.Info
                            },
                            contentDescription = null
                        )
                    }
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (accident.isUserAtFault) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (accident.isUserAtFault) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (accident.isUserAtFault) "Я виновник" else "Я пострадавший",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PayoutsInfoSection(accident: Accident) {
    InfoSection(
        title = "Страховые выплаты",
        icon = Icons.Default.AccountBalance
    ) {
        accident.osagoPayout?.let {
            InfoRow("По ОСАГО", "$it ₽", valueColor = MaterialTheme.colorScheme.primary)
        }
        accident.kaskoPayout?.let {
            InfoRow("По КАСКО", "$it ₽", valueColor = MaterialTheme.colorScheme.primary)
        }
        accident.culpritPayout?.let {
            InfoRow("От виновника", "$it ₽", valueColor = MaterialTheme.colorScheme.primary)
        }
        
        val totalPayouts = listOfNotNull(
            accident.osagoPayout,
            accident.kaskoPayout,
            accident.culpritPayout
        ).sum()
        
        if (totalPayouts > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(
                "Всего выплат",
                "$totalPayouts ₽",
                labelWeight = FontWeight.Bold,
                valueColor = MaterialTheme.colorScheme.primary,
                valueWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RepairInfoSection(accident: Accident) {
    InfoSection(
        title = "Стоимость ремонта",
        icon = Icons.Default.Build
    ) {
        InfoRow(
            "Общая стоимость",
            "${accident.repairCost} ₽",
            valueColor = MaterialTheme.colorScheme.error,
            valueWeight = FontWeight.Bold
        )
        
        if (!accident.installedPartIds.isNullOrEmpty()) {
            Text(
                text = "Установлено запчастей: ${accident.installedPartIds.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PhotosInfoSection(photosPaths: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Фотографии (${photosPaths.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photosPaths) { photoPath ->
                AsyncImage(
                    model = Uri.parse(photoPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun DocumentInfoSection() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Прикреплен документ ДТП (PDF)",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    labelWeight: FontWeight = FontWeight.Normal,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    valueWeight: FontWeight = FontWeight.Normal
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = labelWeight
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = valueWeight
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    return sdf.format(Date(timestamp))
}
