package com.carlog.presentation.screens.backup

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.presentation.screens.settings.CloudBackupSection
import com.carlog.presentation.screens.settings.SettingsSection
import com.carlog.presentation.screens.settings.SettingsViewModel
import com.carlog.util.AppRestart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRestoreBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val cloudBackupState by viewModel.cloudBackupState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    
    // Лончер для экспорта
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                isExporting = true
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val result = viewModel.exportDatabase(outputStream)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Данные успешно экспортированы", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ошибка экспорта: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }
    
    // Лончер для импорта
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val result = viewModel.importDatabase(inputStream)
                        if (result.isSuccess) {
                            val photosRestored = result.getOrNull() ?: 0
                            val message = if (photosRestored > 0) {
                                "Данные успешно импортированы! Восстановлено фотографий: $photosRestored. Перезапуск..."
                            } else {
                                "Данные успешно импортированы! Перезапуск..."
                            }
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                            // Полный перезапуск процесса — БД была подменена под работающим приложением
                            AppRestart.restart(context)
                        } else {
                            Toast.makeText(context, "Ошибка импорта: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Резервные сохранения") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // === Облачное резервное копирование (Яндекс.Диск) ===
            CloudBackupSection(
                cloudBackupState = cloudBackupState,
                onConnectYandex = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.getYandexAuthUrl()))
                    context.startActivity(intent)
                },
                onAutoBackupToggle = { enabled ->
                    viewModel.setAutoBackupEnabled(enabled)
                },
                onAutoDeleteToggle = { enabled ->
                    viewModel.setAutoDeleteOldBackups(enabled)
                },
                onManualBackup = {
                    viewModel.createManualBackup()
                },
                onRestoreBackup = {
                    onNavigateToRestoreBackup()
                },
                onDisconnect = {
                    viewModel.disconnectCloudBackup()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // === Ручное резервное копирование ===
            SettingsSection(title = stringResource(R.string.settings_backup_title))
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_data)) },
                supportingContent = { Text(stringResource(R.string.settings_export_description)) },
                leadingContent = {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = !isExporting) {
                    exportLauncher.launch(viewModel.generateBackupFileName())
                }
            )
            
            if (isExporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import_data)) },
                supportingContent = { Text(stringResource(R.string.settings_import_description)) },
                leadingContent = {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = !isImporting) {
                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }
            )
            
            if (isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}
