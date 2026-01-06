package com.carlog.presentation.screens.consumables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.ConsumableCategories
import com.carlog.util.ConsumableStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToConsumableDetail: (Long) -> Unit,
    onNavigateToAddConsumable: (Long, String?) -> Unit,
    onNavigateToHistory: (Long) -> Unit,
    onNavigateToSettings: (Long) -> Unit,
    viewModel: ConsumablesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showFirstLaunchDialog by viewModel.showFirstLaunchDialog.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
    // Показать диалог настроек при первом запуске
    if (showFirstLaunchDialog) {
        FirstLaunchDialog(
            onComplete = { /* ViewModel автоматически обновит флаг */ }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.consumables_section_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToSettings(carId) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { onNavigateToAddConsumable(carId, null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_consumable))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.active_tab)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1
                        onNavigateToHistory(carId)
                    },
                    text = { Text(stringResource(R.string.history_tab)) }
                )
            }
            
            when (val state = uiState) {
                is ConsumablesUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ConsumablesUiState.Success -> {
                    if (state.activeConsumables.isEmpty()) {
                        EmptyConsumablesContent(
                            onAddClick = { onNavigateToAddConsumable(carId, null) }
                        )
                    } else {
                        ActiveConsumablesGrid(
                            carId = carId,
                            consumables = state.activeConsumables,
                            onConsumableClick = onNavigateToConsumableDetail,
                            onAddConsumable = onNavigateToAddConsumable
                        )
                    }
                }
                is ConsumablesUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
}

@Composable
fun ActiveConsumablesGrid(
    carId: Long,
    consumables: List<ConsumableWithStatus>,
    onConsumableClick: (Long) -> Unit,
    onAddConsumable: (Long, String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(consumables) { consumableWithStatus ->
            ConsumableCard(
                consumableWithStatus = consumableWithStatus,
                onClick = {
                    if (consumableWithStatus.consumable != null) {
                        onConsumableClick(consumableWithStatus.consumable.id)
                    } else {
                        // Открыть форму добавления с предзаполненной категорией
                        onAddConsumable(carId, consumableWithStatus.category)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumableCard(
    consumableWithStatus: ConsumableWithStatus,
    onClick: () -> Unit
) {
    val consumable = consumableWithStatus.consumable
    val statusInfo = consumableWithStatus.statusInfo
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    // Если расходник не добавлен - синяя карточка
    val cardColor = if (consumable == null) {
        if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFBBDEFB) // Темно-синий / Светло-синий
    } else {
        when (statusInfo?.status) {
            ConsumableStatus.Status.NORMAL -> if (isDarkTheme) Color(0xFF2E5C3A) else Color(0xFFC8E6C9) // Темно-зеленый / Светло-зеленый
            ConsumableStatus.Status.WARNING -> if (isDarkTheme) Color(0xFF5C5230) else Color(0xFFFFF9C4) // Темно-желтый / Светло-желтый
            ConsumableStatus.Status.CRITICAL -> if (isDarkTheme) Color(0xFF5C2E2E) else Color(0xFFFFCDD2) // Темно-красный / Светло-красный
            else -> MaterialTheme.colorScheme.surface
        }
    }
    
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = consumableWithStatus.category,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Start
            )
            
            if (consumable == null) {
                // Для не добавленных расходников
                Text(
                    text = stringResource(R.string.consumable_not_added),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1976D2), // Светло-синий для темной темы / Синий текст
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Для добавленных расходников
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    consumable.manufacturer?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                    
                    consumable.cost?.let {
                        Text(
                            text = "$it ₽",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        text = "${consumable.installationMileage} км",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Показываем сколько осталось или превышение
                    statusInfo?.let { info ->
                        val mileageRemaining = info.remainingMileage
                        val daysRemaining = info.remainingDays
                        
                        // Проверяем, есть ли превышения
                        val mileageExceeded = mileageRemaining != null && mileageRemaining < 0
                        val daysExceeded = daysRemaining != null && daysRemaining < 0
                        
                        val message = when {
                            // Оба превышены
                            mileageExceeded && daysExceeded -> {
                                stringResource(
                                    R.string.interval_and_date_exceeded,
                                    "${-mileageRemaining!!} км",
                                    "${-daysRemaining!!} дн"
                                )
                            }
                            // Только пробег превышен
                            mileageExceeded -> {
                                stringResource(R.string.interval_exceeded_mileage, "${-mileageRemaining!!} км")
                            }
                            // Только дата превышена
                            daysExceeded -> {
                                stringResource(R.string.date_exceeded, "${-daysRemaining!!} дн")
                            }
                            // Ничего не превышено - показываем остаток
                            else -> {
                                val parts = mutableListOf<String>()
                                mileageRemaining?.let { if (it >= 0) parts.add("$it км") }
                                daysRemaining?.let { if (it >= 0) parts.add("$it дн") }
                                if (parts.isNotEmpty()) {
                                    stringResource(R.string.remaining_mileage_or_days, parts.joinToString(" или "))
                                } else null
                            }
                        }
                        
                        message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    mileageExceeded || daysExceeded -> MaterialTheme.colorScheme.error
                                    info.status == ConsumableStatus.Status.WARNING -> if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFF57C00)
                                    else -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF388E3C)
                                },
                                fontWeight = if (mileageExceeded || daysExceeded) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyConsumablesContent(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.no_active_consumables),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddClick) {
                Text(stringResource(R.string.add_consumable))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstLaunchDialog(
    onComplete: () -> Unit,
    viewModel: ConsumableSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onComplete()
        }
    }
    
    AlertDialog(
        onDismissRequest = { /* Не позволяем закрыть */ },
        title = {
            Text(
                text = stringResource(R.string.consumable_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.consumable_welcome_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.additional_categories),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                ConsumableCategories.ADDITIONAL_CATEGORIES.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.selectedCategories.contains(category),
                            onCheckedChange = { viewModel.toggleCategory(category) }
                        )
                        Text(
                            text = category,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveSettings() },
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.start))
                }
            }
        }
    )
}
