package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consumables",
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
data class Consumable(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val category: String, // "Масло в двигателе", "Фильтр масляный", etc.
    // Название позиции категории «Другое» (герметик, хомуты): у неё нет ни интервалов,
    // ни напоминаний, и в модуле «Расходники» она не показывается
    val customName: String? = null,
    val manufacturer: String?, // Производитель
    val articleNumber: String?, // Артикул
    val installationMileage: Int, // Пробег установки
    val installationDate: Long, // Дата установки (timestamp)
    val linkedMaintenanceId: Long? = null, // ID связанного ТО (если создан в рамках ТО)
    val replacementMileage: Int?, // Пробег замены (null если активен)
    val replacementDate: Long?, // Дата замены (null если активен)
    val cost: Double?, // Стоимость
    val isInstalledAtService: Boolean, // Установлен в сервисе
    val serviceCost: Double?, // Стоимость работы сервиса
    val volume: Double?, // Объем для жидкостей (литры)
    val replacementIntervalMileage: Int?, // Интервал замены по пробегу (км)
    val replacementIntervalDays: Int?, // Интервал замены по дням
    val isActive: Boolean, // Активен ли расходник
    val notes: String?, // Заметки
    val createdAt: Long,
    val updatedAt: Long
)
