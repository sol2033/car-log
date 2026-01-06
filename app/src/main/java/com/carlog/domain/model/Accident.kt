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
