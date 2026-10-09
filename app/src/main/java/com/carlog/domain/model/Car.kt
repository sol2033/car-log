package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val brand: String,
    val model: String,
    val year: Int?,
    val color: String?,
    val licensePlate: String?,
    val vin: String?,
    val engineModel: String?,
    val engineVolume: Double?,
    val transmissionType: String?,
    val driveType: String?,
    val bodyType: String?,
    val fuelType: String, // Petrol, Diesel, Electric
    val hasGasEquipment: Boolean = false,
    val gasType: String? = null, // Methane, LPG (only if hasGasEquipment = true)
    val currentMileage: Int,
    val purchaseMileage: Int?,
    val purchaseDate: Long?,
    val mainPhotoPath: String?,
    val photosPaths: List<String>?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // Пользователь запросил новый отсчёт расхода топлива: следующая добавленная
    // заправка станет точкой отсчёта (см. Refueling.isConsumptionResetPoint)
    val fuelResetPending: Boolean = false,
    // Объём бака, л; у электромобиля — ёмкость батареи, кВт·ч. Нужен для «пробега на баке».
    // null — машина заведена до 1.4.0: статистика просит указать объём, а не выдумывает его
    val tankVolume: Double? = null,
    // Объём газового баллона при ГБО: л для пропана (полезный, ≈80% номинала), м³ для метана
    val gasTankVolume: Double? = null,
    // Объём масла между метками MIN и MAX на щупе, л: по нему падение уровня «в долях щупа»
    // переводится в литры. Объём картера для этого не годится — он в разы больше
    val oilDipstickVolume: Double? = null,
    // Пользователь выбрал «не знаю»: подставлен 1 л, и расход по щупу — с погрешностью
    val oilDipstickVolumeIsGuess: Boolean = false
)
