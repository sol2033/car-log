package com.carlog.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Единая конвертация хранимых меток времени (epoch millis) в календарные даты.
 *
 * Раньше по коду статистики был раскидан приём `LocalDate.ofEpochDay(millis / 86_400_000)`:
 * он делит время по **UTC**-суткам, тогда как границы периодов считаются от локальной полуночи.
 * Из-за расхождения запись, созданная ночью (в UTC+3 — с 00:00 до 03:00), попадала в предыдущий
 * день, а значит и в предыдущий месяц/неделю при группировке; в UTC− наоборот — запись,
 * датированная первым числом, выпадала из фильтра месяца.
 */
object DateUtils {

    /** Календарная дата события в часовом поясе устройства */
    fun toLocalDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Начало суток (локальная полночь) в epoch millis */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Конец суток (23:59:59 по локальному времени) в epoch millis */
    fun endOfDayMillis(date: LocalDate): Long =
        date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
