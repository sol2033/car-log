package com.carlog.data.repository

import com.carlog.data.local.dao.TollPaymentDao
import com.carlog.domain.model.TollPayment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository для оплат платных дорог. Вложений у записи нет — удалять файлы не нужно.
 */
@Singleton
class TollPaymentRepository @Inject constructor(
    private val tollPaymentDao: TollPaymentDao
) {

    suspend fun insertTollPayment(payment: TollPayment): Long =
        tollPaymentDao.insertTollPayment(payment)

    suspend fun updateTollPayment(payment: TollPayment) =
        tollPaymentDao.updateTollPayment(payment)

    suspend fun deleteTollPayment(payment: TollPayment) =
        tollPaymentDao.deleteTollPayment(payment)

    fun getTollPaymentById(id: Long): Flow<TollPayment?> =
        tollPaymentDao.getTollPaymentById(id)

    fun getTollPaymentsByCarId(carId: Long): Flow<List<TollPayment>> =
        tollPaymentDao.getTollPaymentsByCarId(carId)

    fun getRoadNames(): Flow<List<String>> = tollPaymentDao.getRoadNames()
}
