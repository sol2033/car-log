package com.carlog.util

import java.time.temporal.ChronoUnit

/**
 * Статус-«светофор» документа по сроку действия (аналог [ConsumableStatus] для расходников)
 */
object DocumentStatus {

    enum class Status {
        NORMAL,   // Зелёный — больше 30 дней до окончания
        WARNING,  // Жёлтый — 30 дней и меньше
        CRITICAL  // Красный — 7 дней и меньше или просрочен
    }

    data class StatusInfo(
        val status: Status,
        val remainingDays: Int // отрицательное значение = просрочен на N дней
    )

    /**
     * Дни — календарные, через [DateUtils], как у штрафов. Раньше здесь вычитались
     * миллисекунды, то есть считались UTC-сутки: результат зависел от времени суток
     * в записи, и на границе дня светофор показывал лишний или недостающий день.
     */
    fun calculateStatus(expiryDate: Long, now: Long = System.currentTimeMillis()): StatusInfo {
        val remainingDays = ChronoUnit.DAYS.between(
            DateUtils.toLocalDate(now),
            DateUtils.toLocalDate(expiryDate)
        ).toInt()

        val status = when {
            remainingDays <= 7 -> Status.CRITICAL
            remainingDays <= 30 -> Status.WARNING
            else -> Status.NORMAL
        }

        return StatusInfo(status, remainingDays)
    }
}
