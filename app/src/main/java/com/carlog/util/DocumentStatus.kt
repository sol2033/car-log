package com.carlog.util

import java.util.concurrent.TimeUnit

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

    fun calculateStatus(expiryDate: Long): StatusInfo {
        val remainingMillis = expiryDate - System.currentTimeMillis()
        val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMillis).toInt()

        val status = when {
            remainingDays <= 7 -> Status.CRITICAL
            remainingDays <= 30 -> Status.WARNING
            else -> Status.NORMAL
        }

        return StatusInfo(status, remainingDays)
    }
}
