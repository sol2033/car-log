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
    }
    
    /**
     * Экспорт всех данных в ZIP архив с базой данных и фотографиями
     */
    suspend fun exportToJson(outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        // Закрываем все соединения с БД
        try {
            database.close()
        } catch (e: Exception) {
            // Игнорируем ошибки закрытия
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
                val photosDir = File(context.filesDir, "photos")
                if (photosDir.exists() && photosDir.isDirectory) {
                    photosDir.listFiles()?.forEach { photoFile ->
                        if (photoFile.isFile) {
                            addFileToZip(zos, photoFile, "$PHOTOS_FOLDER/${photoFile.name}")
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
        // Закрываем базу
        try {
            database.close()
        } catch (e: Exception) {
            // Игнорируем ошибки закрытия
        }
        
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
    
    /**
     * Импорт из ZIP архива (новый формат)
     */
    private suspend fun importFromZip(zipFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var dbRestored = false
            var photosRestored = 0
            
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
                            dbRestored = true
                        }
                        entry.name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory -> {
                            // Восстанавливаем фотографию
                            val photoName = entry.name.removePrefix("$PHOTOS_FOLDER/")
                            val photoFile = File(context.filesDir, "photos/$photoName")
                            photoFile.parentFile?.mkdirs()
                            FileOutputStream(photoFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            photosRestored++
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
