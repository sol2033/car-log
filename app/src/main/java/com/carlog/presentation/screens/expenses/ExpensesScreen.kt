package com.carlog.presentation.screens.expenses

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
import com.carlog.domain.model.Expense
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: (Long, Long?) -> Unit,
    onNavigateToExpenseDetail: (Long) -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val car by viewModel.car.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val expensesCount by viewModel.expensesCount.collectAsState()
    val totalCost by viewModel.totalCost.collectAsState()
    val averageCost by viewModel.averageCost.collectAsState()
    
    var showSortMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.other_expenses)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    // Кнопка сортировки
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, "Сортировка")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_date_newest)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.DATE_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.DATE_DESC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_date_oldest)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.DATE_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.DATE_ASC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_mileage_high)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.MILEAGE_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.MILEAGE_DESC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_mileage_low)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.MILEAGE_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.MILEAGE_ASC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_cost_high)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.COST_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.COST_DESC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_cost_low)) },
                            onClick = {
                                viewModel.setSortType(ExpensesViewModel.SortType.COST_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == ExpensesViewModel.SortType.COST_ASC) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddExpense(carId, null) },
                containerColor = Color(0xFF2196F3) // Синий
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить расход"
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
            if (expenses.isNotEmpty()) {
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
                            text = stringResource(R.string.statistics_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                label = "Всего расходов",
                                value = "$expensesCount"
                            )
                            
                            StatItem(
                                label = "Общая стоимость",
                                value = String.format("%.0f ₽", totalCost ?: 0.0),
                                valueColor = Color(0xFF2196F3) // Синий
                            )
                        }
                        
                        if (averageCost != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            StatItem(
                                label = "Средняя стоимость",
                                value = String.format("%.0f ₽", averageCost)
                            )
                        }
                    }
                }
            }
            
            // Список расходов
            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет записей о расходах",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нажмите + чтобы добавить",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(expenses) { expense ->
                        ExpenseCard(
                            expense = expense,
                            onClick = { onNavigateToExpenseDetail(expense.id) },
                            onDelete = { viewModel.deleteExpense(expense) }
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
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
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
            color = valueColor
        )
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
                    .background(Color(0xFF2196F3).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dateFormat.format(Date(expense.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = expense.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${expense.mileage} км",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (expense.serviceName != null) {
                        Text(
                            text = "• ${expense.serviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = String.format("%.0f ₽", expense.cost),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
            }
            
            // Кнопка удаления
            IconButton(
                onClick = { showDeleteDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Диалог подтверждения удаления
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_expense_title)) },
            text = { Text(stringResource(R.string.delete_expense_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
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
