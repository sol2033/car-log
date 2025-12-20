package com.carlog.di

import com.carlog.data.local.dao.CarDao
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
        carDao: CarDao
    ): CarRepository {
        return CarRepository(carDao)
    }
}
