package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Долив моторного масла между заменами — из них складывается расход масла (угар).
 *
 * К конкретной карточке масла запись не привязана: интервал определяется по пробегу
 * (между установкой масла и его заменой, см. [OilConsumptionCalculator]). Привязку по id
 * пришлось бы чинить при каждой правке цепочки замен, а пробег у долива есть всегда.
 *
 * Отдельная сущность, а не [Consumable]: долив не заменяет масло, и запись-расходник
 * сломала бы правило «в категории один активный» (§4.4 бизнес-логики).
 *
 * @property volume Долито, л
 * @property cost Стоимость (необязательно) — идёт в «ТО» и вкладку «Расходники» статистики
 */
@Entity(
    tableName = "oil_top_ups",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["carId"])]
)
data class OilTopUp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val date: Long,
    val mileage: Int,
    val volume: Double,
    val cost: Double? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
