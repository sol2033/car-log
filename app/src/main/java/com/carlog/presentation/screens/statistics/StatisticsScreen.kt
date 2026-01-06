package com.carlog.presentation.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.StatisticsPeriod
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBreakdowns: () -> Unit,
    onNavigateToConsumables: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Period filter
            PeriodFilterRow(
                selectedPeriod = uiState.selectedPeriod,
                specificMonth = uiState.specificMonth,
                onPeriodSelected = viewModel::setPeriod,
                onSpecificMonthSelected = viewModel::setSpecificMonth,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Exclude accidents checkbox (visible on all tabs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.excludeAccidents,
                    onCheckedChange = { viewModel.toggleExcludeAccidents() }
                )
                Text(
                    text = "Исключить ДТП из расчетов",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.tab_general)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.tab_fuel)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text(stringResource(R.string.tab_repairs)) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text(stringResource(R.string.tab_other_expenses)) }
                )
                Tab(
                    selected = selectedTabIndex == 4,
                    onClick = { selectedTabIndex = 4 },
                    text = { Text(stringResource(R.string.tab_consumables)) }
                )
            }
            
            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.error != null -> {
                        ErrorContent(
                            error = uiState.error!!,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.statistics.isEmpty -> {
                        EmptyStatisticsContent(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        when (selectedTabIndex) {
                            0 -> GeneralStatisticsTab(
                                statistics = uiState.statistics.general,
                                selectedPeriod = uiState.selectedPeriod,
                                specificMonth = uiState.specificMonth,
                                modifier = Modifier.fillMaxSize()
                            )
                            1 -> FuelStatisticsTab(
                                statistics = uiState.statistics.fuel,
                                selectedPeriod = uiState.selectedPeriod,
                                specificMonth = uiState.specificMonth,
                                modifier = Modifier.fillMaxSize()
                            )
                            2 -> RepairsStatisticsTab(
                                statistics = uiState.statistics.repairs,
                                selectedPeriod = uiState.selectedPeriod,
                                specificMonth = uiState.specificMonth,
                                onNavigateToBreakdowns = onNavigateToBreakdowns,
                                modifier = Modifier.fillMaxSize()
                            )
                            3 -> ExpensesStatisticsTab(
                                statistics = uiState.statistics.expenses,
                                modifier = Modifier.fillMaxSize()
                            )
                            4 -> ConsumablesStatisticsTab(
                                statistics = uiState.statistics.consumables,
                                onNavigateToConsumables = onNavigateToConsumables,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(
    selectedPeriod: StatisticsPeriod,
    specificMonth: YearMonth?,
    onPeriodSelected: (StatisticsPeriod) -> Unit,
    onSpecificMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Период:",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (specificMonth != null) {
                        getMonthYearText(specificMonth)
                    } else {
                        getPeriodText(selectedPeriod)
                    }
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                StatisticsPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(getPeriodText(period)) },
                        onClick = {
                            onPeriodSelected(period)
                            expanded = false
                        }
                    )
                }
                
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_month)) },
                    onClick = {
                        expanded = false
                        showMonthPicker = true
                    }
                )
            }
        }
    }
    
    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYearMonth = specificMonth ?: YearMonth.now(),
            onDismiss = { showMonthPicker = false },
            onConfirm = { yearMonth ->
                onSpecificMonthSelected(yearMonth)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun getPeriodText(period: StatisticsPeriod): String {
    return when (period) {
        StatisticsPeriod.WEEK -> "Неделя"
        StatisticsPeriod.TWO_WEEKS -> "2 недели"
        StatisticsPeriod.MONTH -> "Месяц"
        StatisticsPeriod.THREE_MONTHS -> "3 месяца"
        StatisticsPeriod.SIX_MONTHS -> "6 месяцев"
        StatisticsPeriod.YEAR -> "Год"
        StatisticsPeriod.ALL_TIME -> "Всё время"
    }
}

@Composable
private fun getMonthYearText(yearMonth: YearMonth): String {
    val monthName = when(yearMonth.monthValue) {
        1 -> "Январь"
        2 -> "Февраль"
        3 -> "Март"
        4 -> "Апрель"
        5 -> "Май"
        6 -> "Июнь"
        7 -> "Июль"
        8 -> "Август"
        9 -> "Сентябрь"
        10 -> "Октябрь"
        11 -> "Ноябрь"
        12 -> "Декабрь"
        else -> ""
    }
    return "$monthName ${yearMonth.year}"
}

@Composable
private fun EmptyStatisticsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Невозможно составить статистику",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Данные не добавлены",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(error: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ошибка",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
