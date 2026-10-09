package com.carlog.di

import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.*
import com.carlog.data.repository.CarRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideCarRepository(
        carDao: CarDao,
        breakdownDao: BreakdownDao,
        refuelingDao: RefuelingDao,
        consumableDao: ConsumableDao,
        partDao: PartDao,
        accidentDao: AccidentDao,
        expenseDao: ExpenseDao,
        carDocumentDao: CarDocumentDao,
        oilTopUpDao: OilTopUpDao,
        fineDao: FineDao
    ): CarRepository {
        return CarRepository(
            carDao, breakdownDao, refuelingDao, consumableDao,
            partDao, accidentDao, expenseDao, carDocumentDao, oilTopUpDao, fineDao
        )
    }
}
