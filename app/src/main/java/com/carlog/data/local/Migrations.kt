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

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем поле title в таблицу expenses
        db.execSQL("ALTER TABLE expenses ADD COLUMN title TEXT DEFAULT NULL")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Заполняем installedPartIds для старых поломок на основе совпадения даты и пробега
        // Это безопасная операция: только SELECT и UPDATE, никаких изменений структуры
        
        // Получаем все поломки без installedPartIds
        val cursor = db.query("""
            SELECT id, breakdownDate, breakdownMileage 
            FROM breakdowns 
            WHERE installedPartIds IS NULL OR installedPartIds = '[]' OR installedPartIds = ''
        """)
        
        val updates = mutableListOf<Pair<Long, String>>()
        
        while (cursor.moveToNext()) {
            val breakdownId = cursor.getLong(0)
            val breakdownDate = cursor.getLong(1)
            val breakdownMileage = cursor.getInt(2)
            
            // Ищем запчасти с такими же датой и пробегом
            val partsCursor = db.query("""
                SELECT id 
                FROM parts 
                WHERE installDate = ? AND installMileage = ?
            """, arrayOf(breakdownDate.toString(), breakdownMileage.toString()))
            
            val partIds = mutableListOf<Long>()
            while (partsCursor.moveToNext()) {
                partIds.add(partsCursor.getLong(0))
            }
            partsCursor.close()
            
            // Если нашли запчасти, готовим обновление
            if (partIds.isNotEmpty()) {
                // Простая JSON сериализация: [1,2,3]
                val installedPartIdsJson = "[${partIds.joinToString(",")}]"
                updates.add(Pair(breakdownId, installedPartIdsJson))
            }
        }
        cursor.close()
        
        // Применяем все обновления
        for ((breakdownId, installedPartIdsJson) in updates) {
            db.execSQL(
                "UPDATE breakdowns SET installedPartIds = ? WHERE id = ?",
                arrayOf(installedPartIdsJson, breakdownId.toString())
            )
        }
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Проверяем, существует ли уже колонка purchaseDate
        val cursor = db.query("PRAGMA table_info(cars)")
        var columnExists = false
        while (cursor.moveToNext()) {
            val columnName = cursor.getString(cursor.getColumnIndex("name"))
            if (columnName == "purchaseDate") {
                columnExists = true
                break
            }
        }
        cursor.close()
        
        // Добавляем поле purchaseDate только если его еще нет
        if (!columnExists) {
            db.execSQL("ALTER TABLE cars ADD COLUMN purchaseDate INTEGER DEFAULT NULL")
        }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем новые поля в таблицу breakdowns для типов обслуживания
        db.execSQL("ALTER TABLE breakdowns ADD COLUMN maintenanceType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE breakdowns ADD COLUMN linkedConsumableIds TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE breakdowns ADD COLUMN isServiceMaintenance INTEGER NOT NULL DEFAULT 0")
        
        // Добавляем новое поле в таблицу consumables для связи с ТО
        db.execSQL("ALTER TABLE consumables ADD COLUMN linkedMaintenanceId INTEGER DEFAULT NULL")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Устанавливаем тип "REPAIR" для всех старых записей без типа
        db.execSQL("UPDATE breakdowns SET maintenanceType = 'REPAIR' WHERE maintenanceType IS NULL")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем поле maintenanceType в таблицу parts
        db.execSQL("ALTER TABLE parts ADD COLUMN maintenanceType TEXT DEFAULT NULL")
        
        // Устанавливаем тип "REPAIR" для всех существующих запчастей,
        // которые были добавлены, когда все поломки были по умолчанию ремонтами
        // (запчасти, добавленные напрямую через форму, останутся с NULL - это правильно)
        db.execSQL("""
            UPDATE parts 
            SET maintenanceType = 'REPAIR' 
            WHERE installationType = 'Сервис'
        """)
    }
}

