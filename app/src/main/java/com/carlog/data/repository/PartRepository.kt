package com.carlog.data.repository

import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Part
import com.carlog.util.FileHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    
    suspend fun deletePart(part: Part) {
        // Фото принадлежат только этой записи — удаляем вместе с ней,
        // иначе файлы остаются в хранилище навсегда и раздувают бэкапы
        FileHelper.deleteFiles(part.photosPaths)
        partDao.deletePart(part)
    }

    suspend fun deletePartsByCarId(carId: Long) {
        partDao.getPartsByCarId(carId).firstOrNull()?.forEach {
            FileHelper.deleteFiles(it.photosPaths)
        }
        partDao.deletePartsByCarId(carId)
    }
    
    suspend fun getPartsCountByCarId(carId: Long): Int =
        partDao.getPartsCountByCarId(carId)
    
    suspend fun getTotalPartsCostByCarId(carId: Long): Double =
        partDao.getTotalPartsCostByCarId(carId) ?: 0.0
}
