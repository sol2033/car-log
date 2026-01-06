package com.carlog.presentation.screens.refuelings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.Refueling
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelingsScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddRefueling: (Long, Long?) -> Unit,
    viewModel: RefuelingsViewModel = hiltViewModel()
) {
    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }
    
    val car by viewModel.car.collectAsState()
    val refuelings by viewModel.refuelings.collectAsState()
    val totalCost by viewModel.totalCost.collectAsState()
    val totalLiters by viewModel.totalLiters.collectAsState()
    val averageConsumption by viewModel.averageConsumption.collectAsState()
    
    val isElectric = car?.fuelType == "Электро"
    val title = if (isElectric) "Зарядки" else "Заправки"
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddRefueling(carId, null) },
                containerColor = if (isElectric) Color(0xFF4CAF50) else Color(0xFF2196F3)
            ) {
                Icon(
                    imageVector = if (isElectric) Icons.Default.Add else Icons.Default.Add,
                    contentDescription = "Добавить"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Статистика
            if (refuelings.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Статистика",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                label = if (isElectric) "Зарядок" else "Заправок",
                                value = "${refuelings.size}"
                            )
                            
                            StatItem(
                                label = "Всего ${if (isElectric) "кВт·ч" else "л"}",
                                value = String.format("%.1f", totalLiters ?: 0.0)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                label = "Общая стоимость",
                                value = String.format("%.0f ₽", totalCost ?: 0.0)
                            )
                            
                            if (!isElectric && averageConsumption != null) {
                                StatItem(
                                    label = "Средний расход",
                                    value = String.format("%.1f л/100км", averageConsumption)
                                )
                            }
                        }
                    }
                }
            }
            
            // Список заправок
            if (refuelings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isElectric) "Нет записей о зарядках" else "Нет записей о заправках",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(refuelings) { refueling ->
                        RefuelingCard(
                            refueling = refueling,
                            isElectric = isElectric,
                            onClick = { onNavigateToAddRefueling(carId, refueling.id) },
                            onDelete = { viewModel.deleteRefueling(refueling) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun RefuelingCard(
    refueling: Refueling,
    isElectric: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Определяем цвет иконки на основе типа топлива
    val iconColor = when {
        refueling.fuelType.contains("Электр", ignoreCase = true) -> Color(0xFF2196F3) // Синий для электро
        refueling.fuelType.contains("Метан", ignoreCase = true) || 
        refueling.fuelType.contains("Пропан", ignoreCase = true) ||
        refueling.fuelType.contains("CNG", ignoreCase = true) -> Color(0xFF2196F3) // Синий для газа
        refueling.fuelType.contains("АИ", ignoreCase = true) ||
        refueling.fuelType == "Бензин" -> Color(0xFF4CAF50) // Зелёный для бензина
        refueling.fuelType.contains("Дизель", ignoreCase = true) -> Color(0xFFFF9800) // Оранжевый для дизеля
        else -> Color(0xFF2196F3) // По умолчанию синий
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                // Выбираем иконку в зависимости от типа топлива
                val icon = when {
                    refueling.fuelType.contains("Метан", ignoreCase = true) || 
                    refueling.fuelType.contains("Пропан", ignoreCase = true) ||
                    refueling.fuelType.contains("CNG", ignoreCase = true) -> Icons.Default.Cloud // Облако для газа
                    else -> Icons.Default.LocalGasStation // Колонка для остальных
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy", Locale("ru")).format(Date(refueling.date)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (refueling.totalCost != null) {
                        Text(
                            text = "${String.format("%.0f", refueling.totalCost)} ₽",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${refueling.liters} ${if (isElectric) "кВт·ч" else "л"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = refueling.fuelType,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (refueling.stationName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = refueling.stationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            // Кнопка удаления
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_refueling_title)) },
            text = { Text(stringResource(R.string.delete_expense_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
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
