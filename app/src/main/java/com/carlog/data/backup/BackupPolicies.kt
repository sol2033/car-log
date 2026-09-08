package com.carlog.data.backup

import java.util.concurrent.TimeUnit

/**
 * Догоняющий бэкап при запуске приложения.
 *
 * Фоновая задача может не доехать: прошивки Huawei, Xiaomi и им подобных морозят фоновые
 * процессы, а WorkManager отдаёт задаче не больше десяти минут. Пока приложение открыто и
 * на экране, помешать этому прошивка не может — поэтому на старте проверяем, не осталось
 * ли изменений, которые так и не уехали в облако.
 */
object BackupCatchUpPolicy {

    /**
     * Насколько «протухшими» должны быть изменения, чтобы догонять.
     * Больше, чем дебаунс автобэкапа (5 минут), иначе догоняющий запуск наступал бы
     * на обычный автоматический и они дублировали бы работу.
     */
    val MIN_AGE_MS: Long = TimeUnit.MINUTES.toMillis(15)

    /**
     * @param lastDataChange когда последний раз меняли данные (0 — не меняли)
     * @param lastBackup когда последняя копия успешно уехала (0 — копий нет)
     */
    fun shouldRun(
        autoBackupEnabled: Boolean,
        isConnected: Boolean,
        lastDataChange: Long,
        lastBackup: Long,
        now: Long,
        minAgeMs: Long = MIN_AGE_MS
    ): Boolean {
        if (!autoBackupEnabled || !isConnected) return false
        // Нечего сохранять: с момента последней копии данные не трогали
        if (lastDataChange <= lastBackup) return false
        // Свежую правку доверяем обычному автобэкапу — он уже запланирован
        return now - lastDataChange >= minAgeMs
    }
}

/**
 * Подсказка «разрешите работу в фоне без ограничений».
 *
 * Речь только про штатную оптимизацию батареи Android (Doze), которая откладывает фоновые
 * задачи при погашенном экране. Собственные менеджеры фона производителей сознательно не
 * трогаем: публичного API для них нет, их экраны недокументированы и разъезжаются между
 * версиями прошивок.
 *
 * Показываем не всем подряд, а только когда сбои действительно повторяются: иначе мы пугали бы
 * людей проблемой, которой у них нет, и просили бы разрешение без повода.
 */
object BatteryOptimizationHintPolicy {

    /** Сколько окончательных сбоев подряд терпим, прежде чем предлагать снять ограничение */
    const val FAILURE_THRESHOLD = 2

    /**
     * @param alreadyExempt приложение уже исключено из оптимизации — просить нечего
     */
    fun shouldShow(
        consecutiveFailures: Int,
        alreadyExempt: Boolean,
        dismissed: Boolean
    ): Boolean {
        if (dismissed || alreadyExempt) return false
        return consecutiveFailures >= FAILURE_THRESHOLD
    }
}
