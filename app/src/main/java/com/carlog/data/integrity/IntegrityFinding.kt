package com.carlog.data.integrity

import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.Part

/** Тип события, к которому может относиться запчасть */
enum class EventType { BREAKDOWN, ACCIDENT }

/** Событие-кандидат для привязки запчасти */
data class EventCandidate(
    val type: EventType,
    val id: Long,
    val title: String,
    val date: Long,
    val mileage: Int,
    /** Сумма, которая уже указана в событии (для подсказки пользователю) */
    val declaredPartsCost: Double?
)

/**
 * Находка проверки данных: что не так и к какой записи относится.
 *
 * [id] — стабильный ключ: по нему запоминается «пропустить», чтобы законно устроенная
 * запись не всплывала находкой при каждом входе.
 */
sealed interface IntegrityFinding {

    val id: String

    /** Машина, к которой относится находка (для подписи в списке) */
    val carLabel: String

    /**
     * Запчасть не привязана ни к одному событию, но по дате, пробегу и способу установки
     * похожа на созданную событием. Её стоимость сейчас учитывается и отдельно,
     * и внутри стоимости события.
     */
    data class UnlinkedPart(
        override val id: String,
        override val carLabel: String,
        val part: Part,
        val candidates: List<EventCandidate>
    ) : IntegrityFinding

    /** У ДТП есть привязанные запчасти, но стоимость ремонта не заполнена */
    data class AccidentWithoutRepairCost(
        override val id: String,
        override val carLabel: String,
        val accident: Accident,
        val linkedPartsSum: Double
    ) : IntegrityFinding

    /** Стоимость запчастей обслуживания разошлась с суммой привязанных к нему запчастей */
    data class BreakdownCostMismatch(
        override val id: String,
        override val carLabel: String,
        val breakdown: Breakdown,
        val linkedPartsSum: Double
    ) : IntegrityFinding

    /** Без даты и пробега покупки средний пробег в день считается от даты создания записи */
    data class CarWithoutPurchaseInfo(
        override val id: String,
        override val carLabel: String,
        val car: Car
    ) : IntegrityFinding

    /** В категории больше одного активного расходника — светофор считается по неверному */
    data class DuplicateActiveConsumables(
        override val id: String,
        override val carLabel: String,
        val category: String,
        val consumables: List<Consumable>
    ) : IntegrityFinding
}
