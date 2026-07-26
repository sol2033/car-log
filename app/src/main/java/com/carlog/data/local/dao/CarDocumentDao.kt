package com.carlog.data.local.dao

import androidx.room.*
import com.carlog.domain.model.CarDocument
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с документами автомобиля
 */
@Dao
interface CarDocumentDao {

    // === CRUD операции ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: CarDocument): Long

    @Update
    suspend fun updateDocument(document: CarDocument)

    @Delete
    suspend fun deleteDocument(document: CarDocument)

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getDocumentById(documentId: Long): Flow<CarDocument?>

    @Query("SELECT * FROM documents WHERE carId = :carId ORDER BY expiryDate ASC")
    fun getDocumentsByCarId(carId: Long): Flow<List<CarDocument>>

    // === Активные / история ===

    @Query("SELECT * FROM documents WHERE carId = :carId AND isActive = 1 ORDER BY expiryDate ASC")
    fun getActiveDocuments(carId: Long): Flow<List<CarDocument>>

    @Query("SELECT * FROM documents WHERE carId = :carId AND isActive = 0 ORDER BY expiryDate DESC")
    fun getArchivedDocuments(carId: Long): Flow<List<CarDocument>>

    @Query("""
        SELECT * FROM documents
        WHERE carId = :carId AND type = :type AND isActive = 1
        ORDER BY expiryDate DESC
        LIMIT 1
    """)
    suspend fun getActiveDocumentByType(carId: Long, type: String): CarDocument?

    @Query("UPDATE documents SET isActive = 0, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun deactivateDocument(documentId: Long, updatedAt: Long = System.currentTimeMillis())
}
