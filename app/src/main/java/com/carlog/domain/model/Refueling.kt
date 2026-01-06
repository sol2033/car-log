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
    val isFullTank: Boolean = true,
    val stationName: String? = null,
    val fuelConsumption: Double? = null, // л/100км (auto-calculated)
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
