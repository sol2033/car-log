package com.carlog.domain.model

data class Car(
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
    val fuelType: String,
    val currentMileage: Int,
    val purchaseMileage: Int?,
    val purchaseDate: Long?,
    val mainPhotoPath: String?,
    val photosPaths: List<String>?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
