package com.carlog.data.repository

import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.domain.model.CarDocument
import com.carlog.util.FileHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository для работы с документами автомобиля
 */
@Singleton
class CarDocumentRepository @Inject constructor(
    private val carDocumentDao: CarDocumentDao
) {

    // === CRUD операции ===

    suspend fun insertDocument(document: CarDocument): Long {
        return carDocumentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: CarDocument) {
        carDocumentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: CarDocument) {
        document.photoPath?.let { FileHelper.deleteFile(it) }
        carDocumentDao.deleteDocument(document)
    }

    fun getDocumentById(documentId: Long): Flow<CarDocument?> {
        return carDocumentDao.getDocumentById(documentId)
    }

    fun getDocumentsByCarId(carId: Long): Flow<List<CarDocument>> {
        return carDocumentDao.getDocumentsByCarId(carId)
    }

    // === Активные / история ===

    fun getActiveDocuments(carId: Long): Flow<List<CarDocument>> {
        return carDocumentDao.getActiveDocuments(carId)
    }

    fun getArchivedDocuments(carId: Long): Flow<List<CarDocument>> {
        return carDocumentDao.getArchivedDocuments(carId)
    }

    suspend fun getActiveDocumentByType(carId: Long, type: String): CarDocument? {
        return carDocumentDao.getActiveDocumentByType(carId, type)
    }

    /** Отправляет документ в историю (isActive = false) */
    suspend fun deactivateDocument(documentId: Long) {
        carDocumentDao.deactivateDocument(documentId)
    }

    /**
     * Продление документа: старый уходит в историю, новый становится активным.
     */
    suspend fun renewDocument(oldDocument: CarDocument, newDocument: CarDocument): Long {
        carDocumentDao.deactivateDocument(oldDocument.id)
        return carDocumentDao.insertDocument(newDocument.copy(id = 0, isActive = true))
    }
}
