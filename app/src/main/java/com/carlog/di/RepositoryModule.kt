package com.carlog.di

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
        expenseDao: ExpenseDao
    ): CarRepository {
        return CarRepository(carDao, breakdownDao, refuelingDao, consumableDao, partDao, accidentDao, expenseDao)
    }
}
