package com.carlog.presentation.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudBackupSection(
    cloudBackupState: CloudBackupState,
    onSelectFolder: (Uri, String) -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit,
    onAutoDeleteToggle: (Boolean) -> Unit,
    onManualBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    // Launcher для выбора папки облака
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Получаем имя папки для отображения
            val displayName = getDisplayNameFromUri(context, uri)
            onSelectFolder(uri, displayName)
            
            // Показываем диалог настройки автобэкапа
            showAutoBackupDialog = true
        }
    }
    
    SettingsSection(title = "☁️ Облачное резервное копирование")
    
    if (!cloudBackupState.isConfigured) {
        // Папка не настроена
        ListItem(
            headlineContent = { Text("Не настроено") },
            supportingContent = { 
                Text(
                    "Настройте облачную папку для автоматического\n" +
                    "сохранения данных с фотографиями",
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
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Выбрать папку облака")
        }
    } else {
        // Папка настроена
        ListItem(
            headlineContent = { Text("Папка: ${cloudBackupState.cloudFolderName}") },
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
                    // Показываем статус ожидающей задачи
                    if (cloudBackupState.hasPendingBackup) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⏳ Ожидание подключения к интернету...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            leadingContent = {
                Icon(
                    if (cloudBackupState.hasPendingBackup) Icons.Default.CloudQueue else Icons.Default.CloudDone,
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
                    onCheckedChange = onAutoBackupToggle
                )
            },
            modifier = Modifier.clickable { 
                onAutoBackupToggle(!cloudBackupState.autoBackupEnabled)
            }
        )
        
        // Автоудаление старых копий
        ListItem(
            headlineContent = { Text("Автоудаление старых копий") },
            supportingContent = { 
                Text(
                    "Хранить 3 последние копии (актуальная, прошлая, позапрошлая)",
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
            // Создать копию сейчас
            OutlinedButton(
                onClick = { onManualBackup() },
                enabled = !cloudBackupState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Создать копию")
            }
            
            // Восстановить
            OutlinedButton(
                onClick = { onRestoreBackup() },
                enabled = !cloudBackupState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Восстановить")
            }
        }
        
        if (cloudBackupState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        
        // Изменить папку / Отключить
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Изменить папку")
            }
            
            TextButton(
                onClick = { showDisconnectDialog = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Отключить")
            }
        }
    }
    
    // Диалог настройки автобэкапа после выбора папки
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
                        "Резервная копия будет создаваться автоматически через 5 минут " +
                        "после последнего изменения записей (добавление, редактирование, удаление).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAutoBackupToggle(true)
                        showAutoBackupDialog = false
                        Toast.makeText(
                            context,
                            "Автоматическое сохранение включено",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Включить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAutoBackupDialog = false
                        Toast.makeText(
                            context,
                            "Вы можете включить автосохранение позже в настройках",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Пока нет")
                }
            }
        )
    }
    
    // Диалог подтверждения отключения
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Отключить облачное копирование?") },
            text = {
                Text(
                    "Автоматическое сохранение будет отключено. " +
                    "Существующие резервные копии в облаке сохранятся."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnect()
                        showDisconnectDialog = false
                        Toast.makeText(
                            context,
                            "Облачное копирование отключено",
                            Toast.LENGTH_SHORT
                        ).show()
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

private fun getDisplayNameFromUri(context: android.content.Context, uri: Uri): String {
    // Пытаемся извлечь читаемое имя папки
    return try {
        val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        docFile?.name ?: "Облачная папка"
    } catch (e: Exception) {
        "Облачная папка"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
