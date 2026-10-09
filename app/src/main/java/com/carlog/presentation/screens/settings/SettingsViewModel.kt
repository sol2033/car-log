package com.carlog.presentation.screens.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.carlog.BuildConfig
import com.carlog.data.backup.BackupEventLog
import com.carlog.data.backup.BackupInfo
import com.carlog.data.backup.BackupLogEntry
import com.carlog.data.backup.BackupLogStore
import com.carlog.data.backup.BackupPrefs
import com.carlog.data.backup.BackupScheduler
import com.carlog.data.backup.BatteryOptimizationHintPolicy
import com.carlog.data.backup.CloudBackupManager
import com.carlog.data.backup.YandexAuthManager
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.preferences.AppPreferences
import com.carlog.data.preferences.Currency
import com.carlog.data.preferences.ThemeMode
import com.carlog.util.BatteryOptimizationHelper
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
    val wifiOnly: Boolean = false,
    val isLoading: Boolean = false,
    val hasPendingBackup: Boolean = false,
    /** Сбои повторяются и приложение под ограничением Doze — предлагаем снять его */
    val showBatteryHint: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val database: CarLogDatabase,
    private val cloudBackupManager: CloudBackupManager,
    private val backupScheduler: BackupScheduler,
    private val yandexAuthManager: YandexAuthManager,
    private val backupLogStore: BackupLogStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val databaseBackup = DatabaseBackup(database, context)
    private val backupPrefs = context.getSharedPreferences(BackupPrefs.NAME, Context.MODE_PRIVATE)

    private val _cloudBackupState = MutableStateFlow(CloudBackupState())
    val cloudBackupState: StateFlow<CloudBackupState> = _cloudBackupState.asStateFlow()

    init {
        loadCloudBackupState()
        observeBackupWorkStatus()
        observeManualBackupWorkStatus()
        observeYandexAuthState()
    }

    private fun observeBackupWorkStatus() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(BackupScheduler.BACKUP_WORK_NAME)
                .collect { workInfos ->
                    val hasPending = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                    }
                    _cloudBackupState.value = _cloudBackupState.value.copy(hasPendingBackup = hasPending)
                }
        }
    }

    /**
     * Прогресс ручного бэкапа берём у самой задачи.
     *
     * Раньше `isLoading` включался и тут же выключался вокруг постановки задачи в очередь:
     * кнопка блокировалась на микросекунды, видимой реакции не было — и человек жал ещё раз,
     * плодя параллельные заливки.
     */
    private fun observeManualBackupWorkStatus() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(BackupScheduler.MANUAL_BACKUP_WORK_NAME)
                .collect { workInfos ->
                    val isRunning = workInfos.any { !it.state.isFinished }
                    _cloudBackupState.value = _cloudBackupState.value.copy(isLoading = isRunning)
                    // Задача завершилась — подтянуть свежую дату копии и статус подсказки
                    if (!isRunning) loadCloudBackupState()
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
        val lastBackupTimestamp = backupPrefs.getLong(BackupPrefs.KEY_LAST_BACKUP, 0L)
        val autoBackupEnabled = backupPrefs.getBoolean(BackupPrefs.KEY_AUTO_BACKUP, false)
        val autoDeleteOldBackups = backupPrefs.getBoolean(BackupPrefs.KEY_AUTO_DELETE, true)
        val wifiOnly = backupPrefs.getBoolean(BackupPrefs.KEY_WIFI_ONLY, false)

        _cloudBackupState.value = _cloudBackupState.value.copy(
            isConnected = yandexAuthManager.isConnected(),
            lastBackupTimestamp = lastBackupTimestamp,
            autoBackupEnabled = autoBackupEnabled,
            autoDeleteOldBackups = autoDeleteOldBackups,
            wifiOnly = wifiOnly,
            showBatteryHint = shouldShowBatteryHint()
        )
    }

    /**
     * Подсказку про оптимизацию батареи показываем не всем подряд, а только когда сбои
     * повторяются и ограничение на приложении действительно стоит.
     */
    private fun shouldShowBatteryHint(): Boolean = BatteryOptimizationHintPolicy.shouldShow(
        consecutiveFailures = BackupEventLog.consecutiveFailures(backupLogStore.entries()),
        alreadyExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
        dismissed = backupPrefs.getBoolean(BackupPrefs.KEY_BATTERY_HINT_DISMISSED, false)
    )

    /**
     * Перечитать состояние экрана. Нужно при возврате из системного диалога разрешения:
     * подсказка должна исчезнуть сразу, а не при следующем открытии настроек.
     */
    fun refreshCloudBackupState() = loadCloudBackupState()

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

    // Учёт расхода масла: выключение прячет интерфейс, данные остаются
    val oilConsumptionEnabled: StateFlow<Boolean> = appPreferences.oilConsumptionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setOilConsumptionEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setOilConsumptionEnabled(enabled) }
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
        backupPrefs.edit().putBoolean(BackupPrefs.KEY_AUTO_BACKUP, enabled).apply()
        if (!enabled) backupScheduler.cancelScheduledBackup()
        loadCloudBackupState()
    }

    /**
     * Включить/выключить автоудаление старых копий
     */
    fun setAutoDeleteOldBackups(enabled: Boolean) {
        backupPrefs.edit().putBoolean(BackupPrefs.KEY_AUTO_DELETE, enabled).apply()
        loadCloudBackupState()
    }

    /**
     * Ограничить автоматические копии домашним Wi-Fi.
     * На ручную копию не влияет: её человек запускает осознанно.
     */
    fun setWifiOnly(enabled: Boolean) {
        backupPrefs.edit().putBoolean(BackupPrefs.KEY_WIFI_ONLY, enabled).apply()
        loadCloudBackupState()
    }

    /**
     * Создать резервную копию вручную (по кнопке).
     * Состояние кнопки ведёт [observeManualBackupWorkStatus] по реальному статусу задачи.
     */
    fun createManualBackup() {
        _cloudBackupState.value = _cloudBackupState.value.copy(error = null)
        backupScheduler.triggerManualBackup()
    }

    // === Журнал резервного копирования ===

    /** Последние события бэкапа — от новых к старым */
    fun backupLogEntries(): List<BackupLogEntry> = backupLogStore.entries()

    /** Текст для отправки в поддержку: только техническая сводка, без данных пользователя */
    fun backupLogForSharing(): String = backupLogStore.formatForSharing(
        appVersion = BuildConfig.VERSION_NAME,
        device = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"
    )

    /** Пользователь закрыл подсказку про оптимизацию батареи — больше не показываем */
    fun dismissBatteryHint() {
        backupPrefs.edit().putBoolean(BackupPrefs.KEY_BATTERY_HINT_DISMISSED, true).apply()
        loadCloudBackupState()
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
        // База закрывается внутри, уже после того как всё скачано: при обрыве связи
        // на середине приложение остаётся с рабочей базой и прежними данными
        return cloudBackupManager.restoreBackup(remotePath)
    }

    /**
     * Отключить Яндекс.Диск
     */
    fun disconnectCloudBackup() {
        yandexAuthManager.clearToken()
        backupPrefs.edit().putBoolean(BackupPrefs.KEY_AUTO_BACKUP, false).apply()
        backupScheduler.cancelScheduledBackup()
        loadCloudBackupState()
    }
}
