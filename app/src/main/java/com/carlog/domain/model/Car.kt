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
    val updatedAt: Long
)
