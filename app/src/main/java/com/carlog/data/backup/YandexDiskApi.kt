package com.carlog.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Клиент Яндекс.Диск REST API.
 * Документация: https://yandex.ru/dev/disk/api/reference/
 */
class YandexDiskApi(private val token: String) {

    companion object {
        private const val BASE_URL = "https://cloud-api.yandex.net/v1/disk"
        const val BACKUP_FOLDER_PATH = "disk:/CarLog"
        const val BACKUP_PREFIX = "CarLog_Backup_"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun authHeader() = "OAuth $token"

    private fun encode(path: String) = URLEncoder.encode(path, "UTF-8")

    /**
     * Создаёт папку CarLog на Яндекс.Диске.
     * 409 Conflict (уже существует) — допустимый ответ, игнорируем.
     */
    fun ensureFolderExists() {
        val request = Request.Builder()
            .url("$BASE_URL/resources?path=${encode(BACKUP_FOLDER_PATH)}")
            .header("Authorization", authHeader())
            .put("".toRequestBody())
            .build()
        client.newCall(request).execute().close()
    }

    /**
     * Загружает файл на Яндекс.Диск в папку CarLog.
     * Шаг 1: получаем URL для загрузки.
     * Шаг 2: PUT файла по этому URL.
     */
    fun uploadFile(fileName: String, data: ByteArray): Boolean {
        val path = "$BACKUP_FOLDER_PATH/$fileName"

        val urlRequest = Request.Builder()
            .url("$BASE_URL/resources/upload?path=${encode(path)}&overwrite=true")
            .header("Authorization", authHeader())
            .get()
            .build()

        val uploadUrl = client.newCall(urlRequest).execute().use { response ->
            if (!response.isSuccessful) return false
            JSONObject(response.body!!.string()).getString("href")
        }

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .put(data.toRequestBody("application/zip".toMediaType()))
            .build()

        return client.newCall(uploadRequest).execute().use { it.isSuccessful }
    }

    /**
     * Возвращает список резервных копий, отсортированных от новых к старым.
     */
    fun listBackups(): List<YandexBackupFile> {
        val url = "$BASE_URL/resources" +
                "?path=${encode(BACKUP_FOLDER_PATH)}" +
                "&limit=100"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Ошибка ${response.code}: ${response.body?.string()}")

            val json = JSONObject(response.body!!.string())
            val items = json.getJSONObject("_embedded").getJSONArray("items")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

            val result = mutableListOf<YandexBackupFile>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val name = item.getString("name")
                if (!name.startsWith(BACKUP_PREFIX)) continue
                val timestamp = sdf.parse(item.getString("modified"))?.time ?: 0L
                result.add(
                    YandexBackupFile(
                        name = name,
                        path = item.getString("path"),
                        timestamp = timestamp,
                        size = item.optLong("size", 0)
                    )
                )
            }
            result.sortedByDescending { it.timestamp }
        }
    }

    /**
     * Скачивает файл с Яндекс.Диска.
     * Шаг 1: получаем URL для скачивания.
     * Шаг 2: GET по этому URL.
     */
    fun downloadFile(path: String): ByteArray {
        val urlRequest = Request.Builder()
            .url("$BASE_URL/resources/download?path=${encode(path)}")
            .header("Authorization", authHeader())
            .get()
            .build()

        val downloadUrl = client.newCall(urlRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Ошибка получения ссылки: ${response.code}")
            JSONObject(response.body!!.string()).getString("href")
        }

        val downloadRequest = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        return client.newCall(downloadRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Ошибка скачивания файла")
            response.body!!.bytes()
        }
    }

    /**
     * Удаляет файл с Яндекс.Диска безвозвратно.
     */
    fun deleteFile(path: String): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/resources?path=${encode(path)}&permanently=true")
            .header("Authorization", authHeader())
            .delete()
            .build()
        return client.newCall(request).execute().use { it.isSuccessful || it.code == 404 }
    }
}

data class YandexBackupFile(
    val name: String,
    val path: String,     // disk:/CarLog/CarLog_Backup_xxx.zip
    val timestamp: Long,
    val size: Long
)
