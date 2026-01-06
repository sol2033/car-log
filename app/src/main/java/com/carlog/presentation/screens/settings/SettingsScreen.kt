package com.carlog.presentation.screens.settings

import android.app.Activity
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    
    // Лончер для экспорта
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
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
                            Toast.makeText(
                                context,
                                "Данные успешно импортированы! Перезапустите приложение.",
                                Toast.LENGTH_LONG
                            ).show()
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
                title = { Text(stringResource(R.string.settings_title)) },
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
            // === Тема ===
            SettingsSection(title = "Внешний вид")

            ThemeSettingItem(
                selectedTheme = themeMode,
                onThemeSelected = { viewModel.setThemeMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // === Язык ===
            SettingsSection(title = "Язык")

            LanguageSettingItem(
                selectedLanguage = language,
                onLanguageSelected = { newLanguage ->
                    scope.launch {
                        viewModel.setLanguageAndWait(newLanguage)
                        (context as? Activity)?.recreate()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // === Валюта ===
            SettingsSection(title = "Валюта")

            CurrencySettingItem(
                selectedCurrency = currency,
                onCurrencySelected = { viewModel.setCurrency(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // === Резервное копирование ===
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // === О приложении ===
            SettingsSection(title = "О приложении")

            InfoItem(label = stringResource(R.string.settings_version), value = "1.0.1")
            InfoItem(label = stringResource(R.string.settings_developer), value = stringResource(R.string.developer_name))
            
            LinkItem(
                label = stringResource(R.string.settings_drive2),
                url = "https://www.drive2.ru/users/sol2033/",
                icon = Icons.Default.DirectionsCar
            )
            
            LinkItem(
                label = stringResource(R.string.settings_repository),
                url = "https://github.com/sol2033/car-log",
                icon = Icons.Default.Code
            )
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun ThemeSettingItem(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        supportingContent = { 
            Text(when (selectedTheme) {
                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
            })
        },
        modifier = Modifier.clickable { expanded = true }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.theme_light)) },
            onClick = {
                onThemeSelected(ThemeMode.LIGHT)
                expanded = false
            },
            trailingIcon = {
                if (selectedTheme == ThemeMode.LIGHT) {
                    RadioButton(selected = true, onClick = null)
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.theme_dark)) },
            onClick = {
                onThemeSelected(ThemeMode.DARK)
                expanded = false
            },
            trailingIcon = {
                if (selectedTheme == ThemeMode.DARK) {
                    RadioButton(selected = true, onClick = null)
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.theme_system)) },
            onClick = {
                onThemeSelected(ThemeMode.SYSTEM)
                expanded = false
            },
            trailingIcon = {
                if (selectedTheme == ThemeMode.SYSTEM) {
                    RadioButton(selected = true, onClick = null)
                }
            }
        )
    }
}

@Composable
fun CurrencySettingItem(
    selectedCurrency: Currency,
    onCurrencySelected: (Currency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_currency)) },
        supportingContent = { Text("${selectedCurrency.symbol} - ${selectedCurrency.displayName}") },
        modifier = Modifier.clickable { expanded = true }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        Currency.values().forEach { currency ->
            DropdownMenuItem(
                text = { Text("${currency.symbol} - ${currency.displayName}") },
                onClick = {
                    onCurrencySelected(currency)
                    expanded = false
                },
                trailingIcon = {
                    if (selectedCurrency == currency) {
                        RadioButton(selected = true, onClick = null)
                    }
                }
            )
        }
    }
}

@Composable
fun LanguageSettingItem(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language_title)) },
        supportingContent = { 
            Text(when (selectedLanguage) {
                "ru" -> "🇷🇺 Русский"
                "en" -> "🇬🇧 English"
                else -> "🇷🇺 Русский"
            })
        },
        modifier = Modifier.clickable { expanded = true }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🇷🇺", fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    Text(stringResource(R.string.language_russian))
                }
            },
            onClick = {
                onLanguageSelected("ru")
                expanded = false
            },
            trailingIcon = {
                if (selectedLanguage == "ru") {
                    RadioButton(selected = true, onClick = null)
                }
            }
        )
        DropdownMenuItem(
            text = { 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🇬🇧", fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    Text("English")
                }
            },
            onClick = {
                onLanguageSelected("en")
                expanded = false
            },
            trailingIcon = {
                if (selectedLanguage == "en") {
                    RadioButton(selected = true, onClick = null)
                }
            }
        )
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) }
    )
}

@Composable
fun LinkItem(
    label: String,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val context = LocalContext.current
    
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { 
            Text(
                text = url,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
