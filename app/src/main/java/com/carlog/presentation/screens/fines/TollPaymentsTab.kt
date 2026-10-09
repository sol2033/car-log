package com.carlog.presentation.screens.fines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.TollMonth
import com.carlog.domain.model.TollPayment
import com.carlog.domain.model.TollSummary
import com.carlog.presentation.components.ConfirmDeleteDialog
import com.carlog.util.formatCurrency
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.*

/** Вкладка «Платные дороги»: сводка и записи, сгруппированные по месяцам */
@Composable
internal fun TollPaymentsTabContent(
    viewModel: TollPaymentsViewModel,
    onOpenPayment: (Long) -> Unit
) {
    val summary = viewModel.summary.collectAsState().value ?: return

    if (summary.count == 0) {
        EmptyState(icon = Icons.Default.Toll, text = stringResource(R.string.toll_payments_empty))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") {
            TollSummaryCard(summary)
        }

        summary.months.forEach { month ->
            item(key = "month-${month.month}") {
                TollMonthHeader(month)
            }
            items(month.payments, key = { it.id }) { payment ->
                TollPaymentCard(
                    payment = payment,
                    onClick = { onOpenPayment(payment.id) },
                    onDelete = { viewModel.deleteTollPayment(payment) }
                )
            }
        }
    }
}

@Composable
private fun TollSummaryCard(summary: TollSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
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
                    label = stringResource(R.string.toll_payments_count),
                    value = "${summary.count}"
                )
                SummaryItem(
                    label = stringResource(R.string.toll_payments_total),
                    value = formatCurrency(summary.total)
                )
            }

            // За один месяц среднее совпало бы с итогом — показывать его незачем
            if (summary.monthsSpan > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                SummaryItem(
                    label = stringResource(R.string.toll_payments_average_month, summary.monthsSpan),
                    value = formatCurrency(summary.averagePerMonth)
                )
            }
        }
    }
}

/** Заголовок месяца: название слева, число проездов и сумма за месяц справа */
@Composable
private fun TollMonthHeader(month: TollMonth) {
    val locale = LocalConfiguration.current.locales[0]
    val monthName = month.month.month
        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$monthName ${month.month.year}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(R.plurals.toll_trips, month.count, month.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatCurrency(month.total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TollPaymentCard(
    payment: TollPayment,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Toll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Сумме место отводится первой, ужимается дата — как в карточке штрафа
                    Text(
                        text = dateFormat.format(Date(payment.date)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatCurrency(payment.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Text(
                    text = payment.roadName ?: stringResource(R.string.toll_road_unnamed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (payment.roadName != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )

                payment.notes?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_toll_payment_title),
            text = stringResource(R.string.delete_toll_payment_message),
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}
