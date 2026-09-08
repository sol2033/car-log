package com.carlog.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планировщик резервного копирования.
 * Debouncing: каждое изменение данных перезапускает 5-минутный таймер.
 * AutoBackupWorker требует интернет — загружает данные на Яндекс.Диск.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val yandexAuthManager: YandexAuthManager
) {
    companion object {
        const val BACKUP_WORK_NAME = "auto_backup_work"

        /**
         * У ручного бэкапа теперь тоже уникальное имя. Раньше здесь был обычный `enqueue`:
         * кнопка разблокировалась мгновенно, видимой реакции не было, человек нажимал ещё
         * раз — и параллельные заливки мешали друг другу до таймаута.
         */
        const val MANUAL_BACKUP_WORK_NAME = "manual_backup_work"

        private const val BACKUP_DELAY_MINUTES = 5L
    }

    private val prefs by lazy {
        context.getSharedPreferences(BackupPrefs.NAME, Context.MODE_PRIVATE)
    }

    /** Слушатель фьючерса короткий (проверить состояние и поставить задачу) — отдельный поток не нужен */
    private val directExecutor = Executor { it.run() }

    private val _lastChangeTimestamp = MutableStateFlow(0L)
    val lastChangeTimestamp: StateFlow<Long> = _lastChangeTimestamp

    /**
     * Вызывается после каждого изменения данных.
     * Запускает/перезапускает задачу бэкапа через 5 минут.
     */
    fun notifyDataChanged() {
        val autoBackupEnabled = prefs.getBoolean(BackupPrefs.KEY_AUTO_BACKUP, false)

        val now = System.currentTimeMillis()
        // Отметку ставим всегда: по ней догоняющий бэкап поймёт, что есть несохранённые
        // изменения, даже если автосохранение включат позже
        prefs.edit().putLong(BackupPrefs.KEY_LAST_CHANGE, now).apply()

        if (!autoBackupEnabled || !yandexAuthManager.isConnected()) return

        _lastChangeTimestamp.value = now

        scheduleAutoBackup(BackupTrigger.AUTO, TimeUnit.MINUTES.toMillis(BACKUP_DELAY_MINUTES))
    }

    fun cancelScheduledBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(BACKUP_WORK_NAME)
    }

    /** Запустить бэкап немедленно (по кнопке пользователя) */
    fun triggerManualBackup() {
        val request = OneTimeWorkRequestBuilder<ManualBackupWorker>()
            .setConstraints(
                // Ручной бэкап не ограничиваем настройкой «только по Wi-Fi»: человек
                // нажал кнопку осознанно, и задача, молча ждущая Wi-Fi, выглядела бы зависшей
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(triggerData(BackupTrigger.MANUAL))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BackupRetryPolicy.BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()

        // KEEP, а не REPLACE: повторное нажатие не должно обрывать идущую заливку
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_BACKUP_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Догоняющий бэкап при открытии приложения.
     *
     * Нужен потому, что фоновая задача может не доехать: прошивки Huawei, Xiaomi и им
     * подобных морозят фоновые процессы. Пока приложение открыто, помешать этому нельзя.
     */
    fun triggerCatchUpBackupIfNeeded() {
        val shouldRun = BackupCatchUpPolicy.shouldRun(
            autoBackupEnabled = prefs.getBoolean(BackupPrefs.KEY_AUTO_BACKUP, false),
            isConnected = yandexAuthManager.isConnected(),
            lastDataChange = prefs.getLong(BackupPrefs.KEY_LAST_CHANGE, 0L),
            lastBackup = prefs.getLong(BackupPrefs.KEY_LAST_BACKUP, 0L),
            now = System.currentTimeMillis()
        )
        if (!shouldRun) return

        scheduleAutoBackup(BackupTrigger.CATCH_UP, delayMs = 0)
    }

    /**
     * Ставит задачу автобэкапа, не обрывая уже идущую заливку.
     *
     * REPLACE во время активной заливки отменял бы её на полпути: потраченный трафик
     * впустую, а изменение, ради которого всё затевалось, осталось бы несохранённым.
     * Поэтому если задача уже выполняется — становимся в очередь за ней.
     */
    private fun scheduleAutoBackup(trigger: BackupTrigger, delayMs: Long) {
        val workManager = WorkManager.getInstance(context)
        val request = buildAutoBackupRequest(trigger, delayMs)
        val future = workManager.getWorkInfosForUniqueWork(BACKUP_WORK_NAME)

        future.addListener({
            val isRunning = runCatching { future.get() }
                .getOrNull()
                ?.any { it.state == WorkInfo.State.RUNNING } == true

            // Нюанс APPEND_OR_REPLACE: пристроенная задача — потомок в цепочке, и если идущая
            // заливка окончательно провалится (после всех повторов), потомок не выполнится.
            // Это приемлемо: пользователь в таком случае уже увидел уведомление об ошибке,
            // а несохранённое изменение подхватит догоняющий бэкап при следующем открытии
            // приложения или ближайшая правка данных.
            val policy = if (isRunning) {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            } else {
                ExistingWorkPolicy.REPLACE
            }
            runCatching { workManager.enqueueUniqueWork(BACKUP_WORK_NAME, policy, request) }
        }, directExecutor)
    }

    private fun buildAutoBackupRequest(trigger: BackupTrigger, delayMs: Long): OneTimeWorkRequest {
        val wifiOnly = prefs.getBoolean(BackupPrefs.KEY_WIFI_ONLY, false)

        return OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInputData(triggerData(trigger))
            // Разовый сетевой затык больше не хоронит копию: повторим с нарастающей паузой
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BackupRetryPolicy.BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
    }

    private fun triggerData(trigger: BackupTrigger): Data =
        Data.Builder().putString(BaseBackupWorker.KEY_TRIGGER, trigger.name).build()
}
