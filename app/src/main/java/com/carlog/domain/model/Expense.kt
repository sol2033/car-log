package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Модель прочих расходов (мойка, аксессуары, детейлинг и прочее)
 *
 * @property id Уникальный идентификатор расхода
 * @property carId ID автомобиля (Foreign Key)
 * @property date Дата расхода в миллисекундах
 * @property mileage Пробег автомобиля на момент расхода
 * @property category Категория расхода
 * @property cost Общая стоимость (включая работы и материалы)
 * @property serviceName Название сервиса (опционально)
 * @property serviceAddress Адрес сервиса (опционально)
 * @property notes Заметки (опционально)
 * @property createdAt Дата создания записи
 * @property updatedAt Дата последнего обновления
 */
@Entity(
    tableName = "expenses",
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
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val date: Long,
    val mileage: Int,
    val category: String, // Самомойка, Аксессуары, Автозвук, Детейлинг салона, Диски, Шины, Другие
    val cost: Double,
    val serviceName: String? = null,
    val serviceAddress: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Категории прочих расходов
 */
object ExpenseCategories {
    const val SELF_WASH = "Самомойка"
    const val ACCESSORIES = "Аксессуары"
    const val CAR_AUDIO = "Автозвук"
    const val INTERIOR_DETAILING = "Детейлинг салона"
    const val WHEELS = "Диски"
    const val TIRES = "Шины"
    const val OTHER = "Другие"

    val ALL = listOf(
        SELF_WASH,
        ACCESSORIES,
        CAR_AUDIO,
        INTERIOR_DETAILING,
        WHEELS,
        TIRES,
        OTHER
    )
}
