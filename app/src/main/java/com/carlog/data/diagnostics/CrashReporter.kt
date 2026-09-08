package com.carlog.data.diagnostics

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Записывает падение приложения в журнал перед тем, как процесс умрёт.
 *
 * Важно: прежний обработчик обязательно вызывается следом. Без этого приложение
 * зависло бы вместо честного падения, а системный отчёт об ошибке не сформировался бы.
 */
object CrashReporter {

    /** Стек режем: полный бывает на сотни строк и вытеснит из журнала всё остальное */
    private const val MAX_STACK_LINES = 25

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                DiagnosticLog.record(
                    area = DiagnosticArea.APP,
                    severity = DiagnosticSeverity.CRASH,
                    event = "uncaught_exception",
                    details = "${thread.name}: ${shortStackTrace(error)}"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun shortStackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
            .lineSequence()
            .take(MAX_STACK_LINES)
            .joinToString(" / ") { it.trim() }
    }
}
