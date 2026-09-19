package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accidents",
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
data class Accident(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val carId: Long,
    
    val date: Long,                         // Дата ДТП
    val mileage: Int,                       // Пробег на момент ДТП
    
    val location: String?,                  // Место ДТП
    val damageDescription: String,          // Описание повреждений
    val severity: String,                   // Серьезность: "Незначительная", "Средняя", "Серьезная", "Тотальная"
    
    val isUserAtFault: Boolean,             // Виновник ли пользователь
    
    // Выплаты (все nullable)
    val osagoPayout: Double?,               // Выплата по ОСАГО
    val kaskoPayout: Double?,               // Выплата по КАСКО
    val culpritPayout: Double?,             // Выплата от виновника

    // Компенсация, выплаченная пострадавшему, когда виновник — сам пользователь
    // (нет страховки, урегулировали на месте, доплата сверх ОСАГО).
    // null — не указана: у записей, заведённых до появления поля, здесь именно null,
    // и ноль вместо него соврал бы, что компенсации не было
    val compensationPaid: Double? = null,
    
    // Ремонт
    val installedPartIds: List<Long>?,      // ID установленных запчастей
    val repairCost: Double?,                // Стоимость ремонта
    
    // Медиа файлы
    val photosPaths: List<String>?,         // Фотографии (без ограничения количества)
    val documentPath: String?,              // Путь к PDF документу (1 файл)
    
    val notes: String?,                     // Заметки
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Полученные по ДТП выплаты: ОСАГО, КАСКО и от виновника */
val Accident.receivedPayouts: Double
    get() = (osagoPayout ?: 0.0) + (kaskoPayout ?: 0.0) + (culpritPayout ?: 0.0)

/**
 * Во сколько ДТП обошлось владельцу — эта сумма и идёт в расходы.
 *
 * Ремонт плюс выплаченная пострадавшему компенсация, минус полученные выплаты: страховая
 * закрыла ремонт — из кармана не ушло ничего. **Ниже нуля не опускается**: выплата больше
 * ремонта не делает аварию источником дохода и не должна съедать стоимость других записей.
 * Ограничение действует на каждое ДТП отдельно, поэтому одна щедрая выплата не обнуляет
 * соседнюю аварию.
 */
val Accident.netCost: Double
    get() = ((repairCost ?: 0.0) + (compensationPaid ?: 0.0) - receivedPayouts).coerceAtLeast(0.0)

