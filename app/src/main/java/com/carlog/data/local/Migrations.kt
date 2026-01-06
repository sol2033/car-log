package com.carlog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Создаем таблицу refuelings
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS refuelings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                carId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                mileage INTEGER NOT NULL,
                liters REAL NOT NULL,
                fuelType TEXT NOT NULL,
                pricePerLiter REAL,
                totalCost REAL,
                isFullTank INTEGER NOT NULL,
                stationName TEXT,
                fuelConsumption REAL,
                notes TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Создаем индекс для carId
        db.execSQL("CREATE INDEX IF NOT EXISTS index_refuelings_carId ON refuelings(carId)")
        
        // Добавляем новые поля в таблицу cars
        db.execSQL("ALTER TABLE cars ADD COLUMN hasGasEquipment INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cars ADD COLUMN gasType TEXT DEFAULT NULL")
    }
}
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем поле serviceCost в таблицу consumables
        db.execSQL("ALTER TABLE consumables ADD COLUMN serviceCost REAL DEFAULT NULL")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Создаем таблицу expenses (прочие расходы)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                carId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                mileage INTEGER NOT NULL,
                category TEXT NOT NULL,
                cost REAL NOT NULL,
                serviceName TEXT,
                serviceAddress TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Создаем индекс для carId
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_carId ON expenses(carId)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Создаем индекс для carId в таблице accidents (если еще не существует)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accidents_carId ON accidents(carId)")
    }
}