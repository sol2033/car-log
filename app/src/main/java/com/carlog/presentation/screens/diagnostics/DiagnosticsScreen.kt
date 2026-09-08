package com.carlog.presentation.screens.diagnostics

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.carlog.R
import com.carlog.data.diagnostics.DiagnosticEntry
import com.carlog.data.diagnostics.DiagnosticLog
import com.carlog.data.diagnostics.DiagnosticSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран диагностики: что приложение записало о собственных сбоях, и как отправить это
 * разработчику.
 *
 * Появился по следам разбора реальной жалобы, где вся информация о проблеме сводилась
 * к одному слову в уведомлении. Журнал ведётся сам, пользователю остаётся сохранить файл
 * и выбрать, как его переслать.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(DiagnosticLog.entries().asReversed()) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Сохранение файла туда, куда укажет пользователь
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(DiagnosticLog.exportText().toByteArray())
            } ?: error("no stream")
        }.isSuccess

        if (saved) {
            showContactDialog = true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.diagnostics_save_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text(stringResource(R.string.diagnostics_clear))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { saveLauncher.launch(DiagnosticLog.EXPORT_FILE_NAME) },
                    enabled = entries.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_save))
                }
                OutlinedButton(
                    onClick = { showContactDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_contact))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.diagnostics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Без key намеренно: две записи журнала могут прийтись на одну
                    // миллисекунду, а дубликат ключа роняет LazyColumn. Список
                    // read-only и не переупорядочивается — выигрыша от ключей нет
                    items(entries) { entry -> DiagnosticRow(entry) }
                }
            }
        }
    }

    if (showContactDialog) {
        ContactDialog(
            onEmail = {
                shareLogByEmail(context)
                showContactDialog = false
            },
            onDrive2 = {
                openUrl(context, context.getString(R.string.support_drive2_url))
                showContactDialog = false
            },
            onDismiss = { showContactDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.diagnostics_clear_title)) },
            text = { Text(stringResource(R.string.diagnostics_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    DiagnosticLog.clear()
                    entries = emptyList()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.diagnostics_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Выбор способа связи. Письмо уходит с уже прикреплённым файлом и заготовкой текста —
 * человеку остаётся дописать, что случилось.
 */
@Composable
private fun ContactDialog(
    onEmail: () -> Unit,
    onDrive2: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Email, contentDescription = null) },
        title = { Text(stringResource(R.string.diagnostics_contact_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.diagnostics_contact_message))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.diagnostics_contact_email)) },
                    supportingContent = { Text(stringResource(R.string.support_email)) },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onEmail)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.diagnostics_contact_drive2)) },
                    supportingContent = { Text(stringResource(R.string.diagnostics_contact_drive2_hint)) },
                    leadingContent = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onDrive2)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_log_close)) }
        }
    )
}

@Composable
private fun DiagnosticRow(entry: DiagnosticEntry) {
    val color = when (entry.severity) {
        DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
        DiagnosticSeverity.CRASH -> MaterialTheme.colorScheme.error
    }
    Column {
        Text(
            text = "${formatTimestamp(entry.timestamp)} · ${entry.area.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = entry.event,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
        entry.details?.let { details ->
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Письмо с прикреплённым файлом журнала. Файл отдаётся через FileProvider. */
private fun shareLogByEmail(context: android.content.Context) {
    val file = DiagnosticLog.exportToFile()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.support_email)))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_email_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.diagnostics_email_body))
        if (file != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.diagnostics_contact_email))
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.diagnostics_no_email_app),
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
