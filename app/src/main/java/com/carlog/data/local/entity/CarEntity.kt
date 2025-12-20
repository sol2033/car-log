package com.carlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.carlog.data.local.Converters

@Entity(tableName = "cars")
@TypeConverters(Converters::class)
data class CarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Основная информация
    val brand: String,                // Марка (обязательно) - "Toyota"
    val model: String,                // Модель (обязательно) - "Camry"
    val year: Int?,                   // Год выпуска - 2020
    val color: String?,               // Цвет - "Черный"
    val licensePlate: String?,        // Госномер - "А123БВ777"
    val vin: String?,                 // VIN код
    
    // Характеристики двигателя
    val engineModel: String?,         // Модель двигателя - "2GR-FE"
    val engineVolume: Double?,        // Объем двигателя (литры) - 3.5
    val transmissionType: String?,    // Тип КПП - "Автомат", "Механика", "Робот", "Вариатор"
    val driveType: String?,           // Привод - "Полный", "Передний", "Задний"
    
    val fuelType: String,             // Тип топлива - "Бензин АИ-95", "Дизель", "Электро", "Газ", "Гибрид"
    
    // Пробег
    val currentMileage: Int,          // Текущий пробег (км) - обновляется автоматически
    val purchaseMileage: Int?,        // Пробег при покупке (км)
    val purchaseDate: Long?,          // Дата покупки (timestamp)
    
    // Фото
    val mainPhotoPath: String?,       // Путь к главному фото автомобиля
    val photosPaths: List<String>?,   // Список путей дополнительных фото
    
    // Дополнительно
    val notes: String?,               // Заметки от пользователя
    val createdAt: Long,              // Дата создания записи
    val updatedAt: Long               // Дата обновления
)
