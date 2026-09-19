package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Модель штрафа (постановление ГИБДД и прочих органов).
 *
 * Единственный модуль наравне с документами **без пробега**: место и дата нарушения известны,
 * а показания одометра — нет, и требовать их значило бы заставлять выдумывать цифру.
 * Следствие: при активном фильтре статистики по пробегу штрафы из неё исключаются целиком
 * (та же логика И, что и у документов, см. §4.7 бизнес-логики).
 *
 * @property date Дата постановления в миллисекундах
 * @property amount Сумма по постановлению
 * @property violationType Тип нарушения (см. [FineViolationTypes])
 * @property customViolation Своя формулировка для типа «Другое» (для остальных null)
 * @property location Место нарушения (опционально)
 * @property decisionNumber Номер постановления / УИН (опционально)
 * @property issuer Орган, вынесший постановление (опционально)
 * @property discountUntil Последний день льготной оплаты (опционально, см. [com.carlog.util.FineStatus])
 * @property isPaid Оплачен ли штраф
 * @property paidDate Дата оплаты (опционально)
 * @property paidAmount Фактически уплаченная сумма — со скидкой она меньше [amount]
 * @property photosPaths Вложения: фото постановления и/или PDF (см. §12.1 техдока)
 */
@Entity(
    tableName = "fines",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId")]
)
data class Fine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val date: Long,
    val amount: Double,
    val violationType: String,
    val customViolation: String? = null,
    val location: String? = null,
    val decisionNumber: String? = null,
    val issuer: String? = null,
    val discountUntil: Long? = null,
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val paidAmount: Double? = null,
    val photosPaths: List<String>? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Что показывать в списке: своя формулировка у «Другого», иначе сам тип */
    val displayViolation: String
        get() = if (violationType == FineViolationTypes.OTHER && !customViolation.isNullOrBlank()) {
            customViolation
        } else {
            violationType
        }

    /**
     * Деньги, реально ушедшие на штраф.
     *
     * Неоплаченное постановление — ещё не трата: в статистику идёт только оплаченное, и именно
     * фактическая сумма (со скидкой она меньше выписанной). Если сумму оплаты не указали,
     * считаем, что заплатили полностью.
     */
    val paidCost: Double
        get() = if (isPaid) (paidAmount ?: amount) else 0.0
}

/**
 * Типы нарушений. Как категории расходников и типы документов — русские строки прямо в БД.
 * Список подсказывает частые формулировки, «Другое» оставляет место любой своей.
 */
object FineViolationTypes {
    const val SPEEDING = "Превышение скорости"
    const val PARKING = "Парковка и остановка"
    const val RED_LIGHT = "Проезд на запрещающий сигнал"
    const val STOP_LINE = "Стоп-линия"
    const val ONCOMING_LANE = "Выезд на встречную полосу"
    const val ROAD_SIGN = "Знак или разметка"
    const val SEAT_BELT = "Ремень безопасности"
    const val PHONE = "Телефон за рулём"
    const val TINTING = "Тонировка"
    const val NO_INSURANCE = "Отсутствие ОСАГО"
    const val MALFUNCTION = "Техническая неисправность"
    const val OTHER = "Другое"

    val ALL = listOf(
        SPEEDING,
        PARKING,
        RED_LIGHT,
        STOP_LINE,
        ONCOMING_LANE,
        ROAD_SIGN,
        SEAT_BELT,
        PHONE,
        TINTING,
        NO_INSURANCE,
        MALFUNCTION,
        OTHER
    )
}
