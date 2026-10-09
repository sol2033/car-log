package com.carlog.data.repository

import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.domain.model.OilTopUp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OilTopUpRepository @Inject constructor(
    private val oilTopUpDao: OilTopUpDao
) {

    fun getTopUpsByCarId(carId: Long): Flow<List<OilTopUp>> =
        oilTopUpDao.getTopUpsByCarId(carId)

    suspend fun insertTopUp(topUp: OilTopUp): Long = oilTopUpDao.insertTopUp(topUp)

    suspend fun updateTopUp(topUp: OilTopUp) = oilTopUpDao.updateTopUp(topUp)

    suspend fun deleteTopUp(topUp: OilTopUp) = oilTopUpDao.deleteTopUp(topUp)
}
