package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Оплата платной дороги.
 *
 * Живёт в одном разделе со штрафами только ради компактности меню — по смыслу и в базе это
 * отдельная сущность: ни статуса, ни льготного срока, ни отметки об оплате у неё нет,
 * запись появляется уже оплаченной.
 *
 * Третий модуль **без пробега** после документов и штрафов: проезд привязан к дате, а не
 * к показаниям одометра. В синхронизации пробега машины не участвует, при активном фильтре
 * статистики по пробегу исключается целиком, в стоимость за км не входит.
 *
 * @property date Дата оплаты в миллисекундах
 * @property amount Сумма оплаты
 * @property roadName Какая дорога (опционально) — из введённых ранее названий форма
 * предлагает подсказки, см. [NameSuggestions]
 */
@Entity(
    tableName = "toll_payments",
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
data class TollPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val date: Long,
    val amount: Double,
    val roadName: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
