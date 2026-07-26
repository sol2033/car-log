package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Модель документа автомобиля (страховка, налог и т.п.)
 *
 * @property id Уникальный идентификатор документа
 * @property carId ID автомобиля (Foreign Key)
 * @property type Тип документа (см. [DocumentTypes])
 * @property customName Название для типа «Другое» (для остальных типов null)
 * @property number Номер документа/полиса (опционально)
 * @property organization Страховая компания или организация (опционально)
 * @property startDate Дата начала действия в миллисекундах (опционально)
 * @property expiryDate Дата окончания действия / дата платежа в миллисекундах
 * @property cost Стоимость (опционально)
 * @property photoPath Путь к фото документа во внутреннем хранилище (опционально)
 * @property notes Заметки (опционально)
 * @property isActive Активен ли документ (false — ушёл в историю после продления)
 * @property createdAt Дата создания записи
 * @property updatedAt Дата последнего обновления
 */
@Entity(
    tableName = "documents",
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
data class CarDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val type: String,
    val customName: String? = null,
    val number: String? = null,
    val organization: String? = null,
    val startDate: Long? = null,
    val expiryDate: Long,
    val cost: Double? = null,
    val photoPath: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Отображаемое название документа */
    val displayName: String
        get() = if (type == DocumentTypes.OTHER && !customName.isNullOrBlank()) customName else type
}

/**
 * Типы документов. Как и категории расходников, хранятся русскими строками.
 */
object DocumentTypes {
    const val OSAGO = "ОСАГО"
    const val KASKO = "КАСКО"
    const val VEHICLE_TAX = "Транспортный налог"
    const val OTHER = "Другое"

    /** Стандартные типы — показываются плитками на экране документов всегда */
    val STANDARD = listOf(OSAGO, KASKO, VEHICLE_TAX)

    val ALL = STANDARD + OTHER
}
