package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refuelings",
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
data class Refueling(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val date: Long,
    val mileage: Int,
    val liters: Double,
    val fuelType: String, // 92, 95, 95+, 100, Diesel, Methane, LPG
    val pricePerLiter: Double? = null,
    val totalCost: Double? = null,
    // Устарело с 1.4.0: «полный бак» больше ни на что не влияет, колонка осталась в схеме
    val isFullTank: Boolean = true,
    val stationName: String? = null,
    // Устарело с 1.4.0: расход по отдельной заправке не считается (без «полного бака» его
    // не из чего получить честно) — средний считает FuelConsumptionCalculator
    val fuelConsumption: Double? = null,
    // Точка отсчёта расхода: перед этой заправкой была пропущенная — всё, что до неё,
    // в средний расход не идёт, и цепочка «от полного бака к полному» начинается заново
    val isConsumptionResetPoint: Boolean = false,
    // Только для машин с ГБО: сколько км проехано на этом топливе с прошлой заправки им же.
    // Общий пробег не делится между газом и бензином, без этого расход каждого приблизителен
    val fuelDistanceKm: Int? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
