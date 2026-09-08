package com.carlog.data.diagnostics

/** Насколько серьёзно событие. INFO пишем редко — только то, что помогает понять контекст сбоя. */
enum class DiagnosticSeverity { INFO, WARNING, ERROR, CRASH }

/**
 * Подсистема, к которой относится событие. По ней видно, куда смотреть,
 * не вчитываясь в текст.
 */
enum class DiagnosticArea {
    /** Облачный бэкап: шаги, хосты, категории ошибок */
    BACKUP,

    /** База данных: миграции, ошибки доступа */
    DATABASE,

    /** Фото и PDF: сохранение, пережатие, удаление */
    PHOTO,

    /** Локальный экспорт/импорт архива */
    EXPORT,

    /** Приложение в целом: запуск, падения */
    APP
}

/**
 * Одна запись диагностического журнала.
 *
 * Журнал существует ради разбора жалоб: пользователь отправляет файл, и по нему должно быть
 * видно, что произошло, без переписки на пять сообщений. Поэтому в записи всё, что нужно
 * для диагноза, и **ничего** из данных пользователя: ни названий машин, ни заметок,
 * ни имён файлов, ни токенов (см. [DiagnosticRedactor]).
 */
data class DiagnosticEntry(
    val timestamp: Long,
    val severity: DiagnosticSeverity,
    val area: DiagnosticArea,
    /** Короткое машинное имя события, например `upload_failed` */
    val event: String,
    /** Технические подробности: класс исключения, шаг, хост, длительность */
    val details: String? = null
) {
    fun serialize(): String = listOf(
        timestamp.toString(),
        severity.name,
        area.name,
        event.sanitize(),
        details?.sanitize()?.take(MAX_DETAILS) ?: EMPTY
    ).joinToString(SEPARATOR)

    /** Разделитель и переносы строк в тексте сломали бы разбор */
    private fun String.sanitize(): String =
        replace(SEPARATOR, "/").replace('\n', ' ').replace('\r', ' ').trim()

    companion object {
        private const val SEPARATOR = "|"
        private const val EMPTY = "-"
        private const val FIELD_COUNT = 5

        /** Столько символов подробностей хватает на стек и не разносит файл */
        const val MAX_DETAILS = 1500

        /** `null` — строка битая или от другой версии формата; такие пропускаем. */
        fun parse(line: String): DiagnosticEntry? {
            val parts = line.split(SEPARATOR, limit = FIELD_COUNT)
            if (parts.size != FIELD_COUNT) return null
            return try {
                DiagnosticEntry(
                    timestamp = parts[0].toLong(),
                    severity = enumValueOf(parts[1]),
                    area = enumValueOf(parts[2]),
                    event = parts[3],
                    details = parts[4].takeIf { it != EMPTY }
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Кольцевой буфер журнала — чистая логика, отдельно от файла (правило 9).
 */
object DiagnosticBuffer {

    /** Сколько последних событий храним */
    const val MAX_ENTRIES = 200

    /** Новые записи в конце: файл читается сверху вниз, как обычный лог. */
    fun append(
        current: List<DiagnosticEntry>,
        entry: DiagnosticEntry,
        maxEntries: Int = MAX_ENTRIES
    ): List<DiagnosticEntry> = (current + entry).takeLast(maxEntries)

    fun serialize(entries: List<DiagnosticEntry>): String =
        entries.joinToString("\n") { it.serialize() }

    fun parse(text: String): List<DiagnosticEntry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { DiagnosticEntry.parse(it) }
            .toList()
}
