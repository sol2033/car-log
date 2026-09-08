package com.carlog.data.backup

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Яндекс.Диск ответил ошибкой. Код нужен, чтобы отличить временную неполадку
 * (5xx, 429 — повторим) от постоянной (401 — токен протух, повторять бессмысленно).
 */
class YandexDiskException(val code: Int, val details: String? = null) :
    Exception("Yandex.Disk HTTP $code${details?.let { ": $it" } ?: ""}")

/** Яндекс.Диск не подключён — нет сохранённого OAuth-токена. */
class YandexNotConnectedException : Exception("Yandex.Disk is not connected")

/**
 * Шаг работы с Яндекс.Диском.
 *
 * Знать шаг критично, потому что **шаги ходят на разные хосты**: всё API живёт на
 * `cloud-api.yandex.net`, а сам файл заливается на отдельный `uploader*.disk.yandex.net`,
 * ссылку на который API выдаёт отдельным запросом. Сбой только на [UPLOAD] при работающих
 * остальных шагах означает, что до хоста загрузчика из этой сети не достучаться, —
 * без разделения по шагам такую картину в журнале не отличить от «просто нет интернета».
 */
enum class BackupPhase {
    /** Создание папки disk:/CarLog */
    FOLDER,

    /** Запрос ссылки для загрузки (cloud-api) */
    UPLOAD_URL,

    /** Собственно заливка файла (хост загрузчика) */
    UPLOAD,

    /** Листинг папки */
    LIST,

    /** Запрос ссылки для скачивания (cloud-api) */
    DOWNLOAD_URL,

    /** Скачивание файла */
    DOWNLOAD,

    /** Удаление файла */
    DELETE
}

/**
 * Сетевой сбой с указанием шага и хоста. Оборачивает исходное исключение,
 * не подменяя его: категорию [BackupErrorClassifier] по-прежнему определяет по причине.
 */
class BackupNetworkException(
    val phase: BackupPhase,
    val host: String?,
    override val cause: Throwable
) : Exception("${phase.name}${host?.let { " @$it" }.orEmpty()}: ${cause.message}", cause)

/**
 * HTTP-код Яндекса сквозь обёртку [BackupNetworkException].
 * Нужен там, где отдельный код — не ошибка по существу: например, 404 при листинге папки
 * медиа означает всего лишь «медиа ещё ни разу не заливали».
 */
val Throwable.yandexHttpCode: Int?
    get() = when (this) {
        is YandexDiskException -> code
        is BackupNetworkException -> cause.yandexHttpCode
        else -> null
    }

/**
 * Категория ошибки бэкапа.
 *
 * [isTransient] — единственное, что решает, повторять ли попытку. Раньше любая ошибка
 * означала `Result.failure()`, и разовый сетевой затык навсегда оставлял пользователя
 * без копии (см. §13.2 техдока).
 */
enum class BackupErrorType(val isTransient: Boolean) {
    /** Нет сети, DNS не резолвится, соединение оборвалось */
    NO_NETWORK(true),

    /** Обмен встал: сработал connect/read/write timeout OkHttp */
    TIMEOUT(true),

    /** 5xx или 429 со стороны Яндекса */
    SERVER(true),

    /** Токен не сохранён — пользователь не подключал Яндекс.Диск */
    NOT_CONNECTED(false),

    /** 401/403 — токен протух или отозван, нужна повторная авторизация */
    UNAUTHORIZED(false),

    /** 507 — на Яндекс.Диске закончилось место */
    QUOTA_EXCEEDED(false),

    /** 404 — копию удалили с Диска, пока пользователь смотрел на список */
    NOT_FOUND(false),

    /** Всё остальное: не повторяем, чтобы не молотить впустую по ошибке в коде */
    UNKNOWN(false)
}

/**
 * Раскладывает исключение по категориям. Чистая функция без Android — тестируется на JVM.
 *
 * Порядок проверок важен: SocketTimeoutException наследуется от InterruptedIOException,
 * а тот — от IOException, так что таймауты разбираем до общего сетевого случая.
 */
object BackupErrorClassifier {

    fun classify(error: Throwable?): BackupErrorType = when (error) {
        null -> BackupErrorType.UNKNOWN
        // Обёртка несёт шаг и хост для журнала, категорию определяет исходная причина
        is BackupNetworkException -> classify(error.cause)
        is YandexNotConnectedException -> BackupErrorType.NOT_CONNECTED
        is YandexDiskException -> classifyHttp(error.code)
        // Именно сюда попадает "timeout" из Okio, с которого начался разбор
        is SocketTimeoutException, is InterruptedIOException -> BackupErrorType.TIMEOUT
        is IOException -> BackupErrorType.NO_NETWORK
        else -> BackupErrorType.UNKNOWN
    }

    private fun classifyHttp(code: Int): BackupErrorType = when {
        code == 401 || code == 403 -> BackupErrorType.UNAUTHORIZED
        code == 404 -> BackupErrorType.NOT_FOUND
        code == 507 -> BackupErrorType.QUOTA_EXCEEDED
        code == 429 -> BackupErrorType.SERVER
        code >= 500 -> BackupErrorType.SERVER
        else -> BackupErrorType.UNKNOWN
    }
}

/**
 * Сколько раз повторять и когда сдаваться.
 *
 * Уведомление об ошибке показываем только когда попытки исчерпаны ([isLastAttempt]) —
 * иначе пользователь получил бы пять одинаковых уведомлений вместо одного.
 */
object BackupRetryPolicy {

    /** Всего попыток, включая первую. WorkManager разводит их экспоненциальной паузой. */
    const val MAX_ATTEMPTS = 5

    /** Стартовая пауза перед повтором; дальше WorkManager удваивает её сам. */
    const val BACKOFF_SECONDS = 30L

    fun shouldRetry(type: BackupErrorType, runAttemptCount: Int): Boolean =
        type.isTransient && runAttemptCount + 1 < MAX_ATTEMPTS

    fun isLastAttempt(type: BackupErrorType, runAttemptCount: Int): Boolean =
        !shouldRetry(type, runAttemptCount)
}
