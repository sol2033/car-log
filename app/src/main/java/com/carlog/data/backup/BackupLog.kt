package com.carlog.data.backup

import android.content.Context
import com.carlog.data.diagnostics.DiagnosticArea
import com.carlog.data.diagnostics.DiagnosticLog
import com.carlog.data.diagnostics.DiagnosticSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Что запустило бэкап — нужно при разборе жалоб: «нажал кнопку» и «сработало само» ведут себя по-разному. */
enum class BackupTrigger { AUTO, MANUAL, CATCH_UP }

/** Чем закончилась попытка. RETRY — сбой, после которого назначен повтор. */
enum class BackupOutcome { SUCCESS, RETRY, FAILURE }

/**
 * Одна запись журнала бэкапов.
 *
 * Журнал появился потому, что вся диагностика проблемы у пользователя сводилась
 * к одному слову в уведомлении и догадкам. Хранится только техническая сводка:
 * ни данных автомобиля, ни имён файлов, ни токена.
 */
data class BackupLogEntry(
    val timestamp: Long,
    val trigger: BackupTrigger,
    val outcome: BackupOutcome,
    val errorType: BackupErrorType?,
    val attempt: Int,
    val sizeBytes: Long,
    val uploadedMediaCount: Int,
    /**
     * Шаг, на котором упало. Главное поле при разборе: шаги ходят на разные хосты,
     * и сбой только на `UPLOAD` при работающих `FOLDER`/`LIST` означает, что недоступен
     * именно хост загрузчика, а не «нет интернета».
     */
    val phase: BackupPhase? = null,
    /** Хост, до которого не достучались. Только адрес Яндекса — ни токена, ни данных. */
    val host: String? = null,
    /**
     * Класс исходного исключения. Категория [BackupErrorType] слишком груба для разбора:
     * `UnknownHostException` (не резолвится DNS), `ConnectException` (маршрут закрыт) и
     * `SocketTimeoutException` (пакеты молча дропаются) — три разные причины, а категория
     * у первых двух одна.
     */
    val errorClass: String? = null,
    /**
     * Сколько миллисекунд шла попытка. Отличает connect timeout (~30 с на попытку)
     * от read timeout (~120 с): это разные болезни.
     */
    val durationMs: Long = 0
) {
    fun serialize(): String = listOf(
        timestamp.toString(),
        trigger.name,
        outcome.name,
        errorType?.name ?: EMPTY,
        attempt.toString(),
        sizeBytes.toString(),
        uploadedMediaCount.toString(),
        phase?.name ?: EMPTY,
        host?.takeIf { it.isNotBlank() } ?: EMPTY,
        errorClass?.takeIf { it.isNotBlank() } ?: EMPTY,
        durationMs.toString()
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"
        private const val EMPTY = "-"
        private const val FIELD_COUNT = 11

        /** `null` — строка от другой версии формата или битая; такие просто пропускаем. */
        fun parse(line: String): BackupLogEntry? {
            val parts = line.split(SEPARATOR)
            if (parts.size != FIELD_COUNT) return null
            return try {
                BackupLogEntry(
                    timestamp = parts[0].toLong(),
                    trigger = enumValueOf(parts[1]),
                    outcome = enumValueOf(parts[2]),
                    errorType = if (parts[3] == EMPTY) null else enumValueOf<BackupErrorType>(parts[3]),
                    attempt = parts[4].toInt(),
                    sizeBytes = parts[5].toLong(),
                    uploadedMediaCount = parts[6].toInt(),
                    phase = if (parts[7] == EMPTY) null else enumValueOf<BackupPhase>(parts[7]),
                    host = parts[8].takeIf { it != EMPTY },
                    errorClass = parts[9].takeIf { it != EMPTY },
                    durationMs = parts[10].toLong()
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Кольцевой буфер журнала — чистая логика, без хранилища.
 */
object BackupEventLog {

    /** Сколько последних событий храним */
    const val MAX_ENTRIES = 20

    /** Новые записи в начале списка; старые вытесняются. */
    fun append(
        current: List<BackupLogEntry>,
        entry: BackupLogEntry,
        maxEntries: Int = MAX_ENTRIES
    ): List<BackupLogEntry> = (listOf(entry) + current).take(maxEntries)

    fun serialize(entries: List<BackupLogEntry>): String =
        entries.joinToString("\n") { it.serialize() }

    fun parse(text: String): List<BackupLogEntry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { BackupLogEntry.parse(it) }
            .toList()

    /**
     * Сколько сбоев подряд в конце (то есть в начале списка — он отсортирован от новых).
     * По этому счётчику решаем, показывать ли подсказку про настройки прошивки.
     * Повторы (RETRY) не считаем: это ещё не окончательный сбой.
     */
    fun consecutiveFailures(entries: List<BackupLogEntry>): Int =
        entries.asSequence()
            .filter { it.outcome != BackupOutcome.RETRY }
            .takeWhile { it.outcome == BackupOutcome.FAILURE }
            .count()
}

/**
 * Журнал в SharedPreferences. Отдельно от [BackupEventLog], чтобы логику
 * кольцевого буфера можно было тестировать без Android (правило 9).
 */
@Singleton
class BackupLogStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(BackupPrefs.NAME, Context.MODE_PRIVATE)
    }

    fun entries(): List<BackupLogEntry> =
        BackupEventLog.parse(prefs.getString(BackupPrefs.KEY_LOG, "") ?: "")

    @Synchronized
    fun record(entry: BackupLogEntry) {
        val updated = BackupEventLog.append(entries(), entry)
        prefs.edit()
            .putString(BackupPrefs.KEY_LOG, BackupEventLog.serialize(updated))
            .apply()

        // Дублируем в общий диагностический журнал: этот показывается на экране
        // структурированно, тот уходит разработчику файлом вместе со всем остальным
        DiagnosticLog.record(
            area = DiagnosticArea.BACKUP,
            severity = when (entry.outcome) {
                BackupOutcome.SUCCESS -> DiagnosticSeverity.INFO
                BackupOutcome.RETRY -> DiagnosticSeverity.WARNING
                BackupOutcome.FAILURE -> DiagnosticSeverity.ERROR
            },
            event = "backup_${entry.outcome.name.lowercase()}",
            details = buildString {
                append("trigger=").append(entry.trigger.name)
                append(" attempt=").append(entry.attempt)
                entry.errorType?.let { append(" error=").append(it.name) }
                entry.errorClass?.let { append(" exception=").append(it) }
                entry.phase?.let { append(" phase=").append(it.name) }
                entry.host?.let { append(" host=").append(it) }
                append(" took=").append(entry.durationMs).append("ms")
                append(" size=").append(entry.sizeBytes)
                append(" media=").append(entry.uploadedMediaCount)
            }
        )
    }

    fun clear() {
        prefs.edit().remove(BackupPrefs.KEY_LOG).apply()
    }

    /** Текст для кнопки «Поделиться» — то, что пользователь пришлёт в поддержку. */
    fun formatForSharing(appVersion: String, device: String): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val header = "Car Log $appVersion / $device"
        val lines = entries().map { entry ->
            buildString {
                append(sdf.format(Date(entry.timestamp)))
                append(' ').append(entry.trigger.name)
                append(' ').append(entry.outcome.name)
                entry.errorType?.let { append(' ').append(it.name) }
                // Шаг, хост и класс исключения — то, ради чего журнал отправляют в поддержку
                entry.errorClass?.let { append(' ').append(it) }
                entry.phase?.let { append(" phase=").append(it.name) }
                entry.host?.let { append(" host=").append(it) }
                append(" took=").append(entry.durationMs).append("ms")
                append(" attempt=").append(entry.attempt)
                append(" size=").append(entry.sizeBytes)
                append(" media=").append(entry.uploadedMediaCount)
            }
        }
        return (listOf(header) + lines).joinToString("\n")
    }
}
