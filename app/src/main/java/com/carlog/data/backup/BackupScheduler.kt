package com.carlog.data.backup

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планировщик резервного копирования
 * Оптимизированный для батареи: создаёт задачу через 5 минут после последнего изменения
 * Использует debouncing - каждое новое изменение сбрасывает таймер
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BACKUP_WORK_NAME = "auto_backup_work"
        private const val BACKUP_DELAY_MINUTES = 5L
    }

    private val _lastChangeTimestamp = MutableStateFlow(0L)
    val lastChangeTimestamp: StateFlow<Long> = _lastChangeTimestamp

    /**
     * Уведомить о том, что данные изменились
     * Сбрасывает таймер и запускает новую задачу через 5 минут
     */
    fun notifyDataChanged() {
        val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        val autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false)
        val cloudFolderUri = prefs.getString("cloud_folder_uri", null)
        
        // Проверяем, что автобэкап включен и папка настроена
        if (!autoBackupEnabled || cloudFolderUri.isNullOrEmpty()) {
            return
        }
        
        _lastChangeTimestamp.value = System.currentTimeMillis()
        
        // Создаём задачу с задержкой 5 минут
        // ExistingWorkPolicy.REPLACE отменяет предыдущую задачу (debouncing)
        val workRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(BACKUP_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Нужен интернет для облака
                    .setRequiresBatteryNotLow(true) // Не запускать при низком заряде
                    .build()
            )
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                BACKUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Заменяем предыдущую задачу
                workRequest
            )
    }

    /**
     * Отменить запланированное копирование
     */
    fun cancelScheduledBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(BACKUP_WORK_NAME)
    }

    /**
     * Запустить копирование немедленно (вручную)
     */
    fun triggerManualBackup() {
        val workRequest = OneTimeWorkRequestBuilder<ManualBackupWorker>()
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
