package com.carlog.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.carlog.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker для автоматического резервного копирования.
 * Запускается через 5 минут после последнего изменения данных (при наличии интернета).
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cloudBackupManager: CloudBackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "backup_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val prefs = applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        // Если пользователь успел выключить автобэкап пока задача ждала
        if (!prefs.getBoolean("auto_backup_enabled", false)) return Result.success()

        val backupResult = cloudBackupManager.createBackup()

        return if (backupResult.isSuccess) {
            prefs.edit().putLong("last_backup_timestamp", System.currentTimeMillis()).apply()
            showSuccessNotification()
            Result.success()
        } else {
            showErrorNotification(backupResult.exceptionOrNull()?.message)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Резервное копирование",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Уведомления о создании резервных копий" }
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showSuccessNotification() {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle("Резервная копия создана")
            .setContentText("Данные успешно сохранены на Яндекс.Диск")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(message: String?) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle("Ошибка резервного копирования")
            .setContentText(message ?: "Не удалось создать резервную копию")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}

/**
 * Worker для ручного резервного копирования (кнопка "Создать копию").
 */
@HiltWorker
class ManualBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cloudBackupManager: CloudBackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "backup_channel"
        private const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()
        showProgressNotification()

        val prefs = applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        val backupResult = cloudBackupManager.createBackup()

        return if (backupResult.isSuccess) {
            prefs.edit().putLong("last_backup_timestamp", System.currentTimeMillis()).apply()
            showSuccessNotification()
            Result.success()
        } else {
            showErrorNotification(backupResult.exceptionOrNull()?.message)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Резервное копирование",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Уведомления о создании резервных копий" }
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification() {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle("Создание резервной копии")
            .setContentText("Загрузка на Яндекс.Диск...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showSuccessNotification() {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle("Резервная копия создана")
            .setContentText("Данные успешно сохранены на Яндекс.Диск")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(message: String?) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle("Ошибка резервного копирования")
            .setContentText(message ?: "Не удалось создать резервную копию")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
