package com.carlog.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.data.repository.ConsumableDeletePlan
import com.carlog.util.formatNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Удаление ТО или расходника, после которого категории могут остаться без текущей установки.
 *
 * Пока план удаления считается, диалог не показывается (это доли секунды). Если возвращать
 * некого — обычное подтверждение. Иначе пользователь видит, сколько расходников удалится
 * и какие предыдущие установки можно вернуть, и выбирает: вернуть их на место или оставить
 * категории пустыми (§4.4 бизнес-логики).
 *
 * @param message основной текст подтверждения («Удалить …?»)
 * @param removedNote строка о расходниках, уходящих вместе с записью (null — не показывать)
 * @param loadPlan расчёт плана удаления; вызывается один раз при открытии диалога
 * @param onDelete удалить; аргумент — вернуть ли предыдущие расходники
 */
@Composable
fun ConsumableDeleteDialog(
    title: String,
    message: String,
    removedNote: ((count: Int) -> String?)? = null,
    loadPlan: suspend () -> ConsumableDeletePlan,
    onDelete: (restorePredecessors: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val plan by produceState<ConsumableDeletePlan?>(initialValue = null) { value = loadPlan() }
    val current = plan ?: return
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    val note = removedNote?.invoke(current.removedCount)
    val text = buildString {
        append(message)
        note?.let { append("\n\n").append(it) }
        if (current.restorable.isNotEmpty()) {
            append("\n\n")
            append(stringResource(R.string.restore_predecessors_intro))
            current.restorable.forEach { item ->
                append("\n")
                append(
                    stringResource(
                        R.string.restore_predecessor_item,
                        item.removed.category,
                        formatNumber(item.predecessor.installationMileage),
                        dateFormat.format(Date(item.predecessor.installationDate))
                    )
                )
            }
            append("\n\n")
            append(stringResource(R.string.restore_predecessors_question))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (current.restorable.isEmpty()) {
                TextButton(onClick = { onDelete(false); onDismiss() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            } else {
                // Три исхода не помещаются в пару «да/нет» — кнопки столбиком
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    TextButton(onClick = { onDelete(true); onDismiss() }) {
                        Text(stringResource(R.string.restore_predecessors_yes), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onDelete(false); onDismiss() }) {
                        Text(stringResource(R.string.restore_predecessors_no), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        },
        dismissButton = if (current.restorable.isEmpty()) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        } else null
    )
}

/** Строка «вместе с записью удалятся расходники: N» для диалога удаления события */
@Composable
fun consumablesRemovedNote(): (Int) -> String? {
    val template = stringResource(R.string.consumables_removed_with_event)
    return { count -> if (count > 0) template.format(count) else null }
}
