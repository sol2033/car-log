package com.carlog.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Клиент Яндекс.Диск REST API.
 * Документация: https://yandex.ru/dev/disk/api/reference/
 *
 * Все неуспешные ответы поднимаются как [YandexDiskException] с HTTP-кодом: по коду
 * [BackupErrorClassifier] решает, временная это неполадка (повторим) или постоянная.
 */
class YandexDiskApi(private val token: String) {

    companion object {
        private const val BASE_URL = "https://cloud-api.yandex.net/v1/disk"
        const val BACKUP_FOLDER_PATH = "disk:/CarLog"

        /** Фото и PDF лежат отдельно от снимков и переиспользуются между копиями */
        const val MEDIA_FOLDER_PATH = "$BACKUP_FOLDER_PATH/media"

        const val BACKUP_PREFIX = "CarLog_Backup_"

        /** Опись снимка, продублированная рядом с ним: по ней чистим осиротевшие медиафайлы */
        const val MANIFEST_PREFIX = "CarLog_Manifest_"

        /** За сколько записей просим листинг: почти всегда хватает одного запроса */
        private const val PAGE_SIZE = 1000

        /** Хост API — отличается от хоста, на который заливается сам файл */
        const val API_HOST = "cloud-api.yandex.net"

        /** Сколько раз перезапрашиваем ссылку на загрузку, надеясь получить другой загрузчик */
        private const val UPLOAD_ATTEMPTS = 3
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun authHeader() = "OAuth $token"

    private fun encode(path: String) = URLEncoder.encode(path, "UTF-8")

    private fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host

    /**
     * Помечает сбой шагом и хостом — по этой паре в журнале видно, что именно недоступно.
     * Исходное исключение сохраняется причиной, категорию ошибки определяет оно.
     */
    private inline fun <T> withPhase(phase: BackupPhase, host: String?, block: () -> T): T = try {
        block()
    } catch (e: BackupNetworkException) {
        throw e
    } catch (e: YandexDiskException) {
        throw BackupNetworkException(phase, host, e)
    } catch (e: IOException) {
        throw BackupNetworkException(phase, host, e)
    }

    /** Тело ответа читаем один раз: повторный вызов string() вернул бы пустоту */
    private fun Response.requireSuccess(): Response {
        if (!isSuccessful) {
            val details = try {
                body?.string()
            } catch (e: Exception) {
                null
            }
            close()
            throw YandexDiskException(code, details?.take(200))
        }
        return this
    }

    /**
     * Создаёт папку на Яндекс.Диске.
     * 409 Conflict (уже существует) — допустимый ответ, игнорируем.
     * Остальные ошибки поднимаем: молча проглоченный 401 всплывал позже и не там.
     */
    fun ensureFolderExists(path: String = BACKUP_FOLDER_PATH) {
        withPhase(BackupPhase.FOLDER, API_HOST) {
            val request = Request.Builder()
                .url("$BASE_URL/resources?path=${encode(path)}")
                .header("Authorization", authHeader())
                .put("".toRequestBody())
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 409) response.requireSuccess()
            }
        }
    }

    /**
     * Загружает файл на Яндекс.Диск.
     * Шаг 1: получаем URL для загрузки. Шаг 2: PUT файла по этому URL.
     *
     * @param remotePath полный путь на Диске, например `disk:/CarLog/media/IMG_1.jpg`
     */
    fun uploadFile(remotePath: String, file: File, contentType: String = "application/octet-stream") {
        // Потоковая загрузка файла — архив не читается в память целиком
        uploadWithFreshHref(remotePath) { file.asRequestBody(contentType.toMediaType()) }
    }

    /** Загружает короткий текст (опись) как файл на Диск. */
    fun uploadText(remotePath: String, text: String) {
        uploadWithFreshHref(remotePath) {
            text.toRequestBody("text/plain; charset=utf-8".toMediaType())
        }
    }

    /**
     * Заливает тело по ссылке, полученной от API, и при сетевом сбое **перезапрашивает
     * ссылку заново**, а не долбится в ту же.
     *
     * Смысл: ссылка ведёт на конкретный хост загрузчика (`uploader*.disk.yandex.net`),
     * отдельный от `cloud-api.yandex.net`, с которым работает всё остальное API. Если
     * до этого конкретного загрузчика из сети пользователя не достучаться, повтор по той же
     * ссылке гарантированно упрётся в то же место, а новая ссылка может указать на другой хост.
     *
     * HTTP-отказы (нет места, нет прав) так не лечатся — их поднимаем сразу, кроме 5xx.
     */
    private fun uploadWithFreshHref(remotePath: String, body: () -> RequestBody) {
        var lastError: Exception? = null

        repeat(UPLOAD_ATTEMPTS) {
            val uploadUrl = try {
                resolveUploadUrl(remotePath)
            } catch (e: Exception) {
                lastError = e
                return@repeat
            }

            try {
                val request = Request.Builder().url(uploadUrl).put(body()).build()
                client.newCall(request).execute().use { it.requireSuccess() }
                return
            } catch (e: YandexDiskException) {
                val wrapped = BackupNetworkException(BackupPhase.UPLOAD, hostOf(uploadUrl), e)
                // 4xx и 507 детерминированы — другой хост загрузчика их не изменит
                if (e.code < 500) throw wrapped
                lastError = wrapped
            } catch (e: IOException) {
                lastError = BackupNetworkException(BackupPhase.UPLOAD, hostOf(uploadUrl), e)
            }
        }

        throw lastError ?: BackupNetworkException(
            BackupPhase.UPLOAD,
            null,
            IOException("upload failed")
        )
    }

    private fun resolveUploadUrl(remotePath: String): String =
        withPhase(BackupPhase.UPLOAD_URL, API_HOST) {
            val urlRequest = Request.Builder()
                .url("$BASE_URL/resources/upload?path=${encode(remotePath)}&overwrite=true")
                .header("Authorization", authHeader())
                .get()
                .build()

            client.newCall(urlRequest).execute().use { response ->
                JSONObject(response.requireSuccess().body!!.string()).getString("href")
            }
        }

    /**
     * Возвращает список резервных копий, отсортированных от новых к старым.
     * Описи (`CarLog_Manifest_*`) сюда не попадают — у них другой префикс.
     */
    fun listBackups(): List<YandexBackupFile> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val fields = "_embedded.items.name,_embedded.items.path," +
                "_embedded.items.modified,_embedded.items.size,_embedded.total"

        return listFolderItems(BACKUP_FOLDER_PATH, fields)
            .mapNotNull { item ->
                val name = item.optString("name")
                if (!name.startsWith(BACKUP_PREFIX)) return@mapNotNull null
                YandexBackupFile(
                    name = name,
                    path = item.optString("path"),
                    timestamp = runCatching { sdf.parse(item.optString("modified"))?.time }
                        .getOrNull() ?: 0L,
                    size = item.optLong("size", 0)
                )
            }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Имена медиафайлов, уже лежащих на Диске.
     * Запрашиваем только имена (`fields`) — при трёх сотнях фото это заметно экономит
     * трафик на каждом бэкапе, а больше от листинга ничего и не нужно.
     * Папки может ещё не быть — это не ошибка, значит медиа не заливали ни разу.
     */
    fun listMediaNames(): Set<String> = try {
        listFolderItems(MEDIA_FOLDER_PATH, "_embedded.items.name,_embedded.total")
            .map { it.optString("name") }
            .filter { it.isNotEmpty() }
            .toSet()
    } catch (e: Exception) {
        // Код достаём сквозь обёртку с шагом и хостом
        if (e.yandexHttpCode == 404) emptySet() else throw e
    }

    /** Постраничный обход папки: за раз Яндекс отдаёт ограниченное число записей. */
    private fun listFolderItems(path: String, fields: String): List<JSONObject> =
        withPhase(BackupPhase.LIST, API_HOST) {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val url = "$BASE_URL/resources" +
                    "?path=${encode(path)}" +
                    "&limit=$PAGE_SIZE" +
                    "&offset=$offset" +
                    "&fields=${encode(fields)}"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader())
                .get()
                .build()

            val page = client.newCall(request).execute().use { response ->
                JSONObject(response.requireSuccess().body!!.string())
            }

            val embedded = page.optJSONObject("_embedded") ?: break
            val items = embedded.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                result.add(items.getJSONObject(i))
            }

            // total может не прийти, если Яндекс подрежет поля — тогда полагаемся на размер страницы
            val total = embedded.optInt("total", result.size)
            offset += items.length()
            if (offset >= total) break
        }
        result
    }

    /**
     * Скачивает файл с Яндекс.Диска.
     * Шаг 1: получаем URL для скачивания. Шаг 2: GET по этому URL.
     */
    fun downloadFile(path: String, target: File) {
        val downloadUrl = resolveDownloadUrl(path)
        withPhase(BackupPhase.DOWNLOAD, hostOf(downloadUrl)) {
            val downloadRequest = Request.Builder().url(downloadUrl).get().build()

            // Потоковое сохранение в файл — архив не читается в память целиком
            client.newCall(downloadRequest).execute().use { response ->
                response.requireSuccess().body!!.byteStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /** Скачивает небольшой текстовый файл (опись) целиком в память. */
    fun downloadText(path: String): String {
        val downloadUrl = resolveDownloadUrl(path)
        return withPhase(BackupPhase.DOWNLOAD, hostOf(downloadUrl)) {
            val request = Request.Builder().url(downloadUrl).get().build()
            client.newCall(request).execute().use { response ->
                response.requireSuccess().body!!.string()
            }
        }
    }

    private fun resolveDownloadUrl(path: String): String =
        withPhase(BackupPhase.DOWNLOAD_URL, API_HOST) {
            val urlRequest = Request.Builder()
                .url("$BASE_URL/resources/download?path=${encode(path)}")
                .header("Authorization", authHeader())
                .get()
                .build()

            client.newCall(urlRequest).execute().use { response ->
                JSONObject(response.requireSuccess().body!!.string()).getString("href")
            }
        }

    /**
     * Удаляет файл с Яндекс.Диска безвозвратно.
     * 404 считаем успехом: файла и так нет — цель достигнута.
     */
    fun deleteFile(path: String) {
        withPhase(BackupPhase.DELETE, API_HOST) {
            val request = Request.Builder()
                .url("$BASE_URL/resources?path=${encode(path)}&permanently=true")
                .header("Authorization", authHeader())
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 404) response.requireSuccess()
            }
        }
    }
}

data class YandexBackupFile(
    val name: String,
    val path: String,     // disk:/CarLog/CarLog_Backup_xxx.zip
    val timestamp: Long,
    val size: Long
)
