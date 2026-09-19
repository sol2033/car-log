package com.carlog.presentation.screens.fines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.Fine
import com.carlog.presentation.components.ConfirmDeleteDialog
import com.carlog.util.FineStatus
import com.carlog.util.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinesScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddFine: (Long, Long?) -> Unit,
    viewModel: FinesViewModel = hiltViewModel()
) {
    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    val fines by viewModel.fines.collectAsState()
    val totalPaid by viewModel.totalPaid.collectAsState()
    val totalUnpaid by viewModel.totalUnpaid.collectAsState()
    val unpaidCount by viewModel.unpaidCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fines)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAddFine(carId, null) }) {
                Icon(Icons.Default.Add, stringResource(R.string.add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (fines.isNotEmpty()) {
                FinesSummaryCard(
                    count = fines.size,
                    unpaidCount = unpaidCount,
                    totalPaid = totalPaid ?: 0.0,
                    totalUnpaid = totalUnpaid ?: 0.0
                )
            }

            if (fines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.fines_empty),
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
                    items(fines, key = { it.id }) { fine ->
                        FineCard(
                            fine = fine,
                            onClick = { onNavigateToAddFine(carId, fine.id) },
                            onTogglePaid = {
                                if (fine.isPaid) viewModel.markAsUnpaid(fine)
                                else viewModel.markAsPaid(fine)
                            },
                            onDelete = { viewModel.deleteFine(fine) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinesSummaryCard(
    count: Int,
    unpaidCount: Int,
    totalPaid: Double,
    totalUnpaid: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                SummaryItem(
                    label = stringResource(R.string.fines_count),
                    value = "$count"
                )
                SummaryItem(
                    label = stringResource(R.string.fines_paid_total),
                    value = formatCurrency(totalPaid)
                )
            }

            if (unpaidCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(
                            R.string.fines_unpaid_summary,
                            unpaidCount,
                            formatCurrency(totalUnpaid)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
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
private fun FineCard(
    fine: Fine,
    onClick: () -> Unit,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val statusInfo = remember(fine.isPaid, fine.discountUntil) {
        FineStatus.calculateStatus(fine.isPaid, fine.discountUntil)
    }
    val statusColor = when (statusInfo.status) {
        FineStatus.Status.PAID -> Color(0xFF4CAF50)    // Зелёный — вопрос закрыт
        FineStatus.Status.NORMAL -> Color(0xFF2196F3)  // Синий — скидка ещё далеко
        FineStatus.Status.WARNING -> Color(0xFFFF9800) // Оранжевый — скидка догорает
        FineStatus.Status.EXPIRED -> Color(0xFFF44336) // Красный — скидка сгорела
    }

    // Короткий формат, как во всех остальных списках: «16 сент. 2026» не помещался
    // в строку рядом с суммой, и оба текста переносились вперемешку
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fine.isPaid) Icons.Default.Check else Icons.Default.Gavel,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Сумма — не переносится ни при каких условиях: место ей отводится
                        // первой, а ужимается дата. Раньше вес не был задан ни одному из них,
                        // и в узкой карточке «500,00 ₽» разваливалось на три строки,
                        // перемешиваясь с датой
                        Text(
                            text = dateFormat.format(Date(fine.date)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatCurrency(if (fine.isPaid) fine.paidCost else fine.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = fine.displayViolation,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    fine.location?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Статус: оплачен / сколько осталось от скидки / скидка сгорела
                    val statusText = when (statusInfo.status) {
                        FineStatus.Status.PAID -> stringResource(R.string.fine_status_paid)
                        FineStatus.Status.EXPIRED -> stringResource(R.string.fine_status_discount_expired)
                        FineStatus.Status.WARNING, FineStatus.Status.NORMAL ->
                            statusInfo.remainingDays?.let {
                                stringResource(R.string.fine_status_discount_days, it)
                            } ?: stringResource(R.string.fine_status_unpaid)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

            }

            // Кнопки — отдельной строкой внизу, как в карточках ДТП и обслуживаний.
            // В общей строке они занимали 96dp, и на дату с суммой оставалось около 136dp:
            // на узком экране «500,00 ₽» не помещалось рядом с датой
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Быстрая отметка об оплате: самое частое действие, ради него незачем открывать форму
                IconButton(onClick = onTogglePaid) {
                    Icon(
                        imageVector = if (fine.isPaid) Icons.AutoMirrored.Filled.Undo else Icons.Default.Payments,
                        contentDescription = stringResource(
                            if (fine.isPaid) R.string.fine_mark_unpaid else R.string.fine_mark_paid
                        ),
                        tint = if (fine.isPaid) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_fine_title),
            text = stringResource(R.string.delete_fine_message),
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}
