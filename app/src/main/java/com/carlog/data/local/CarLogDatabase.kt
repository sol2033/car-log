package com.carlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.entity.CarEntity

@Database(
    entities = [
        CarEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CarLogDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    
    companion object {
        const val DATABASE_NAME = "car_log_database"
    }
}
