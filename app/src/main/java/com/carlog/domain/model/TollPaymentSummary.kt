package com.carlog.domain.model

import com.carlog.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Итог платных дорог за один календарный месяц */
data class TollMonth(
    val month: YearMonth,
    val total: Double,
    val count: Int,
    /** Записи месяца, новые сверху — список экрана рисуется группами по месяцам */
    val payments: List<TollPayment>
)

/**
 * Сводка по платным дорогам для экрана списка.
 *
 * @property months Только месяцы, в которых были проезды, новые сверху
 * @property monthsSpan Сколько календарных месяцев прошло от первого проезда до текущего
 * месяца включительно — по нему считается среднее
 * @property averagePerMonth Средняя трата в месяц: месяцы без проездов тоже входят в делитель,
 * иначе редкие поездки выглядели бы как ежемесячная статья расходов
 */
data class TollSummary(
    val count: Int,
    val total: Double,
    val months: List<TollMonth>,
    val monthsSpan: Int,
    val averagePerMonth: Double
)

/** Помесячный расчёт платных дорог — чистая функция, тесты `TollPaymentSummaryTest` */
object TollPaymentSummary {

    fun summarize(payments: List<TollPayment>, today: LocalDate = LocalDate.now()): TollSummary {
        if (payments.isEmpty()) {
            return TollSummary(count = 0, total = 0.0, months = emptyList(), monthsSpan = 0, averagePerMonth = 0.0)
        }

        // Месяц — по локальной дате (правило 2 из CLAUDE.md): ночной проезд 1-го числа
        // не должен уехать в предыдущий месяц
        val byMonth = payments.groupBy { YearMonth.from(DateUtils.toLocalDate(it.date)) }
        val months = byMonth
            .map { (month, items) ->
                TollMonth(
                    month = month,
                    total = items.sumOf { it.amount },
                    count = items.size,
                    payments = items.sortedByDescending { it.date }
                )
            }
            .sortedByDescending { it.month }

        val total = payments.sumOf { it.amount }
        val first = months.last().month
        // Запись «из будущего» (дату выбрали вперёд) не должна сжимать делитель
        val last = maxOf(YearMonth.from(today), months.first().month)
        val span = ChronoUnit.MONTHS.between(first, last).toInt() + 1

        return TollSummary(
            count = payments.size,
            total = total,
            months = months,
            monthsSpan = span,
            averagePerMonth = total / span
        )
    }
}
