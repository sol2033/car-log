package com.carlog.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.carlog.BuildConfig
import com.carlog.R

/**
 * Показывать ли окно с изменениями.
 *
 * @param isFirstLaunch null — флаг ещё не прочитан; true — идёт онбординг (язык не выбран,
 * окно там неуместно).
 * @param shownForVersion null — настройка ещё не прочитана (иначе окно моргнуло бы у тех,
 * кто его уже закрыл); пустая строка — не показывали ни разу.
 */
fun shouldShowWhatsNew(
    isFirstLaunch: Boolean?,
    shownForVersion: String?,
    currentVersion: String
): Boolean = isFirstLaunch == false &&
    shownForVersion != null &&
    shownForVersion != currentVersion

/**
 * Окно с изменениями версии. Показывается один раз на версию — и на свежей установке,
 * и после обновления (см. `AppPreferences.whatsNewShownFor`).
 *
 * Содержимое прокручиваемое: на среднем экране помещается целиком, но при крупном
 * системном шрифте или маленьком экране кнопка иначе уехала бы за границу.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    item(
                        stringResource(R.string.whats_new_fuel_reset_title),
                        stringResource(R.string.whats_new_fuel_reset_body)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    item(
                        stringResource(R.string.whats_new_works_title),
                        stringResource(R.string.whats_new_works_body)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    item(
                        stringResource(R.string.whats_new_to_consumables_title),
                        stringResource(R.string.whats_new_to_consumables_body)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    item(
                        stringResource(R.string.whats_new_hidden_parts_title),
                        stringResource(R.string.whats_new_hidden_parts_body)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_confirm))
            }
        }
    )
}

/** Полужирный заголовок пункта и продолжение в той же строке */
private fun item(title: String, body: String?): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
    if (body != null) {
        append(" — ")
        append(body)
    }
}
