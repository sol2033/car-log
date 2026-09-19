package com.carlog.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Статус-«светофор» штрафа по льготному сроку оплаты
 * (аналог [DocumentStatus] для документов и [ConsumableStatus] для расходников).
 *
 * Смысл светофора — не «просрочен платёж», а «скидка ещё жива»: именно её пропуск стоит денег,
 * а сам долг никуда не девается. Поэтому оплаченный штраф выходит из светофора совсем.
 *
 * Дни считаются календарные, через [DateUtils]: вычитание миллисекунд дало бы UTC-сутки и
 * на границе дня показывало бы лишний или недостающий день.
 */
object FineStatus {

    /** Льготный срок оплаты по умолчанию — 30 дней с даты постановления */
    const val DEFAULT_DISCOUNT_DAYS = 30L

    /** Порог жёлтого: столько дней до конца скидки считаем «пора платить» */
    const val WARNING_DAYS = 7

    enum class Status {
        PAID,     // Оплачен — светофор не нужен
        NORMAL,   // Зелёный — скидка действует, времени достаточно
        WARNING,  // Жёлтый — до конца скидки не больше WARNING_DAYS дней
        EXPIRED   // Красный — скидка сгорела, а штраф всё ещё не оплачен
    }

    /**
     * @property remainingDays дней до конца льготного срока (0 — последний день,
     * отрицательное — скидка сгорела N дней назад). `null`, когда отсчитывать нечего:
     * штраф оплачен или льготный срок не указан.
     */
    data class StatusInfo(
        val status: Status,
        val remainingDays: Int?
    )

    fun calculateStatus(
        isPaid: Boolean,
        discountUntil: Long?,
        now: Long = System.currentTimeMillis()
    ): StatusInfo {
        if (isPaid) return StatusInfo(Status.PAID, null)
        // Срока не задали — напоминать не о чем, но штраф висит неоплаченным
        if (discountUntil == null) return StatusInfo(Status.NORMAL, null)

        val remainingDays = ChronoUnit.DAYS.between(
            DateUtils.toLocalDate(now),
            DateUtils.toLocalDate(discountUntil)
        ).toInt()

        val status = when {
            remainingDays < 0 -> Status.EXPIRED
            remainingDays <= WARNING_DAYS -> Status.WARNING
            else -> Status.NORMAL
        }

        return StatusInfo(status, remainingDays)
    }

    /** Льготный срок по умолчанию для даты постановления — им предзаполняется форма */
    fun defaultDiscountUntil(decisionDate: Long): Long {
        val day = DateUtils.toLocalDate(decisionDate).plusDays(DEFAULT_DISCOUNT_DAYS)
        return DateUtils.endOfDayMillis(day)
    }

    /** Последний день льготного срока как календарная дата — для подписей в UI */
    fun discountDeadline(decisionDate: Long): LocalDate =
        DateUtils.toLocalDate(decisionDate).plusDays(DEFAULT_DISCOUNT_DAYS)
}
