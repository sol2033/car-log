package com.carlog.data.repository

import com.carlog.data.local.dao.FineDao
import com.carlog.domain.model.Fine
import com.carlog.util.FileHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository для работы со штрафами
 */
@Singleton
class FineRepository @Inject constructor(
    private val fineDao: FineDao
) {

    // === CRUD операции ===

    suspend fun insertFine(fine: Fine): Long {
        return fineDao.insertFine(fine)
    }

    suspend fun updateFine(fine: Fine) {
        fineDao.updateFine(fine)
    }

    /** Удаляет запись вместе с её вложениями: каскад БД до файлов в хранилище не достаёт */
    suspend fun deleteFine(fine: Fine) {
        FileHelper.deleteFiles(fine.photosPaths)
        fineDao.deleteFine(fine)
    }

    fun getFineById(fineId: Long): Flow<Fine?> {
        return fineDao.getFineById(fineId)
    }

    fun getFinesByCarId(carId: Long): Flow<List<Fine>> {
        return fineDao.getFinesByCarId(carId)
    }

    // === Сводка ===

    fun getFinesCount(carId: Long): Flow<Int> = fineDao.getFinesCount(carId)

    fun getTotalPaid(carId: Long): Flow<Double?> = fineDao.getTotalPaid(carId)

    fun getTotalUnpaid(carId: Long): Flow<Double?> = fineDao.getTotalUnpaid(carId)

    fun getUnpaidCount(carId: Long): Flow<Int> = fineDao.getUnpaidCount(carId)

    /**
     * Отметка об оплате прямо из списка — самое частое действие после добавления штрафа,
     * ради него не стоит открывать форму. Идём через `copy`, чтобы не потерять поля,
     * которых нет в этом сценарии (правило 1 из CLAUDE.md).
     */
    suspend fun markAsPaid(fine: Fine, paidDate: Long, paidAmount: Double?) {
        fineDao.updateFine(
            fine.copy(
                isPaid = true,
                paidDate = paidDate,
                paidAmount = paidAmount,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Снятие отметки об оплате — если её поставили по ошибке */
    suspend fun markAsUnpaid(fine: Fine) {
        fineDao.updateFine(
            fine.copy(
                isPaid = false,
                paidDate = null,
                paidAmount = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
