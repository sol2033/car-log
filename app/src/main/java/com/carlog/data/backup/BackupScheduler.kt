package com.carlog.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планировщик резервного копирования.
 * Debouncing: каждое изменение данных перезапускает 5-минутный таймер.
 * AutoBackupWorker требует интернет — загружает ZIP на Яндекс.Диск.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val yandexAuthManager: YandexAuthManager
) {
    companion object {
        private const val BACKUP_WORK_NAME = "auto_backup_work"
        private const val BACKUP_DELAY_MINUTES = 5L
    }

    private val _lastChangeTimestamp = MutableStateFlow(0L)
    val lastChangeTimestamp: StateFlow<Long> = _lastChangeTimestamp

    /**
     * Вызывается после каждого изменения данных.
     * Запускает/перезапускает задачу бэкапа через 5 минут.
     */
    fun notifyDataChanged() {
        val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        val autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false)

        if (!autoBackupEnabled || !yandexAuthManager.isConnected()) return

        _lastChangeTimestamp.value = System.currentTimeMillis()

        val workRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(BACKUP_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Нужен интернет для Яндекс.Диска
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(BACKUP_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun cancelScheduledBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(BACKUP_WORK_NAME)
    }

    /** Запустить бэкап немедленно (по кнопке пользователя) */
    fun triggerManualBackup() {
        val workRequest = OneTimeWorkRequestBuilder<ManualBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
