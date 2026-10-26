package com.carlog.util

import android.content.Context
import com.carlog.data.local.CarLogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DatabaseBackup(
    private val database: CarLogDatabase,
    private val context: Context
) {
    
    companion object {
        private const val DATABASE_NAME = "car_log_database"
        private const val DATABASE_FILE_IN_ZIP = "car_log_database.db"
        private const val PHOTOS_FOLDER = "photos"
        private const val DOCUMENTS_FOLDER = "documents"

        /** Первые 16 байт любого файла базы SQLite */
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /**
         * Похож ли файл на базу SQLite. Проверка до подмены: раньше любой выбранный файл,
         * не оказавшийся ZIP (фото, PDF), записывался поверх базы, и после перезапуска
         * Room не мог её открыть — все данные пропадали.
         */
        fun isSqliteHeader(header: ByteArray): Boolean =
            header.size >= SQLITE_HEADER.size &&
                SQLITE_HEADER.indices.all { header[it] == SQLITE_HEADER[it] }
    }
    
    /**
     * Экспорт всех данных в ZIP архив с базой данных и фотографиями
     */
    suspend fun exportToJson(outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        // База остаётся открытой: приложение после экспорта продолжает работать, а Room
        // после close() дальше работать не рассчитан (раньше база здесь закрывалась).
        // Чтобы последние транзакции попали в копию, переносим журнал WAL в основной файл —
        // так же делает облачный бэкап
        try {
            database.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        } catch (e: Exception) {
            // Не повод срывать экспорт: в худшем случае копия будет без самых свежих правок
        }
        
        try {
            // Создаём ZIP архив
            ZipOutputStream(outputStream).use { zos ->
                // 1. Добавляем базу данных
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (dbFile.exists()) {
                    addFileToZip(zos, dbFile, DATABASE_FILE_IN_ZIP)
                } else {
                    return@withContext Result.failure(Exception("Database file not found"))
                }
                
                // 2. Добавляем все фотографии
                val photosDir = File(context.filesDir, PHOTOS_FOLDER)
                if (photosDir.exists() && photosDir.isDirectory) {
                    photosDir.listFiles()?.forEach { photoFile ->
                        if (photoFile.isFile) {
                            addFileToZip(zos, photoFile, "$PHOTOS_FOLDER/${photoFile.name}")
                        }
                    }
                }

                // 3. Добавляем PDF-документы ДТП
                val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
                if (documentsDir.exists() && documentsDir.isDirectory) {
                    documentsDir.listFiles()?.forEach { documentFile ->
                        if (documentFile.isFile) {
                            addFileToZip(zos, documentFile, "$DOCUMENTS_FOLDER/${documentFile.name}")
                        }
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Добавить файл в ZIP архив
     */
    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }
    
    /**
     * Импорт данных из ZIP архива или старого .db файла (обратная совместимость)
     */
    suspend fun importFromJson(inputStream: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "import_temp_file")
        try {
            
            // Копируем входной поток во временный файл
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            // Проверяем формат файла: ZIP или старый .db
            val isZipFile = try {
                ZipInputStream(FileInputStream(tempFile)).use { zis ->
                    zis.nextEntry != null
                }
            } catch (e: Exception) {
                false
            }

            // Сначала убеждаемся, что внутри действительно база, и только потом закрываем
            // текущую и пишем поверх: на неподходящем файле приложение остаётся как было
            val hasValidDatabase = if (isZipFile) zipContainsSqliteDatabase(tempFile) else fileIsSqlite(tempFile)
            if (!hasValidDatabase) {
                return@withContext Result.failure(NotABackupFileException())
            }

            try {
                database.close()
            } catch (e: Exception) {
                // Игнорируем ошибки закрытия
            }
            
            if (isZipFile) {
                // Новый формат: ZIP архив с БД и фотографиями
                importFromZip(tempFile)
            } else {
                // Старый формат: просто файл базы данных
                importFromLegacyDb(tempFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Удаляем временный файл в любом случае
            tempFile.delete()
        }
    }
    
    private fun fileIsSqlite(file: File): Boolean =
        FileInputStream(file).use { isSqliteHeader(it.readNBytesCompat(SQLITE_HEADER.size)) }

    /** В архиве есть запись .db, и это база SQLite — та самая, что подменит текущую */
    private fun zipContainsSqliteDatabase(zipFile: File): Boolean =
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".db")) {
                    return@use isSqliteHeader(zis.readNBytesCompat(SQLITE_HEADER.size))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            false
        }

    /** Чтение до [count] байт: `InputStream.readNBytes` появился только в API 33 */
    private fun InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(buffer, read, count - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    /**
     * Импорт из ZIP архива (новый формат)
     */
    private suspend fun importFromZip(zipFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var dbRestored = false
            var photosRestored = 0
            val photosDir = File(context.filesDir, PHOTOS_FOLDER)
            val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.endsWith(".db") -> {
                            // Восстанавливаем базу данных
                            val dbFile = context.getDatabasePath(DATABASE_NAME)
                            FileOutputStream(dbFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            // Устаревшие журналы старой БД не должны «доиграться» поверх восстановленной
                            File(dbFile.path + "-wal").delete()
                            File(dbFile.path + "-shm").delete()
                            dbRestored = true
                        }
                        entry.name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory -> {
                            // Восстанавливаем фотографию
                            val photoName = entry.name.removePrefix("$PHOTOS_FOLDER/")
                            val photoFile = File(photosDir, photoName)
                            // Защита от zip-slip: имя из архива не должно выводить за пределы папки фото
                            if (photoFile.canonicalPath.startsWith(photosDir.canonicalPath + File.separator)) {
                                photoFile.parentFile?.mkdirs()
                                FileOutputStream(photoFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                photosRestored++
                            }
                        }
                        entry.name.startsWith(DOCUMENTS_FOLDER) && !entry.isDirectory -> {
                            // Восстанавливаем PDF-документ ДТП
                            val documentName = entry.name.removePrefix("$DOCUMENTS_FOLDER/")
                            val documentFile = File(documentsDir, documentName)
                            if (documentFile.canonicalPath.startsWith(documentsDir.canonicalPath + File.separator)) {
                                documentFile.parentFile?.mkdirs()
                                FileOutputStream(documentFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            
            if (!dbRestored) {
                return@withContext Result.failure(Exception("Database not found in backup file"))
            }
            
            // Возвращаем количество восстановленных фотографий
            Result.success(photosRestored)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Импорт из старого .db файла (обратная совместимость)
     */
    private suspend fun importFromLegacyDb(dbFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Просто копируем файл базы данных
            val targetDbFile = context.getDatabasePath(DATABASE_NAME)
            FileInputStream(dbFile).use { input ->
                FileOutputStream(targetDbFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Устаревшие журналы старой БД не должны «доиграться» поверх восстановленной
            File(targetDbFile.path + "-wal").delete()
            File(targetDbFile.path + "-shm").delete()

            // Старый формат без фотографий
            Result.success(0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Генерирует имя файла для бэкапа
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "CarLog_backup_$timestamp.zip"
    }
}

/** Выбранный для импорта файл — не база Car Log и не архив с ней; текущие данные не тронуты */
class NotABackupFileException : Exception("Файл не является резервной копией Car Log")
