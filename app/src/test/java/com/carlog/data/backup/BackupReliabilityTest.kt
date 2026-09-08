package com.carlog.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Регрессия на историю, с которой началась переделка бэкапа: пользователь получил
 * уведомление «Ошибка резервного копирования / timeout», после чего копия не создавалась
 * вовсе — воркер возвращал `Result.failure()`, и разовый сетевой затык хоронил её насовсем.
 */
class BackupErrorClassifierTest {

    @Test
    fun `таймаут OkHttp распознаётся как временная ошибка`() {
        // Ровно то исключение, чей message пользователь увидел в уведомлении
        val type = BackupErrorClassifier.classify(SocketTimeoutException("timeout"))

        assertEquals(BackupErrorType.TIMEOUT, type)
        assertTrue("таймаут обязан повторяться", type.isTransient)
    }

    @Test
    fun `прерванный обмен Okio — тоже таймаут`() {
        assertEquals(
            BackupErrorType.TIMEOUT,
            BackupErrorClassifier.classify(InterruptedIOException("timeout"))
        )
    }

    @Test
    fun `нет сети — временная ошибка`() {
        assertEquals(
            BackupErrorType.NO_NETWORK,
            BackupErrorClassifier.classify(UnknownHostException("cloud-api.yandex.net"))
        )
        assertEquals(
            BackupErrorType.NO_NETWORK,
            BackupErrorClassifier.classify(IOException("unexpected end of stream"))
        )
    }

    @Test
    fun `протухший токен повторять бессмысленно`() {
        val type = BackupErrorClassifier.classify(YandexDiskException(401))

        assertEquals(BackupErrorType.UNAUTHORIZED, type)
        assertFalse(type.isTransient)
    }

    @Test
    fun `кончилось место на Диске — постоянная ошибка`() {
        val type = BackupErrorClassifier.classify(YandexDiskException(507))

        assertEquals(BackupErrorType.QUOTA_EXCEEDED, type)
        assertFalse(type.isTransient)
    }

    @Test
    fun `сбой на стороне Яндекса — временная ошибка`() {
        assertTrue(BackupErrorClassifier.classify(YandexDiskException(500)).isTransient)
        assertTrue(BackupErrorClassifier.classify(YandexDiskException(503)).isTransient)
        // Слишком частые запросы — тоже повод подождать и повторить
        assertEquals(
            BackupErrorType.SERVER,
            BackupErrorClassifier.classify(YandexDiskException(429))
        )
    }

    @Test
    fun `не подключённый Диск не повторяем`() {
        val type = BackupErrorClassifier.classify(YandexNotConnectedException())

        assertEquals(BackupErrorType.NOT_CONNECTED, type)
        assertFalse(type.isTransient)
    }

    @Test
    fun `неизвестную ошибку не молотим повторами`() {
        val type = BackupErrorClassifier.classify(IllegalStateException("баг в коде"))

        assertEquals(BackupErrorType.UNKNOWN, type)
        assertFalse(type.isTransient)
    }

    @Test
    fun `отсутствие исключения не роняет классификатор`() {
        assertEquals(BackupErrorType.UNKNOWN, BackupErrorClassifier.classify(null))
    }

    @Test
    fun `обёртка с шагом и хостом не подменяет категорию`() {
        // Шаг и хост нужны журналу, но решение «повторять или нет» принимается по причине
        val wrapped = BackupNetworkException(
            phase = BackupPhase.UPLOAD,
            host = "uploader1g.disk.yandex.net",
            cause = SocketTimeoutException("timeout")
        )

        assertEquals(BackupErrorType.TIMEOUT, BackupErrorClassifier.classify(wrapped))
        assertTrue(BackupErrorClassifier.classify(wrapped).isTransient)
    }

    @Test
    fun `HTTP-код достаётся сквозь обёртку`() {
        // На этом держится «404 при листинге медиа — это не ошибка, а пустая папка»
        val wrapped = BackupNetworkException(
            phase = BackupPhase.LIST,
            host = YandexDiskApi.API_HOST,
            cause = YandexDiskException(404)
        )

        assertEquals(404, wrapped.yandexHttpCode)
        assertEquals(BackupErrorType.NOT_FOUND, BackupErrorClassifier.classify(wrapped))
    }
}

class BackupRetryPolicyTest {

    @Test
    fun `временная ошибка повторяется до исчерпания попыток`() {
        val timeout = BackupErrorType.TIMEOUT

        // Попытки 0..3 — ещё повторяем
        for (attempt in 0 until BackupRetryPolicy.MAX_ATTEMPTS - 1) {
            assertTrue(
                "попытка $attempt должна повториться",
                BackupRetryPolicy.shouldRetry(timeout, attempt)
            )
        }
        // Пятая (индекс 4) — последняя, дальше сдаёмся
        assertFalse(
            BackupRetryPolicy.shouldRetry(timeout, BackupRetryPolicy.MAX_ATTEMPTS - 1)
        )
    }

    @Test
    fun `постоянная ошибка не повторяется даже с первой попытки`() {
        assertFalse(BackupRetryPolicy.shouldRetry(BackupErrorType.UNAUTHORIZED, 0))
        assertFalse(BackupRetryPolicy.shouldRetry(BackupErrorType.QUOTA_EXCEEDED, 0))
        assertFalse(BackupRetryPolicy.shouldRetry(BackupErrorType.NOT_CONNECTED, 0))
    }

    @Test
    fun `уведомление показывается только на последней попытке`() {
        // Промежуточные сбои молчат — иначе пользователь получил бы пять уведомлений подряд
        assertFalse(BackupRetryPolicy.isLastAttempt(BackupErrorType.TIMEOUT, 0))
        assertFalse(BackupRetryPolicy.isLastAttempt(BackupErrorType.TIMEOUT, 3))
        assertTrue(BackupRetryPolicy.isLastAttempt(BackupErrorType.TIMEOUT, 4))

        // А постоянную ошибку показываем сразу: ждать нечего
        assertTrue(BackupRetryPolicy.isLastAttempt(BackupErrorType.UNAUTHORIZED, 0))
    }
}

class BackupManifestTest {

    @Test
    fun `опись переживает сериализацию`() {
        val manifest = BackupManifest(
            listOf("photos/IMG_a.jpg", "photos/IMG_b.jpg", "documents/DOC_c.pdf")
        )

        val parsed = BackupManifest.parse(manifest.serialize())

        assertEquals(manifest.mediaEntries, parsed?.mediaEntries)
    }

    @Test
    fun `пустая опись — это валидная опись, а не старый формат`() {
        val parsed = BackupManifest.parse(BackupManifest(emptyList()).serialize())

        assertEquals(emptyList<String>(), parsed?.mediaEntries)
    }

    @Test
    fun `архив старого формата описью не притворяется`() {
        // До 1.2.3 фото лежали внутри самого архива, файла manifest.txt там нет;
        // если такой архив всё же попадётся, разбирать его надо по-старому
        assertNull(BackupManifest.parse(""))
        assertNull(BackupManifest.parse("photos/IMG_a.jpg\nphotos/IMG_b.jpg"))
        assertNull(BackupManifest.parse("carlog-backup-v1\nphotos/IMG_a.jpg"))
    }
}

class BackupMediaDiffTest {

    @Test
    fun `повторно заливаются только новые файлы`() {
        val local = listOf("IMG_a.jpg", "IMG_b.jpg", "IMG_c.jpg")
        val remote = setOf("IMG_a.jpg", "IMG_b.jpg")

        assertEquals(listOf("IMG_c.jpg"), BackupMediaDiff.toUpload(local, remote))
    }

    @Test
    fun `когда всё уже на Диске — заливать нечего`() {
        val local = listOf("IMG_a.jpg", "IMG_b.jpg")

        // Именно ради этого случая всё и затевалось: обычный бэкап после правки пробега
        // не должен тащить фотографии заново
        assertTrue(BackupMediaDiff.toUpload(local, local.toSet()).isEmpty())
    }

    @Test
    fun `сироты считаются по всем сохраняемым копиям, а не по последней`() {
        val remote = setOf("IMG_old.jpg", "IMG_new.jpg", "IMG_gone.jpg")
        // IMG_old ещё нужен позавчерашней копии, хотя из приложения фото уже удалили
        val referenced = setOf("IMG_new.jpg", "IMG_old.jpg")

        assertEquals(listOf("IMG_gone.jpg"), BackupMediaDiff.orphans(remote, referenced))
    }
}

class BackupEventLogTest {

    private fun entry(
        outcome: BackupOutcome,
        timestamp: Long = 0L,
        errorType: BackupErrorType? = null
    ) = BackupLogEntry(
        timestamp = timestamp,
        trigger = BackupTrigger.AUTO,
        outcome = outcome,
        errorType = errorType,
        attempt = 0,
        sizeBytes = 0,
        uploadedMediaCount = 0
    )

    @Test
    fun `журнал не растёт бесконечно`() {
        var log = emptyList<BackupLogEntry>()
        repeat(BackupEventLog.MAX_ENTRIES + 5) { i ->
            log = BackupEventLog.append(log, entry(BackupOutcome.SUCCESS, timestamp = i.toLong()))
        }

        assertEquals(BackupEventLog.MAX_ENTRIES, log.size)
        // Свежая запись — первая, самые старые вытеснены
        assertEquals((BackupEventLog.MAX_ENTRIES + 4).toLong(), log.first().timestamp)
    }

    @Test
    fun `запись журнала переживает сериализацию`() {
        val original = BackupLogEntry(
            timestamp = 1_700_000_000_000,
            trigger = BackupTrigger.MANUAL,
            outcome = BackupOutcome.FAILURE,
            errorType = BackupErrorType.TIMEOUT,
            attempt = 4,
            sizeBytes = 512_000,
            uploadedMediaCount = 7,
            phase = BackupPhase.UPLOAD,
            host = "uploader1g.disk.yandex.net",
            errorClass = "SocketTimeoutException",
            durationMs = 91_000
        )

        val restored = BackupEventLog.parse(BackupEventLog.serialize(listOf(original)))

        assertEquals(listOf(original), restored)
    }

    @Test
    fun `битые строки журнала пропускаются`() {
        val good = entry(BackupOutcome.SUCCESS, timestamp = 42)

        val restored = BackupEventLog.parse("мусор\n\n${good.serialize()}\n1|2|3")

        assertEquals(listOf(good), restored)
    }

    @Test
    fun `подряд идущие сбои считаются до первого успеха`() {
        val log = listOf(
            entry(BackupOutcome.FAILURE, errorType = BackupErrorType.TIMEOUT),
            entry(BackupOutcome.FAILURE, errorType = BackupErrorType.TIMEOUT),
            entry(BackupOutcome.SUCCESS),
            entry(BackupOutcome.FAILURE, errorType = BackupErrorType.TIMEOUT)
        )

        assertEquals(2, BackupEventLog.consecutiveFailures(log))
    }

    @Test
    fun `промежуточные повторы не считаются сбоями`() {
        // RETRY — это ещё не приговор: попытка назначена, пользователю ничего не показывали
        val log = listOf(
            entry(BackupOutcome.RETRY, errorType = BackupErrorType.TIMEOUT),
            entry(BackupOutcome.RETRY, errorType = BackupErrorType.TIMEOUT),
            entry(BackupOutcome.SUCCESS)
        )

        assertEquals(0, BackupEventLog.consecutiveFailures(log))
    }
}

class BackupCatchUpPolicyTest {

    private val now = 1_700_000_000_000
    private val long_ago = now - TimeUnit.HOURS.toMillis(3)

    @Test
    fun `догоняем, если изменения так и не уехали`() {
        assertTrue(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = true,
                isConnected = true,
                lastDataChange = long_ago,
                lastBackup = long_ago - TimeUnit.HOURS.toMillis(1),
                now = now
            )
        )
    }

    @Test
    fun `не догоняем, когда с последней копии данные не трогали`() {
        assertFalse(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = true,
                isConnected = true,
                lastDataChange = long_ago,
                lastBackup = long_ago + TimeUnit.MINUTES.toMillis(1),
                now = now
            )
        )
    }

    @Test
    fun `свежую правку оставляем обычному автобэкапу`() {
        // Изменение минуту назад — по нему уже запланирована задача с дебаунсом в 5 минут,
        // догоняющий запуск только продублировал бы работу
        assertFalse(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = true,
                isConnected = true,
                lastDataChange = now - TimeUnit.MINUTES.toMillis(1),
                lastBackup = 0,
                now = now
            )
        )
    }

    @Test
    fun `без подключения и с выключенным автосохранением не догоняем`() {
        assertFalse(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = false,
                isConnected = true,
                lastDataChange = long_ago,
                lastBackup = 0,
                now = now
            )
        )
        assertFalse(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = true,
                isConnected = false,
                lastDataChange = long_ago,
                lastBackup = 0,
                now = now
            )
        )
    }

    @Test
    fun `первая копия при непустых данных всё же делается`() {
        // lastBackup == 0: копий ещё не было, а данные уже вносили
        assertTrue(
            BackupCatchUpPolicy.shouldRun(
                autoBackupEnabled = true,
                isConnected = true,
                lastDataChange = long_ago,
                lastBackup = 0,
                now = now
            )
        )
    }
}

class BatteryOptimizationHintPolicyTest {

    @Test
    fun `подсказка появляется после повторных сбоев`() {
        assertTrue(
            BatteryOptimizationHintPolicy.shouldShow(
                consecutiveFailures = 2,
                alreadyExempt = false,
                dismissed = false
            )
        )
    }

    @Test
    fun `после единственного сбоя никого не тревожим`() {
        // Разовый сбой лечится ретраем — просить разрешение из-за него незачем
        assertFalse(
            BatteryOptimizationHintPolicy.shouldShow(
                consecutiveFailures = 1,
                alreadyExempt = false,
                dismissed = false
            )
        )
        assertFalse(
            BatteryOptimizationHintPolicy.shouldShow(
                consecutiveFailures = 0,
                alreadyExempt = false,
                dismissed = false
            )
        )
    }

    @Test
    fun `если ограничение уже снято — просить нечего`() {
        assertFalse(
            BatteryOptimizationHintPolicy.shouldShow(
                consecutiveFailures = 9,
                alreadyExempt = true,
                dismissed = false
            )
        )
    }

    @Test
    fun `закрытую подсказку больше не показываем`() {
        assertFalse(
            BatteryOptimizationHintPolicy.shouldShow(
                consecutiveFailures = 9,
                alreadyExempt = false,
                dismissed = true
            )
        )
    }
}
