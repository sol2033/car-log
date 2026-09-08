package com.carlog.presentation.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlog.R
import com.carlog.data.backup.BackupLogEntry
import com.carlog.data.backup.BackupOutcome
import com.carlog.data.backup.BackupTrigger
import com.carlog.data.backup.messageRes
import com.carlog.util.BatteryOptimizationHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudBackupSection(
    cloudBackupState: CloudBackupState,
    onConnectYandex: () -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit,
    onAutoDeleteToggle: (Boolean) -> Unit,
    onWifiOnlyToggle: (Boolean) -> Unit,
    onManualBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDismissBatteryHint: () -> Unit,
    backupLog: () -> List<BackupLogEntry>,
    backupLogText: () -> String,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    SettingsSection(title = "☁️ Облачное резервное копирование")

    if (!cloudBackupState.isConnected) {
        // Яндекс.Диск не подключён
        ListItem(
            headlineContent = { Text("Яндекс.Диск не подключён") },
            supportingContent = {
                Text(
                    "Подключите Яндекс.Диск для автоматического сохранения " +
                    "данных и фотографий в облако",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )

        Button(
            onClick = onConnectYandex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Подключить Яндекс.Диск")
        }

        Text(
            text = "Откроется браузер — войдите в аккаунт Яндекса и нажмите «Разрешить». " +
                   "После этого вернитесь в приложение.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    } else {
        // Яндекс.Диск подключён
        ListItem(
            headlineContent = {
                Text("Яндекс.Диск подключён", fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Column {
                    Text(
                        if (cloudBackupState.lastBackupTimestamp > 0) {
                            "Последнее сохранение: ${formatTimestamp(cloudBackupState.lastBackupTimestamp)}"
                        } else {
                            "Резервных копий пока нет"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (cloudBackupState.hasPendingBackup) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⏳ Ожидание интернета для сохранения...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            leadingContent = {
                Icon(
                    if (cloudBackupState.hasPendingBackup) Icons.Default.CloudQueue
                    else Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = if (cloudBackupState.hasPendingBackup)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        )

        // Подсказка про оптимизацию батареи — только если сбои повторяются
        if (cloudBackupState.showBatteryHint) {
            BatteryOptimizationHintCard(
                onAllow = {
                    if (!BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_battery_hint_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onDismiss = onDismissBatteryHint
            )
        }

        // Автоматическое резервное копирование
        ListItem(
            headlineContent = { Text("Автоматическое сохранение") },
            supportingContent = {
                Text(
                    "Через 5 минут после изменения данных",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = cloudBackupState.autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) showAutoBackupDialog = true
                        else onAutoBackupToggle(false)
                    }
                )
            },
            modifier = Modifier.clickable {
                if (!cloudBackupState.autoBackupEnabled) showAutoBackupDialog = true
                else onAutoBackupToggle(false)
            }
        )

        // Автоудаление старых копий
        ListItem(
            headlineContent = { Text("Автоудаление старых копий") },
            supportingContent = {
                Text(
                    "Хранить 3 последние копии",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = cloudBackupState.autoDeleteOldBackups,
                    onCheckedChange = onAutoDeleteToggle
                )
            },
            modifier = Modifier.clickable {
                onAutoDeleteToggle(!cloudBackupState.autoDeleteOldBackups)
            }
        )

        // Только по Wi-Fi (на ручную копию не влияет)
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_wifi_only_title)) },
            supportingContent = {
                Text(
                    stringResource(R.string.backup_wifi_only_subtitle),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = cloudBackupState.wifiOnly,
                    onCheckedChange = onWifiOnlyToggle
                )
            },
            modifier = Modifier.clickable {
                onWifiOnlyToggle(!cloudBackupState.wifiOnly)
            }
        )

        // Кнопки действий
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onManualBackup,
                enabled = !cloudBackupState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Создать копию")
            }

            OutlinedButton(
                onClick = onRestoreBackup,
                enabled = !cloudBackupState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Восстановить", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (cloudBackupState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // Журнал: с чем идти в поддержку, если копии срываются
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_log_open)) },
            leadingContent = {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.clickable { showLogDialog = true }
        )

        TextButton(
            onClick = { showDisconnectDialog = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text("Отключить Яндекс.Диск")
        }
    }

    if (showLogDialog) {
        BackupLogDialog(
            entries = remember { backupLog() },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_log_share_title))
                    putExtra(Intent.EXTRA_TEXT, backupLogText())
                }
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.backup_log_share))
                )
            },
            onDismiss = { showLogDialog = false }
        )
    }

    // Диалог подтверждения включения автобэкапа
    if (showAutoBackupDialog) {
        AlertDialog(
            onDismissRequest = { showAutoBackupDialog = false },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
            title = { Text("Автоматическое сохранение") },
            text = {
                Column {
                    Text("Включить автоматическое резервное копирование?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Резервная копия будет создаваться через 5 минут после " +
                        "каждого изменения данных при наличии интернета.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAutoBackupToggle(true)
                    showAutoBackupDialog = false
                    Toast.makeText(context, "Автоматическое сохранение включено", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Включить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoBackupDialog = false }) {
                    Text("Пока нет")
                }
            }
        )
    }

    // Диалог подтверждения отключения
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Отключить Яндекс.Диск?") },
            text = {
                Text(
                    "Автоматическое сохранение будет отключено. " +
                    "Существующие резервные копии на Яндекс.Диске сохранятся."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnect()
                        showDisconnectDialog = false
                        Toast.makeText(context, "Яндекс.Диск отключён", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Отключить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Подсказка «разрешите работу в фоне без ограничений» (оптимизация батареи Android).
 * Показывается только тем, у кого копии реально срываются подряд и ограничение ещё стоит, —
 * см. `BatteryOptimizationHintPolicy`.
 */
@Composable
private fun BatteryOptimizationHintCard(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.backup_battery_hint_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.backup_battery_hint_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) {
                    Text(stringResource(R.string.backup_battery_hint_allow))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.backup_battery_hint_dismiss))
                }
            }
        }
    }
}

/** Журнал последних событий бэкапа: что, когда и чем закончилось. */
@Composable
private fun BackupLogDialog(
    entries: List<BackupLogEntry>,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text(stringResource(R.string.backup_log_title)) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.backup_log_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Без key намеренно: две записи журнала могут прийтись на одну
                    // миллисекунду, а дубликат ключа роняет LazyColumn. Список
                    // read-only и не переупорядочивается — выигрыша от ключей нет
                    items(entries) { entry -> BackupLogRow(entry) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.backup_log_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_log_close))
            }
        }
    )
}

@Composable
private fun BackupLogRow(entry: BackupLogEntry) {
    val outcomeColor = when (entry.outcome) {
        BackupOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
        BackupOutcome.RETRY -> MaterialTheme.colorScheme.tertiary
        BackupOutcome.FAILURE -> MaterialTheme.colorScheme.error
    }
    val outcomeText = stringResource(
        when (entry.outcome) {
            BackupOutcome.SUCCESS -> R.string.backup_log_outcome_success
            BackupOutcome.RETRY -> R.string.backup_log_outcome_retry
            BackupOutcome.FAILURE -> R.string.backup_log_outcome_failure
        }
    )
    val triggerText = stringResource(
        when (entry.trigger) {
            BackupTrigger.AUTO -> R.string.backup_log_trigger_auto
            BackupTrigger.MANUAL -> R.string.backup_log_trigger_manual
            BackupTrigger.CATCH_UP -> R.string.backup_log_trigger_catch_up
        }
    )

    Column {
        Text(
            text = "${formatLogTimestamp(entry.timestamp)} · $triggerText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = outcomeText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = outcomeColor
        )
        entry.errorType?.let { type ->
            Text(
                text = stringResource(type.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Технический хвост: на каком шаге и до какого хоста не достучались
        if (entry.phase != null || entry.host != null) {
            Text(
                text = listOfNotNull(entry.phase?.name, entry.host).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.outcome == BackupOutcome.SUCCESS && entry.uploadedMediaCount > 0) {
            Text(
                text = stringResource(
                    R.string.backup_log_media_uploaded,
                    entry.uploadedMediaCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatLogTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
