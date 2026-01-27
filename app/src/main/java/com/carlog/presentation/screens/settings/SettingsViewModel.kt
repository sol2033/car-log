package com.carlog.presentation.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.carlog.data.backup.BackupInfo
import com.carlog.data.backup.BackupScheduler
import com.carlog.data.backup.CloudBackupManager
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import com.carlog.util.DatabaseBackup
import com.carlog.util.ImportStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class CloudBackupState(
    val isConfigured: Boolean = false,
    val cloudFolderName: String = "",
    val lastBackupTimestamp: Long = 0L,
    val autoBackupEnabled: Boolean = false,
    val autoDeleteOldBackups: Boolean = true,
    val isLoading: Boolean = false,
    val hasPendingBackup: Boolean = false, // Есть ожидающая задача
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val database: CarLogDatabase,
    private val cloudBackupManager: CloudBackupManager,
    private val backupScheduler: BackupScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val databaseBackup = DatabaseBackup(database, context)
    
    private val backupPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    
    private val _cloudBackupState = MutableStateFlow(CloudBackupState())
    val cloudBackupState: StateFlow<CloudBackupState> = _cloudBackupState.asStateFlow()
    
    init {
        loadCloudBackupState()
        observeBackupWorkStatus()
    }
    
    private fun observeBackupWorkStatus() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow("auto_backup_work")
                .collect { workInfos ->
                    val hasPending = workInfos.any { 
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED 
                    }
                    _cloudBackupState.value = _cloudBackupState.value.copy(
                        hasPendingBackup = hasPending
                    )
                }
        }
    }
    
    private fun loadCloudBackupState() {
        val cloudFolderUri = backupPrefs.getString("cloud_folder_uri", null)
        val cloudFolderName = backupPrefs.getString("cloud_folder_name", "")
        val lastBackupTimestamp = backupPrefs.getLong("last_backup_timestamp", 0L)
        val autoBackupEnabled = backupPrefs.getBoolean("auto_backup_enabled", false)
        val autoDeleteOldBackups = backupPrefs.getBoolean("auto_delete_old_backups", true)
        
        _cloudBackupState.value = CloudBackupState(
            isConfigured = !cloudFolderUri.isNullOrEmpty(),
            cloudFolderName = cloudFolderName ?: "",
            lastBackupTimestamp = lastBackupTimestamp,
            autoBackupEnabled = autoBackupEnabled,
            autoDeleteOldBackups = autoDeleteOldBackups
        )
    }

    // === Тема ===

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)
        }
    }

    // === Валюта ===

    val currency: StateFlow<Currency> = appPreferences.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.RUB)

    fun setCurrency(currency: Currency) {
        viewModelScope.launch {
            appPreferences.setCurrency(currency)
        }
    }

    // === Язык ===

    val language: StateFlow<String> = appPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ru")

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            appPreferences.setLanguage(languageCode)
        }
    }
    
    // Для получения корутины, которая завершится после сохранения языка
    suspend fun setLanguageAndWait(languageCode: String) {
        appPreferences.setLanguage(languageCode)
    }
    
    // === Экспорт/Импорт ===
    
    suspend fun exportDatabase(outputStream: OutputStream): Result<Unit> {
        return databaseBackup.exportToJson(outputStream)
    }
    
    suspend fun importDatabase(inputStream: InputStream): Result<ImportStats> {
        return databaseBackup.importFromJson(inputStream)
    }
    
    fun generateBackupFileName(): String {
        return databaseBackup.generateBackupFileName()
    }
    
    // === Облачное резервное копирование ===
    
    /**
     * Сохранить URI выбранной папки облака
     */
    fun saveCloudFolder(uri: Uri, displayName: String) {
        // Сохраняем постоянный доступ к папке
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        
        backupPrefs.edit()
            .putString("cloud_folder_uri", uri.toString())
            .putString("cloud_folder_name", displayName)
            .apply()
        
        loadCloudBackupState()
    }
    
    /**
     * Включить/выключить автоматическое резервное копирование
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        backupPrefs.edit()
            .putBoolean("auto_backup_enabled", enabled)
            .apply()
        
        if (!enabled) {
            backupScheduler.cancelScheduledBackup()
        }
        
        loadCloudBackupState()
    }
    
    /**
     * Включить/выключить автоматическое удаление старых копий
     */
    fun setAutoDeleteOldBackups(enabled: Boolean) {
        backupPrefs.edit()
            .putBoolean("auto_delete_old_backups", enabled)
            .apply()
        
        loadCloudBackupState()
    }
    
    /**
     * Создать резервную копию вручную
     */
    fun createManualBackup() {
        viewModelScope.launch {
            _cloudBackupState.value = _cloudBackupState.value.copy(isLoading = true, error = null)
            
            backupScheduler.triggerManualBackup()
            
            _cloudBackupState.value = _cloudBackupState.value.copy(isLoading = false)
        }
    }
    
    /**
     * Получить список доступных резервных копий
     */
    suspend fun getAvailableBackups(): Result<List<BackupInfo>> {
        val cloudFolderUri = backupPrefs.getString("cloud_folder_uri", null)
            ?: return Result.failure(Exception("Папка облака не настроена"))
        
        return cloudBackupManager.getAvailableBackups(Uri.parse(cloudFolderUri))
    }
    
    /**
     * Восстановить данные из резервной копии
     */
    suspend fun restoreFromBackup(backupUri: Uri): Result<Unit> {
        // Закрываем базу данных
        database.close()
        
        return cloudBackupManager.restoreBackup(backupUri)
    }
    
    /**
     * Отключить облачное резервное копирование
     */
    fun disconnectCloudBackup() {
        val cloudFolderUri = backupPrefs.getString("cloud_folder_uri", null)
        if (cloudFolderUri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(cloudFolderUri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
        
        backupPrefs.edit()
            .remove("cloud_folder_uri")
            .remove("cloud_folder_name")
            .putBoolean("auto_backup_enabled", false)
            .apply()
        
        backupScheduler.cancelScheduledBackup()
        loadCloudBackupState()
    }
}
