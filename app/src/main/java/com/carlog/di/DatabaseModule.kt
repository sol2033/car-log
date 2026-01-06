package com.carlog.di

import android.content.Context
import androidx.room.Room
import com.carlog.data.local.CarLogDatabase
import com.carlog.data.local.MIGRATION_7_8
import com.carlog.data.local.MIGRATION_8_9
import com.carlog.data.local.MIGRATION_9_10
import com.carlog.data.local.MIGRATION_10_11
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
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
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideCarDao(database: CarLogDatabase): CarDao {
        return database.carDao()
    }
    
    @Provides
    @Singleton
    fun providePartDao(database: CarLogDatabase): PartDao {
        return database.partDao()
    }
    
    @Provides
    @Singleton
    fun provideBreakdownDao(database: CarLogDatabase): BreakdownDao {
        return database.breakdownDao()
    }
    
    @Provides
    @Singleton
    fun provideAccidentDao(database: CarLogDatabase): AccidentDao {
        return database.accidentDao()
    }
    
    @Provides
    @Singleton
    fun provideConsumableDao(database: CarLogDatabase): ConsumableDao {
        return database.consumableDao()
    }
    
    @Provides
    @Singleton
    fun provideRefuelingDao(database: CarLogDatabase): RefuelingDao {
        return database.refuelingDao()
    }
    
    @Provides
    @Singleton
    fun provideExpenseDao(database: CarLogDatabase): ExpenseDao {
        return database.expenseDao()
    }
}
