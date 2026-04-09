package com.carlog.presentation.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudBackupSection(
    cloudBackupState: CloudBackupState,
    onConnectYandex: () -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit,
    onAutoDeleteToggle: (Boolean) -> Unit,
    onManualBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }

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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
