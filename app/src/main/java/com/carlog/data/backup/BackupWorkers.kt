package com.carlog.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.carlog.MainActivity
import com.carlog.data.diagnostics.DiagnosticArea
import com.carlog.data.diagnostics.DiagnosticLog
import com.carlog.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Locale

/**
 * Общая часть автоматического и ручного бэкапа.
 *
 * Ключевые отличия от версии до 1.2.3:
 * - временные сбои возвращают `Result.retry()`, а не `failure()`: разовый сетевой затык
 *   больше не оставляет пользователя без копии до следующего изменения данных;
 * - уведомление об ошибке показывается только когда попытки исчерпаны;
 * - в тексте уведомления — понятная фраза, а не `message` исключения (пользователь
 *   видел «timeout»);
 * - заливка идёт как foreground-задача, иначе прошивки Huawei и подобных морозят
 *   процесс посреди сокета.
 */
abstract class BaseBackupWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val cloudBackupManager: CloudBackupManager,
    private val backupLogStore: BackupLogStore
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "backup_channel"
        const val PROGRESS_CHANNEL_ID = "backup_progress_channel"
        const val PROGRESS_NOTIFICATION_ID = 1003

        /** Ключ входных данных: что запустило задачу (см. [BackupTrigger]) */
        const val KEY_TRIGGER = "trigger"

        /** Не чаще одного обновления уведомления в это время — при сотнях фото иначе перебор */
        private const val PROGRESS_THROTTLE_MS = 500L
    }

    /** Идентификатор итогового уведомления — у автоматического и ручного разные */
    protected abstract val resultNotificationId: Int

    /** Проверять ли, что автосохранение всё ещё включено */
    protected abstract val checksAutoBackupEnabled: Boolean

    /**
     * Контекст с языком, выбранным в приложении. `applicationContext` берёт язык системы,
     * и уведомление приходило бы не на том языке, на котором человек видит приложение.
     */
    private val localizedContext: Context by lazy {
        val languageCode = applicationContext
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("language", "ru") ?: "ru"
        val configuration = Configuration(applicationContext.resources.configuration)
        configuration.setLocale(Locale(languageCode))
        applicationContext.createConfigurationContext(configuration)
    }

    private val notificationManager: NotificationManager
        get() = applicationContext.getSystemService(NotificationManager::class.java)

    private var lastProgressUpdate = 0L

    override suspend fun doWork(): Result {
        createNotificationChannels()

        val prefs = applicationContext.getSharedPreferences(BackupPrefs.NAME, Context.MODE_PRIVATE)
        // Если пользователь успел выключить автобэкап пока задача ждала
        if (checksAutoBackupEnabled && !prefs.getBoolean(BackupPrefs.KEY_AUTO_BACKUP, false)) {
            return Result.success()
        }

        val trigger = runCatching {
            enumValueOf<BackupTrigger>(inputData.getString(KEY_TRIGGER) ?: "")
        }.getOrDefault(BackupTrigger.AUTO)

        promoteToForeground()

        val startedAt = System.currentTimeMillis()
        val backupResult = cloudBackupManager.createBackup { progress ->
            updateProgressNotification(progress)
        }
        val durationMs = System.currentTimeMillis() - startedAt

        return backupResult.fold(
            onSuccess = { info ->
                // Отметка — момент начала, а не конца: снимок базы снимается в начале заливки,
                // и правки, сделанные пока она шла, в копию не попали. С отметкой «по концу»
                // догоняющий бэкап считал бы их сохранёнными
                prefs.edit().putLong(BackupPrefs.KEY_LAST_BACKUP, startedAt).apply()
                backupLogStore.record(
                    BackupLogEntry(
                        timestamp = System.currentTimeMillis(),
                        trigger = trigger,
                        outcome = BackupOutcome.SUCCESS,
                        errorType = null,
                        attempt = runAttemptCount,
                        sizeBytes = info.size,
                        uploadedMediaCount = info.uploadedMediaCount,
                        durationMs = durationMs
                    )
                )
                notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
                showSuccessNotification()
                Result.success()
            },
            onFailure = { error ->
                val type = BackupErrorClassifier.classify(error)
                val willRetry = BackupRetryPolicy.shouldRetry(type, runAttemptCount)
                // Шаг и хост нужны, чтобы отличить «недоступен загрузчик» от «нет интернета»
                val network = error as? BackupNetworkException
                backupLogStore.record(
                    BackupLogEntry(
                        timestamp = System.currentTimeMillis(),
                        trigger = trigger,
                        outcome = if (willRetry) BackupOutcome.RETRY else BackupOutcome.FAILURE,
                        errorType = type,
                        attempt = runAttemptCount,
                        sizeBytes = 0,
                        uploadedMediaCount = 0,
                        phase = network?.phase,
                        host = network?.host,
                        // Разворачиваем обёртку: нужен класс исходной причины,
                        // а не наш BackupNetworkException
                        errorClass = (network?.cause ?: error).javaClass.simpleName,
                        durationMs = durationMs
                    )
                )
                notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
                if (willRetry) {
                    // Молча: пользователю незачем знать про каждую промежуточную попытку
                    Result.retry()
                } else {
                    showErrorNotification(type)
                    Result.failure()
                }
            }
        )
    }

    // === Уведомления ===

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val resultChannel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.backup_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedContext.getString(R.string.backup_channel_description)
        }

        // Отдельный тихий канал: ход заливки не должен звенеть при каждом бэкапе
        val progressChannel = NotificationChannel(
            PROGRESS_CHANNEL_ID,
            localizedContext.getString(R.string.backup_progress_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedContext.getString(R.string.backup_progress_channel_description)
        }

        notificationManager.createNotificationChannel(resultChannel)
        notificationManager.createNotificationChannel(progressChannel)
    }

    /**
     * Просим систему считать задачу приоритетной на время заливки.
     * На Android 12+ старт foreground-сервиса из фона может быть запрещён — тогда
     * работаем как обычная фоновая задача, как и раньше.
     */
    private suspend fun promoteToForeground() {
        try {
            setForeground(foregroundInfo(BackupProgress.Preparing))
        } catch (e: Exception) {
            DiagnosticLog.recordError(DiagnosticArea.BACKUP, "foreground_promotion_failed", e)
        }
    }

    private fun foregroundInfo(progress: BackupProgress): ForegroundInfo = ForegroundInfo(
        PROGRESS_NOTIFICATION_ID,
        progressNotification(progress),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )

    private fun progressNotification(progress: BackupProgress) =
        NotificationCompat.Builder(localizedContext, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle(localizedContext.getString(R.string.backup_progress_title))
            .setContentText(progressText(progress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .apply {
                if (progress is BackupProgress.UploadingMedia && progress.total > 0) {
                    setProgress(progress.total, progress.done, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

    private fun progressText(progress: BackupProgress): String = when (progress) {
        is BackupProgress.Preparing ->
            localizedContext.getString(R.string.backup_progress_preparing)
        is BackupProgress.UploadingMedia ->
            localizedContext.getString(
                R.string.backup_progress_media,
                progress.done + 1,
                progress.total
            )
        is BackupProgress.UploadingSnapshot ->
            localizedContext.getString(R.string.backup_progress_snapshot)
        is BackupProgress.Finishing ->
            localizedContext.getString(R.string.backup_progress_finishing)
    }

    private fun updateProgressNotification(progress: BackupProgress) {
        val now = System.currentTimeMillis()
        // Первое и последнее событие этапа важнее плавности: остальное режем по времени
        if (progress is BackupProgress.UploadingMedia &&
            now - lastProgressUpdate < PROGRESS_THROTTLE_MS
        ) {
            return
        }
        lastProgressUpdate = now
        try {
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, progressNotification(progress))
        } catch (e: Exception) {
            DiagnosticLog.recordError(DiagnosticArea.BACKUP, "progress_notification_failed", e)
        }
    }

    /** Уведомление открывает приложение — раньше по нажатию не происходило ничего. */
    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showSuccessNotification() {
        val notification = NotificationCompat.Builder(localizedContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle(localizedContext.getString(R.string.backup_success_title))
            .setContentText(localizedContext.getString(R.string.backup_success_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId, notification)
    }

    private fun showErrorNotification(type: BackupErrorType) {
        val notification = NotificationCompat.Builder(localizedContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup_notification)
            .setContentTitle(localizedContext.getString(R.string.backup_error_title))
            .setContentText(localizedContext.getString(type.messageRes()))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(localizedContext.getString(type.messageRes()))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId, notification)
    }
}

/**
 * Worker для автоматического резервного копирования.
 * Запускается через 5 минут после последнего изменения данных (при наличии интернета),
 * а также как догоняющий бэкап при открытии приложения.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    cloudBackupManager: CloudBackupManager,
    backupLogStore: BackupLogStore
) : BaseBackupWorker(context, workerParams, cloudBackupManager, backupLogStore) {

    override val resultNotificationId = 1001
    override val checksAutoBackupEnabled = true
}

/**
 * Worker для ручного резервного копирования (кнопка "Создать копию").
 */
@HiltWorker
class ManualBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    cloudBackupManager: CloudBackupManager,
    backupLogStore: BackupLogStore
) : BaseBackupWorker(context, workerParams, cloudBackupManager, backupLogStore) {

    override val resultNotificationId = 1002
    override val checksAutoBackupEnabled = false
}
