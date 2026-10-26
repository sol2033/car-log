package com.carlog.util

import com.carlog.data.local.CarLogDatabase

/**
 * Проверка файла базы из резервной копии **до** того, как он заменит текущую.
 *
 * Читается только заголовок SQLite (первые 100 байт): подпись формата и `user_version`,
 * в котором Room хранит версию схемы.
 */
object BackupDatabaseCheck {

    /** Сколько байт заголовка нужно для проверки */
    const val HEADER_SIZE = 100

    private val SQLITE_SIGNATURE = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** Смещение `user_version` в заголовке SQLite: 4 байта, big-endian */
    private const val USER_VERSION_OFFSET = 60

    enum class Verdict {
        OK,
        /** Не база SQLite: фото, PDF, чужой архив */
        NOT_DATABASE,
        /**
         * Схема новее, чем знает эта версия приложения. Room при запуске упал бы
         * на понижении версии — и так при каждом запуске, пока не очистить данные
         */
        TOO_NEW
    }

    fun isSqliteHeader(header: ByteArray): Boolean =
        header.size >= SQLITE_SIGNATURE.size &&
            SQLITE_SIGNATURE.indices.all { header[it] == SQLITE_SIGNATURE[it] }

    /** Версия схемы из заголовка; null — заголовок короче нужного */
    fun userVersion(header: ByteArray): Int? {
        if (header.size < USER_VERSION_OFFSET + 4) return null
        return (USER_VERSION_OFFSET until USER_VERSION_OFFSET + 4)
            .fold(0) { acc, i -> (acc shl 8) or (header[i].toInt() and 0xFF) }
    }

    fun verdict(header: ByteArray, supportedVersion: Int = CarLogDatabase.SCHEMA_VERSION): Verdict {
        if (!isSqliteHeader(header)) return Verdict.NOT_DATABASE
        val version = userVersion(header) ?: return Verdict.NOT_DATABASE
        return if (version > supportedVersion) Verdict.TOO_NEW else Verdict.OK
    }

    /** Заголовок отдельного файла базы */
    fun headerOf(file: java.io.File): ByteArray =
        java.io.FileInputStream(file).use { it.readUpTo(HEADER_SIZE) }

    /**
     * Заголовок базы внутри ZIP-архива копии; null — базы в архиве нет.
     * @param isDatabaseEntry какая запись архива считается базой
     */
    fun headerInZip(zipFile: java.io.File, isDatabaseEntry: (String) -> Boolean): ByteArray? =
        java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isDatabaseEntry(entry.name)) return@use zis.readUpTo(HEADER_SIZE)
                zis.closeEntry()
                entry = zis.nextEntry
            }
            null
        }

    /** Исключение для отказа — null, если копию можно восстанавливать */
    fun failureFor(verdict: Verdict): Exception? = when (verdict) {
        Verdict.OK -> null
        Verdict.NOT_DATABASE -> NotABackupFileException()
        Verdict.TOO_NEW -> BackupFromNewerAppException()
    }
}

/** Выбранный для импорта файл — не база Car Log и не архив с ней; текущие данные не тронуты */
class NotABackupFileException : Exception("Файл не является резервной копией Car Log")

/** Копия сделана более новой версией приложения; текущие данные не тронуты */
class BackupFromNewerAppException :
    Exception("Копия сделана более новой версией Car Log — сначала обновите приложение")

/** Чтение до [count] байт: `InputStream.readNBytes` появился только в API 33 */
fun java.io.InputStream.readUpTo(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(buffer, read, count - read)
        if (n < 0) break
        read += n
    }
    return buffer.copyOf(read)
}
