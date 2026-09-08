package com.carlog.data.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.carlog.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Диагностический журнал приложения.
 *
 * Намеренно сделан объектом с ручной инициализацией, а не Hilt-синглтоном: писать в него
 * должны и места без внедрения зависимостей — `object FileHelper` и обработчик падений,
 * который срабатывает, когда графа зависимостей уже может не быть.
 *
 * Пишем только ошибки и редкие опорные события: журнал на 200 записей должен покрывать
 * недели использования, иначе к моменту жалобы нужного в нём уже не окажется.
 */
object DiagnosticLog {

    private const val LOG_DIR = "diagnostics"
    private const val LOG_FILE = "log.txt"

    /** Имя файла, который пользователь сохраняет и отправляет разработчику */
    const val EXPORT_FILE_NAME = "carlog-diagnostics.txt"

    @Volatile
    private var appContext: Context? = null

    private val entries = mutableListOf<DiagnosticEntry>()
    private val lock = Any()

    /** Вызывается один раз из `CarLogApplication.onCreate` */
    fun init(context: Context) {
        appContext = context.applicationContext
        synchronized(lock) {
            entries.clear()
            entries.addAll(runCatching { DiagnosticBuffer.parse(logFile().readTextOrEmpty()) }
                .getOrDefault(emptyList()))
        }
    }

    fun record(
        area: DiagnosticArea,
        severity: DiagnosticSeverity,
        event: String,
        details: String? = null
    ) {
        val entry = DiagnosticEntry(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            area = area,
            event = event,
            // Чистим на входе: секрет не должен полежать в файле даже секунду
            details = DiagnosticRedactor.redact(details)
        )
        synchronized(lock) {
            val updated = DiagnosticBuffer.append(entries.toList(), entry)
            entries.clear()
            entries.addAll(updated)
            runCatching { logFile().writeText(DiagnosticBuffer.serialize(updated)) }
        }
    }

    /** Ошибка с исключением: класс важнее текста, текст бывает пустым */
    fun recordError(
        area: DiagnosticArea,
        event: String,
        error: Throwable,
        details: String? = null
    ) {
        val description = buildString {
            append(error.javaClass.simpleName)
            error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            error.cause?.let { append(" <- ").append(it.javaClass.simpleName) }
            details?.let { append(" — ").append(it) }
        }
        record(area, DiagnosticSeverity.ERROR, event, description)
    }

    fun entries(): List<DiagnosticEntry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            runCatching { logFile().delete() }
        }
    }

    /**
     * Текст для отправки разработчику: шапка с окружением и весь журнал.
     * Шапка отвечает на вопросы, которые иначе пришлось бы задавать письмом.
     */
    fun exportText(): String {
        val context = appContext
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        val header = buildString {
            appendLine("Car Log ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Локаль: ${Locale.getDefault()}")
            context?.let {
                appendLine("Сеть сейчас: ${networkDescription(it)}")
                appendLine("Работа в фоне без ограничений: ${batteryExemptionDescription(it)}")
            }
            appendLine("Журнал снят: ${sdf.format(Date())}")
            appendLine("Записей: ${entries().size}")
        }

        val body = entries().joinToString("\n") { entry ->
            buildString {
                append(sdf.format(Date(entry.timestamp)))
                append(' ').append(entry.severity.name)
                append(' ').append(entry.area.name)
                append(' ').append(entry.event)
                entry.details?.let { append(" — ").append(it) }
            }
        }

        return if (body.isEmpty()) "$header\n(журнал пуст)" else "$header\n$body"
    }

    /** Сохраняет журнал во внутренний файл, пригодный для отправки через FileProvider. */
    fun exportToFile(): File? {
        val context = appContext ?: return null
        return runCatching {
            val target = File(logDir(context), EXPORT_FILE_NAME)
            target.writeText(exportText())
            target
        }.getOrNull()
    }

    // === Окружение ===

    private fun networkDescription(context: Context): String = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
        when {
            capabilities == null -> "нет"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "другая"
        }
    } catch (e: Exception) {
        "неизвестно"
    }

    private fun batteryExemptionDescription(context: Context): String = try {
        val powerManager = context.getSystemService(android.os.PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) "да" else "нет"
    } catch (e: Exception) {
        "неизвестно"
    }

    // === Файл ===

    private fun logDir(context: Context): File =
        File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }

    private fun logFile(): File {
        val context = appContext ?: error("DiagnosticLog.init не вызван")
        return File(logDir(context), LOG_FILE)
    }

    private fun File.readTextOrEmpty(): String = if (exists()) readText() else ""
}
