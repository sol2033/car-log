package com.carlog.data.backup

import android.content.Context
import com.carlog.data.local.CarLogDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.carlog.util.BackupDatabaseCheck
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
import kotlin.coroutines.coroutineContext

/** Этап работы — по нему воркер обновляет уведомление, чтобы прогресс не выглядел зависшим. */
sealed class BackupProgress {
    object Preparing : BackupProgress()
    data class UploadingMedia(val done: Int, val total: Int) : BackupProgress()
    object UploadingSnapshot : BackupProgress()
    object Finishing : BackupProgress()
}

/**
 * Менеджер резервного копирования через Яндекс.Диск API.
 *
 * Раскладка на Диске (формат v2, с версии 1.2.3):
 * ```
 * disk:/CarLog/CarLog_Backup_<ts>.zip     снимок: БД + опись медиа
 * disk:/CarLog/CarLog_Manifest_<ts>.txt   та же опись рядом — для чистки осиротевших медиа
 * disk:/CarLog/media/IMG_<uuid>.jpg       фото и PDF, общие для всех снимков
 * ```
 * До 1.2.3 каждая копия была одним архивом со всеми фотографиями внутри и заливалась
 * заново при каждом изменении данных. Восстановление такие архивы по-прежнему понимает.
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

        /** Через сколько временный архив в кэше считается брошенным и подлежит уборке */
        private const val STALE_TEMP_AGE_MS = 60 * 60 * 1000L
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * Ручной и автоматический бэкап могут прийтись на один момент. Параллельно они
     * заливали бы по своей копии одного и того же в один канал и мешали бы друг другу
     * до таймаута — поэтому пропускаем их по очереди.
     */
    private val backupMutex = Mutex()

    private fun getApi(): YandexDiskApi {
        val token = yandexAuthManager.getToken() ?: throw YandexNotConnectedException()
        return YandexDiskApi(token)
    }

    /**
     * Создаёт резервную копию и загружает её на Яндекс.Диск.
     *
     * Порядок важен: сначала медиа, снимок — последним. Тогда любой снимок, доехавший
     * до Диска, гарантированно опирается на уже лежащие там файлы.
     */
    suspend fun createBackup(
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<BackupInfo> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            var tempZipFile: File? = null
            try {
                onProgress(BackupProgress.Preparing)
                val api = getApi()
                val timestamp = dateFormat.format(Date())
                val backupFileName = "${YandexDiskApi.BACKUP_PREFIX}$timestamp.zip"
                val manifestFileName = "${YandexDiskApi.MANIFEST_PREFIX}$timestamp.txt"

                // Подбираем архивы, брошенные прошлыми неудачными попытками
                cleanStaleTempFiles()

                // Сбрасываем WAL в основной файл БД: Room работает в режиме WAL, и без
                // checkpoint последние транзакции лежат в car_log_database-wal и не попали бы в архив
                checkpointDatabase()

                api.ensureFolderExists()

                // 1. Медиа: заливаем только то, чего на Диске ещё нет
                val localMedia = collectLocalMedia()
                val uploadedCount = uploadMissingMedia(api, localMedia, onProgress)

                // 2. Снимок: БД и опись — это сотни килобайт вместо десятков мегабайт
                onProgress(BackupProgress.UploadingSnapshot)
                val manifest = BackupManifest(localMedia.keys.sorted())
                tempZipFile = File(context.cacheDir, backupFileName)
                createSnapshotArchive(tempZipFile, manifest)
                val backupSize = tempZipFile.length()

                api.uploadFile(
                    "${YandexDiskApi.BACKUP_FOLDER_PATH}/$backupFileName",
                    tempZipFile,
                    "application/zip"
                )

                // 3. Опись рядом со снимком — чтобы чистить медиа, не скачивая архивы
                api.uploadText(
                    "${YandexDiskApi.BACKUP_FOLDER_PATH}/$manifestFileName",
                    manifest.serialize()
                )

                onProgress(BackupProgress.Finishing)
                val prefs = context.getSharedPreferences(BackupPrefs.NAME, Context.MODE_PRIVATE)
                if (prefs.getBoolean(BackupPrefs.KEY_AUTO_DELETE, true)) {
                    cleanOldBackups(api, backupFileName, manifest)
                }

                Result.success(
                    BackupInfo(
                        fileName = backupFileName,
                        remotePath = "${YandexDiskApi.BACKUP_FOLDER_PATH}/$backupFileName",
                        timestamp = System.currentTimeMillis(),
                        size = backupSize,
                        uploadedMediaCount = uploadedCount
                    )
                )
            } catch (e: CancellationException) {
                // Воркер сняли — отмену нельзя превращать в обычную ошибку,
                // иначе корутинная машинерия останется в некорректном состоянии
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Раньше временный архив удалялся только после успешной загрузки: каждая
                // неудача оставляла в кэше полноразмерную копию базы со всеми фото
                tempZipFile?.delete()
            }
        }
    }

    /**
     * Восстанавливает данные из резервной копии на Яндекс.Диске.
     *
     * Сначала полностью выкачиваем всё нужное во временную папку и только потом закрываем
     * базу и подменяем файлы. Если сеть отвалится на середине, приложение останется
     * с рабочей базой и прежними данными.
     *
     * @param remotePath путь на Яндекс.Диске, например "disk:/CarLog/CarLog_Backup_xxx.zip"
     */
    suspend fun restoreBackup(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "restore_temp.zip")
        val stagingDir = File(context.cacheDir, "restore_staging")
        try {
            val api = getApi()
            api.downloadFile(remotePath, tempZipFile)

            // База копии должна быть базой Car Log не новее этой версии приложения:
            // копия с более новой схемой роняла бы приложение при каждом запуске.
            // Проверяем до скачивания медиа и до закрытия текущей базы
            val header = BackupDatabaseCheck.headerInZip(tempZipFile) { it == DATABASE_FILE_IN_ZIP }
            BackupDatabaseCheck.failureFor(
                header?.let { BackupDatabaseCheck.verdict(it) } ?: BackupDatabaseCheck.Verdict.NOT_DATABASE
            )?.let { return@withContext Result.failure(it) }

            // Опись есть только у формата v2; её отсутствие означает старый цельный архив
            val manifest = readManifestFromZip(tempZipFile)

            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            if (manifest != null) {
                downloadMediaToStaging(api, manifest, stagingDir)
            }

            // Сетевая часть позади — дальше только локальные файлы
            database.close()
            extractZipArchive(tempZipFile)
            if (manifest != null) {
                moveStagedMedia(stagingDir)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempZipFile.delete()
            stagingDir.deleteRecursively()
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Медиа ===

    /**
     * Локальные фото и PDF: относительный путь внутри архива -> файл.
     * Имена уникальны (`IMG_<uuid>`, `DOC_<uuid>`) и не переиспользуются, поэтому
     * на Диске все они лежат одной плоской папкой.
     */
    private fun collectLocalMedia(): Map<String, File> {
        val media = linkedMapOf<String, File>()
        listOf(PHOTOS_FOLDER, DOCUMENTS_FOLDER).forEach { folder ->
            val dir = File(context.filesDir, folder)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    media["$folder/${file.name}"] = file
                }
            }
        }
        return media
    }

    private suspend fun uploadMissingMedia(
        api: YandexDiskApi,
        localMedia: Map<String, File>,
        onProgress: (BackupProgress) -> Unit
    ): Int {
        if (localMedia.isEmpty()) return 0

        val remoteNames = api.listMediaNames()
        val byBaseName = localMedia.mapKeys { (path, _) -> path.substringAfterLast('/') }
        val toUpload = BackupMediaDiff.toUpload(byBaseName.keys, remoteNames)
        if (toUpload.isEmpty()) return 0

        api.ensureFolderExists(YandexDiskApi.MEDIA_FOLDER_PATH)

        toUpload.forEachIndexed { index, baseName ->
            coroutineContext.ensureActive()
            onProgress(BackupProgress.UploadingMedia(index, toUpload.size))
            val file = byBaseName[baseName] ?: return@forEachIndexed
            // Файл мог быть удалён из приложения, пока шла заливка
            if (!file.exists()) return@forEachIndexed
            api.uploadFile(
                "${YandexDiskApi.MEDIA_FOLDER_PATH}/$baseName",
                file,
                contentTypeFor(baseName)
            )
        }
        return toUpload.size
    }

    private fun contentTypeFor(name: String): String = when {
        name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        else -> "image/jpeg"
    }

    private suspend fun downloadMediaToStaging(
        api: YandexDiskApi,
        manifest: BackupManifest,
        stagingDir: File
    ) {
        manifest.mediaEntries.forEach { entry ->
            coroutineContext.ensureActive()
            val baseName = entry.substringAfterLast('/')
            val target = File(stagingDir, entry)
            // Защита от zip-slip-подобной подмены пути: опись приходит из облака
            if (!target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                return@forEach
            }
            target.parentFile?.mkdirs()
            try {
                api.downloadFile("${YandexDiskApi.MEDIA_FOLDER_PATH}/$baseName", target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Файла на Диске нет — восстанавливаем остальное, запись просто останется без фото.
                // Код достаём сквозь обёртку с шагом и хостом
                if (e.yandexHttpCode != 404) throw e
                target.delete()
            }
        }
    }

    private fun moveStagedMedia(stagingDir: File) {
        listOf(PHOTOS_FOLDER, DOCUMENTS_FOLDER).forEach { folder ->
            val source = File(stagingDir, folder)
            if (!source.exists() || !source.isDirectory) return@forEach
            val target = File(context.filesDir, folder)
            target.mkdirs()
            source.listFiles()?.forEach { file ->
                val destination = File(target, file.name)
                if (!file.renameTo(destination)) {
                    // renameTo не работает между разделами — копируем
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                    file.delete()
                }
            }
        }
    }

    // === Уборка ===

    /**
     * Оставляет [MAX_BACKUPS] последних снимков и удаляет медиа, на которые уже никто
     * не ссылается.
     *
     * Чистка медиа устроена «безопасно по умолчанию»: если хоть одну опись не удалось
     * прочитать (или среди оставленных копий есть старая, без описи), не удаляем ничего.
     * Лишний файл на Диске стоит копейки, недостающий — это фотография, потерянная
     * при восстановлении.
     */
    private fun cleanOldBackups(
        api: YandexDiskApi,
        currentBackupName: String,
        currentManifest: BackupManifest
    ) {
        try {
            val backups = api.listBackups()
            val kept = backups.take(MAX_BACKUPS)

            backups.drop(MAX_BACKUPS).forEach { old ->
                api.deleteFile(old.path)
                runCatching { api.deleteFile(manifestPathFor(old.name)) }
            }

            val referenced = mutableSetOf<String>()
            for (backup in kept) {
                val entries = if (backup.name == currentBackupName) {
                    // Опись только что уехавшего снимка уже есть на руках — не тянем её обратно
                    currentManifest.mediaEntries
                } else {
                    val text = runCatching { api.downloadText(manifestPathFor(backup.name)) }
                        .getOrNull() ?: return
                    BackupManifest.parse(text)?.mediaEntries ?: return
                }
                referenced.addAll(entries.map { it.substringAfterLast('/') })
            }

            val orphans = BackupMediaDiff.orphans(api.listMediaNames(), referenced)
            orphans.forEach { name ->
                runCatching { api.deleteFile("${YandexDiskApi.MEDIA_FOLDER_PATH}/$name") }
            }
        } catch (e: Exception) {
            // Уборка — не повод считать удавшийся бэкап неудавшимся
        }
    }

    /** `CarLog_Backup_<ts>.zip` -> `disk:/CarLog/CarLog_Manifest_<ts>.txt` */
    private fun manifestPathFor(backupFileName: String): String {
        val stamp = backupFileName
            .removePrefix(YandexDiskApi.BACKUP_PREFIX)
            .removeSuffix(".zip")
        return "${YandexDiskApi.BACKUP_FOLDER_PATH}/${YandexDiskApi.MANIFEST_PREFIX}$stamp.txt"
    }

    /** Архивы, брошенные упавшими попытками: раньше они копились в кэше без ограничений. */
    private fun cleanStaleTempFiles() {
        val now = System.currentTimeMillis()
        context.cacheDir.listFiles()?.forEach { file ->
            val isBackupTemp = file.isFile && file.name.startsWith(YandexDiskApi.BACKUP_PREFIX)
            if (isBackupTemp && now - file.lastModified() > STALE_TEMP_AGE_MS) {
                file.delete()
            }
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

    // === Архив ===

    /** Снимок формата v2: база и опись медиа. Сами медиафайлы лежат на Диске отдельно. */
    private fun createSnapshotArchive(zipFile: File, manifest: BackupManifest) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, DATABASE_FILE_IN_ZIP)
            }
            zos.putNextEntry(ZipEntry(BackupManifest.FILE_NAME))
            zos.write(manifest.serialize().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    /** `null` — архив старого формата: медиа лежат внутри него самого. */
    private fun readManifestFromZip(zipFile: File): BackupManifest? {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == BackupManifest.FILE_NAME) {
                    return BackupManifest.parse(zis.readBytes().toString(Charsets.UTF_8))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
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
    val size: Long,
    /** Сколько медиафайлов реально уехало в этот раз — для журнала бэкапов */
    val uploadedMediaCount: Int = 0
)
