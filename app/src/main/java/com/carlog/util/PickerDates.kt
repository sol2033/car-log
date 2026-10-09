package com.carlog.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Перевод дат между записями приложения и Material3 `DatePicker`.
 *
 * Календарь Material3 работает в **UTC**: открывается на UTC-дате переданного значения и
 * возвращает выбранный день как полночь по UTC. Записи же хранят момент времени, а день
 * показывают по часовому поясу устройства (см. [DateUtils]). Раньше значения передавались
 * как есть, и:
 * - запись, сделанная с 00:00 до 03:00 по Москве, открывалась в календаре на **предыдущем** дне;
 * - выбранный день сохранялся как 03:00 по Москве, а в западных поясах — как вечер
 *   **предыдущего** дня.
 *
 * Переводится только граница «форма ↔ календарь». Хранимые записи не меняются, пока
 * пользователь сам не выберет дату; при выборе время суток прежнего значения сохраняется,
 * чтобы порядок записей внутри дня не сбивался. Тесты — `PickerDatesTest`.
 */
object PickerDates {

    /** Значение для `initialSelectedDateMillis`: локальный день записи как полночь UTC */
    fun toPicker(stored: Long): Long =
        DateUtils.toLocalDate(stored).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Выбранный в календаре день — в хранимое значение: этот день по часовому поясу
     * устройства, время суток — от прежнего значения поля.
     *
     * @param picked `selectedDateMillis` календаря (полночь UTC выбранного дня)
     * @param previous прежнее значение поля; его время суток переносится на новый день
     */
    fun fromPicker(picked: Long, previous: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val day: LocalDate = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(previous).atZone(zone).toLocalTime()
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
