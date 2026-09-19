package com.carlog.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.carlog.R

/**
 * Подтверждение удаления — один диалог на все «корзинки».
 *
 * Появился по следам правки, где подтверждение добавляли в каждый экран копипастой: тексты
 * расходились (у заправки подставлялось сообщение из расходов), а позиции внутри форм
 * удалялись вообще без спроса. Кнопка удаления окрашена в `error`, чтобы её нельзя было
 * спутать с отменой на автомате.
 *
 * @param text пояснение под заголовком; null — заголовка достаточно.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.delete)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
