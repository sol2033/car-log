package com.carlog.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.domain.model.WorkItem

/**
 * Окно добавления или редактирования одной работы обслуживания.
 *
 * Работа живёт только внутри своего обслуживания: отдельного раздела у неё нет, поэтому
 * ни дат, ни пробега здесь не спрашиваем — они берутся у события.
 *
 * @param initial редактируемая работа; null — добавление новой.
 */
@Composable
fun WorkItemDialog(
    initial: WorkItem?,
    onDismiss: () -> Unit,
    onConfirm: (WorkItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    var nameTouched by remember { mutableStateOf(false) }
    var costTouched by remember { mutableStateOf(false) }

    val costValue = cost.replace(',', '.').toDoubleOrNull()
    val nameError = nameTouched && name.isBlank()
    val costError = costTouched && (costValue == null || costValue < 0)
    val canConfirm = name.isNotBlank() && costValue != null && costValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.work_item_add_title
                    else R.string.work_item_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameTouched = true },
                    label = { Text(stringResource(R.string.work_item_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_required_field)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it; costTouched = true },
                    label = { Text(stringResource(R.string.work_item_cost)) },
                    isError = costError,
                    supportingText = if (costError) {
                        { Text(stringResource(R.string.error_required_field)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.work_item_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        WorkItem(
                            name = name.trim(),
                            cost = costValue ?: 0.0,
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
