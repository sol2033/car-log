package com.carlog.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.carlog.data.backup.BackupInfo
import com.carlog.data.backup.BackupScheduler
import com.carlog.data.backup.CloudBackupManager
import com.carlog.data.backup.YandexAuthManager
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import com.carlog.util.DatabaseBackup
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
    val isConnected: Boolean = false,
    val lastBackupTimestamp: Long = 0L,
    val autoBackupEnabled: Boolean = false,
    val autoDeleteOldBackups: Boolean = true,
    val isLoading: Boolean = false,
    val hasPendingBackup: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val database: CarLogDatabase,
    private val cloudBackupManager: CloudBackupManager,
    private val backupScheduler: BackupScheduler,
    private val yandexAuthManager: YandexAuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val databaseBackup = DatabaseBackup(database, context)
    private val backupPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    private val _cloudBackupState = MutableStateFlow(CloudBackupState())
    val cloudBackupState: StateFlow<CloudBackupState> = _cloudBackupState.asStateFlow()

    init {
        loadCloudBackupState()
        observeBackupWorkStatus()
        observeYandexAuthState()
    }

    private fun observeBackupWorkStatus() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow("auto_backup_work")
                .collect { workInfos ->
                    val hasPending = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                    }
                    _cloudBackupState.value = _cloudBackupState.value.copy(hasPendingBackup = hasPending)
                }
        }
    }

    private fun observeYandexAuthState() {
        viewModelScope.launch {
            yandexAuthManager.isConnectedFlow.collect {
                loadCloudBackupState()
            }
        }
    }

    private fun loadCloudBackupState() {
        val lastBackupTimestamp = backupPrefs.getLong("last_backup_timestamp", 0L)
        val autoBackupEnabled = backupPrefs.getBoolean("auto_backup_enabled", false)
        val autoDeleteOldBackups = backupPrefs.getBoolean("auto_delete_old_backups", true)

        _cloudBackupState.value = _cloudBackupState.value.copy(
            isConnected = yandexAuthManager.isConnected(),
            lastBackupTimestamp = lastBackupTimestamp,
            autoBackupEnabled = autoBackupEnabled,
            autoDeleteOldBackups = autoDeleteOldBackups
        )
    }

    // === Тема ===

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    // === Валюта ===

    val currency: StateFlow<Currency> = appPreferences.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.RUB)

    fun setCurrency(currency: Currency) {
        viewModelScope.launch { appPreferences.setCurrency(currency) }
    }

    // === Язык ===

    val language: StateFlow<String> = appPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ru")

    fun setLanguage(languageCode: String) {
        viewModelScope.launch { appPreferences.setLanguage(languageCode) }
    }

    suspend fun setLanguageAndWait(languageCode: String) {
        appPreferences.setLanguage(languageCode)
    }

    // === Экспорт/Импорт (ручной бэкап) ===

    suspend fun exportDatabase(outputStream: OutputStream): Result<Unit> {
        return databaseBackup.exportToJson(outputStream)
    }

    suspend fun importDatabase(inputStream: InputStream): Result<Int> {
        return databaseBackup.importFromJson(inputStream)
    }

    fun generateBackupFileName(): String = databaseBackup.generateBackupFileName()

    // === Яндекс.Диск ===

    /** URL для открытия браузера — начало OAuth-авторизации */
    fun getYandexAuthUrl(): String = yandexAuthManager.getAuthUrl()

    /**
     * Включить/выключить автоматическое резервное копирование
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        backupPrefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        if (!enabled) backupScheduler.cancelScheduledBackup()
        loadCloudBackupState()
    }

    /**
     * Включить/выключить автоудаление старых копий
     */
    fun setAutoDeleteOldBackups(enabled: Boolean) {
        backupPrefs.edit().putBoolean("auto_delete_old_backups", enabled).apply()
        loadCloudBackupState()
    }

    /**
     * Создать резервную копию вручную (по кнопке)
     */
    fun createManualBackup() {
        viewModelScope.launch {
            _cloudBackupState.value = _cloudBackupState.value.copy(isLoading = true, error = null)
            backupScheduler.triggerManualBackup()
            _cloudBackupState.value = _cloudBackupState.value.copy(isLoading = false)
        }
    }

    /**
     * Получить список резервных копий с Яндекс.Диска
     */
    suspend fun getAvailableBackups(): Result<List<BackupInfo>> {
        return cloudBackupManager.getAvailableBackups()
    }

    /**
     * Восстановить данные из резервной копии
     * @param remotePath путь на Яндекс.Диске, например "disk:/CarLog/CarLog_Backup_xxx.zip"
     */
    suspend fun restoreFromBackup(remotePath: String): Result<Unit> {
        database.close()
        return cloudBackupManager.restoreBackup(remotePath)
    }

    /**
     * Отключить Яндекс.Диск
     */
    fun disconnectCloudBackup() {
        yandexAuthManager.clearToken()
        backupPrefs.edit().putBoolean("auto_backup_enabled", false).apply()
        backupScheduler.cancelScheduledBackup()
        loadCloudBackupState()
    }
}
