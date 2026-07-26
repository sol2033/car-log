package com.carlog.data.backup

import android.content.Context
import com.carlog.data.local.CarLogDatabase
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
 * Менеджер резервного копирования через Яндекс.Диск API.
 * Создаёт ZIP-архивы с базой данных и фотографиями, загружает на Яндекс.Диск.
 */
@Singleton
class CloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val yandexAuthManager: YandexAuthManager,
    private val database: CarLogDatabase
) {
    companion object {
        private const val DATABASE_NAME = "car_log_database"
        private const val DATABASE_FILE_IN_ZIP = "car_log_database.db"
        private const val PHOTOS_FOLDER = "photos"
        private const val DOCUMENTS_FOLDER = "documents"
        private const val MAX_BACKUPS = 3
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    private fun getApi(): YandexDiskApi {
        val token = yandexAuthManager.getToken()
            ?: throw Exception("Яндекс.Диск не подключён")
        return YandexDiskApi(token)
    }

    /**
     * Создаёт резервную копию и загружает её на Яндекс.Диск.
     */
    suspend fun createBackup(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val timestamp = dateFormat.format(Date())
            val backupFileName = "${YandexDiskApi.BACKUP_PREFIX}$timestamp.zip"

            // Сбрасываем WAL в основной файл БД: Room работает в режиме WAL, и без
            // checkpoint последние транзакции лежат в car_log_database-wal и не попали бы в архив
            checkpointDatabase()

            // Создаём временный ZIP-архив
            val tempZipFile = File(context.cacheDir, backupFileName)
            createZipArchive(tempZipFile)
            val backupSize = tempZipFile.length()

            // Создаём папку на Яндекс.Диске (игнорирует 409 если уже есть)
            api.ensureFolderExists()

            // Загружаем ZIP на Яндекс.Диск
            val success = api.uploadFile(backupFileName, tempZipFile)
            tempZipFile.delete()

            if (!success) {
                return@withContext Result.failure(Exception("Ошибка загрузки на Яндекс.Диск"))
            }

            // Удаляем старые копии, если включено
            val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_delete_old_backups", true)) {
                cleanOldBackups(api)
            }

            Result.success(
                BackupInfo(
                    fileName = backupFileName,
                    remotePath = "${YandexDiskApi.BACKUP_FOLDER_PATH}/$backupFileName",
                    timestamp = System.currentTimeMillis(),
                    size = backupSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Восстанавливает данные из резервной копии на Яндекс.Диске.
     * @param remotePath путь на Яндекс.Диске, например "disk:/CarLog/CarLog_Backup_xxx.zip"
     */
    suspend fun restoreBackup(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "restore_temp.zip")
        try {
            val api = getApi()
            api.downloadFile(remotePath, tempZipFile)
            extractZipArchive(tempZipFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempZipFile.delete()
        }
    }

    /**
     * Возвращает список доступных резервных копий на Яндекс.Диске.
     */
    suspend fun getAvailableBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val backups = api.listBackups().map { file ->
                BackupInfo(
                    fileName = file.name,
                    remotePath = file.path,
                    timestamp = file.timestamp,
                    size = file.size
                )
            }
            Result.success(backups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRAGMA wal_checkpoint(TRUNCATE) переносит содержимое -wal в основной файл БД.
     * Без этого архив, снятый с «живой» базы, не содержал бы последних изменений.
     * Ошибка checkpoint не должна срывать бэкап — в худшем случае копия будет без
     * самых свежих данных, как и раньше.
     */
    private fun checkpointDatabase() {
        try {
            database.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        } catch (_: Exception) {
        }
    }

    private fun cleanOldBackups(api: YandexDiskApi) {
        api.listBackups()
            .drop(MAX_BACKUPS)
            .forEach { api.deleteFile(it.path) }
    }

    private fun createZipArchive(zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // База данных
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, DATABASE_FILE_IN_ZIP)
            }

            // Фотографии
            val photosDir = File(context.filesDir, PHOTOS_FOLDER)
            if (photosDir.exists() && photosDir.isDirectory) {
                photosDir.listFiles()?.forEach { photoFile ->
                    if (photoFile.isFile) {
                        addFileToZip(zos, photoFile, "$PHOTOS_FOLDER/${photoFile.name}")
                    }
                }
            }

            // PDF-документы ДТП
            val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
            if (documentsDir.exists() && documentsDir.isDirectory) {
                documentsDir.listFiles()?.forEach { documentFile ->
                    if (documentFile.isFile) {
                        addFileToZip(zos, documentFile, "$DOCUMENTS_FOLDER/${documentFile.name}")
                    }
                }
            }
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    private fun extractZipArchive(zipFile: File) {
        val photosDir = File(context.filesDir, PHOTOS_FOLDER)
        val documentsDir = File(context.filesDir, DOCUMENTS_FOLDER)
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outputFile = when {
                    entry.name == DATABASE_FILE_IN_ZIP ->
                        context.getDatabasePath(DATABASE_NAME)
                    entry.name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory -> {
                        val photoName = entry.name.removePrefix("$PHOTOS_FOLDER/")
                        // Защита от zip-slip: имя из архива не должно выводить за пределы папки фото
                        File(photosDir, photoName).takeIf {
                            it.canonicalPath.startsWith(photosDir.canonicalPath + File.separator)
                        }
                    }
                    entry.name.startsWith(DOCUMENTS_FOLDER) && !entry.isDirectory -> {
                        val documentName = entry.name.removePrefix("$DOCUMENTS_FOLDER/")
                        File(documentsDir, documentName).takeIf {
                            it.canonicalPath.startsWith(documentsDir.canonicalPath + File.separator)
                        }
                    }
                    else -> null
                }
                outputFile?.let { file ->
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                    if (entry?.name == DATABASE_FILE_IN_ZIP) {
                        // Устаревшие журналы старой БД не должны «доиграться» поверх восстановленной
                        File(file.path + "-wal").delete()
                        File(file.path + "-shm").delete()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

data class BackupInfo(
    val fileName: String,
    val remotePath: String,   // Путь на Яндекс.Диске: disk:/CarLog/CarLog_Backup_xxx.zip
    val timestamp: Long,
    val size: Long
)
