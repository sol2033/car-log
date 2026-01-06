package com.carlog.data.repository

import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Part
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartRepository @Inject constructor(
    private val partDao: PartDao
) {
    fun getPartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getPartsByCarId(carId)
    
    fun getActivePartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getActivePartsByCarId(carId)
    
    fun getBrokenPartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getBrokenPartsByCarId(carId)
    
    fun getPartById(partId: Long): Flow<Part?> =
        partDao.getPartById(partId)
    
    suspend fun insertPart(part: Part): Long =
        partDao.insertPart(part)
    
    suspend fun updatePart(part: Part) =
        partDao.updatePart(part)
    
    suspend fun deletePart(part: Part) =
        partDao.deletePart(part)
    
    suspend fun deletePartsByCarId(carId: Long) =
        partDao.deletePartsByCarId(carId)
    
    suspend fun getPartsCountByCarId(carId: Long): Int =
        partDao.getPartsCountByCarId(carId)
    
    suspend fun getTotalPartsCostByCarId(carId: Long): Double =
        partDao.getTotalPartsCostByCarId(carId) ?: 0.0
}
