package com.carlog.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер резервного копирования в облачное хранилище через SAF (Storage Access Framework)
 * Создаёт ZIP архивы с базой данных и фотографиями
 */
@Singleton
class CloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BACKUP_PREFIX = "CarLog_Backup_"
        private const val DATABASE_NAME = "car_log_database"
        private const val DATABASE_FILE_IN_ZIP = "car_log_database.db"
        private const val PHOTOS_FOLDER = "photos"
        private const val MAX_BACKUPS = 3 // Актуальная, прошлая, позапрошлая
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * Создать резервную копию в выбранную папку облака
     * @param cloudFolderUri URI папки, выбранной пользователем через SAF
     * @return Result с информацией о созданном файле или ошибкой
     */
    suspend fun createBackup(cloudFolderUri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val backupFileName = "$BACKUP_PREFIX$timestamp.zip"
            
            // Создаём временный ZIP файл
            val tempZipFile = File(context.cacheDir, backupFileName)
            createZipArchive(tempZipFile)
            
            // Копируем в облачную папку через SAF
            val cloudFolder = DocumentFile.fromTreeUri(context, cloudFolderUri)
                ?: return@withContext Result.failure(Exception("Не удалось получить доступ к папке"))
            
            // Создаём файл в облачной папке
            val cloudFile = cloudFolder.createFile("application/zip", backupFileName)
                ?: return@withContext Result.failure(Exception("Не удалось создать файл в облаке"))
            
            // Копируем содержимое
            context.contentResolver.openOutputStream(cloudFile.uri)?.use { outputStream ->
                tempZipFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Не удалось записать файл"))
            
            // Удаляем временный файл
            tempZipFile.delete()
            
            // Очищаем старые бэкапы, если включено автоудаление
            val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            val autoDeleteEnabled = prefs.getBoolean("auto_delete_old_backups", true)
            if (autoDeleteEnabled) {
                cleanOldBackups(cloudFolder)
            }
            
            val backupInfo = BackupInfo(
                fileName = backupFileName,
                uri = cloudFile.uri,
                timestamp = System.currentTimeMillis(),
                size = tempZipFile.length()
            )
            
            Result.success(backupInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Создать ZIP архив с базой данных и всеми фотографиями
     */
    private fun createZipArchive(zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Добавляем базу данных
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, DATABASE_FILE_IN_ZIP)
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
     * Восстановить данные из резервной копии
     * @param backupUri URI файла резервной копии
     * @return Result с информацией об успехе/ошибке
     */
    suspend fun restoreBackup(backupUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "restore_temp.zip")
        try {
            // Копируем ZIP из облака во временный файл
            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                tempZipFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Не удалось прочитать файл"))
            
            // Закрываем базу данных перед восстановлением
            // (это будет сделано в ViewModel перед вызовом)
            
            // Распаковываем ZIP
            extractZipArchive(tempZipFile)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Удаляем временный файл в любом случае
            tempZipFile.delete()
        }
    }

    /**
     * Распаковать ZIP архив
     */
    private fun extractZipArchive(zipFile: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outputFile = when {
                    entry.name == DATABASE_FILE_IN_ZIP -> {
                        context.getDatabasePath(DATABASE_NAME)
                    }
                    entry.name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory -> {
                        val photoName = entry.name.removePrefix("$PHOTOS_FOLDER/")
                        File(context.filesDir, "photos/$photoName")
                    }
                    else -> null
                }
                
                outputFile?.let { file ->
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Удалить старые бэкапы, оставив только MAX_BACKUPS последних
     */
    private fun cleanOldBackups(cloudFolder: DocumentFile) {
        val backupFiles = cloudFolder.listFiles()
            .filter { it.name?.startsWith(BACKUP_PREFIX) == true }
            .sortedByDescending { it.lastModified() }
        
        // Удаляем все кроме MAX_BACKUPS последних
        backupFiles.drop(MAX_BACKUPS).forEach { file ->
            file.delete()
        }
    }

    /**
     * Получить список доступных резервных копий в папке
     */
    suspend fun getAvailableBackups(cloudFolderUri: Uri): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val cloudFolder = DocumentFile.fromTreeUri(context, cloudFolderUri)
                ?: return@withContext Result.failure(Exception("Не удалось получить доступ к папке"))
            
            val backups = cloudFolder.listFiles()
                .filter { it.name?.startsWith(BACKUP_PREFIX) == true }
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    BackupInfo(
                        fileName = file.name ?: "Unknown",
                        uri = file.uri,
                        timestamp = file.lastModified(),
                        size = file.length()
                    )
                }
            
            Result.success(backups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Информация о резервной копии
 */
data class BackupInfo(
    val fileName: String,
    val uri: Uri,
    val timestamp: Long,
    val size: Long
)
