package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parts",
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
data class Part(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val name: String,
    val manufacturer: String? = null,
    val partNumber: String? = null,
    val installDate: Long,
    val installMileage: Int,
    val installationType: String, // "Самостоятельно", "Сервис"
    val price: Double,
    val servicePrice: Double? = null,
    val serviceName: String? = null,
    val serviceAddress: String? = null,
    val photosPaths: List<String>? = null,
    val isBroken: Boolean = false,
    val breakdownDate: Long? = null,
    val breakdownMileage: Int? = null,
    val mileageDriven: Int? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
