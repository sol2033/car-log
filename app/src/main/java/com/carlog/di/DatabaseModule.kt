package com.carlog.di

import android.content.Context
import androidx.room.Room
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.local.dao.CarDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideCarLogDatabase(
        @ApplicationContext context: Context
    ): CarLogDatabase {
        return Room.databaseBuilder(
            context,
            CarLogDatabase::class.java,
            CarLogDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideCarDao(database: CarLogDatabase): CarDao {
        return database.carDao()
    }
}
