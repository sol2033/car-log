package com.carlog.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "breakdowns",
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
data class Breakdown(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val title: String,
    val description: String,
    val breakdownDate: Long,
    val breakdownMileage: Int,
    val brokenPartId: Long? = null,
    val brokenPartName: String? = null,
    val installedPartIds: List<Long>? = null,
    val partsCost: Double,
    val serviceCost: Double? = null,
    val totalCost: Double,
    val isWarrantyRepair: Boolean = false,
    val serviceName: String? = null,
    val serviceAddress: String? = null,
    val photosPaths: List<String>? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
