package com.carlog.data.local

import androidx.room.migration.Migration
import com.carlog.data.local.repair.EventPartLinkRepair
import com.carlog.data.local.repair.RepairEvent
import com.carlog.data.local.repair.RepairPart
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

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем в таблицу consumables ForeignKey(carId -> cars.id, ON DELETE CASCADE) и индекс по carId.
        // SQLite не умеет добавлять внешний ключ через ALTER TABLE, поэтому пересоздаём таблицу:
        // создаём новую с нужной схемой, копируем данные, удаляем старую, переименовываем.
        // Схема new-таблицы дословно совпадает с тем, что генерирует Room для @Entity Consumable (v18),
        // иначе Room упадёт при старте на проверке схемы.

        // Откладываем проверку внешних ключей до конца транзакции (как делают авто-миграции Room).
        db.execSQL("PRAGMA defer_foreign_keys = TRUE")

        // 1. Новая таблица с FK
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `consumables_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`carId` INTEGER NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`manufacturer` TEXT, " +
                "`articleNumber` TEXT, " +
                "`installationMileage` INTEGER NOT NULL, " +
                "`installationDate` INTEGER NOT NULL, " +
                "`linkedMaintenanceId` INTEGER, " +
                "`replacementMileage` INTEGER, " +
                "`replacementDate` INTEGER, " +
                "`cost` REAL, " +
                "`isInstalledAtService` INTEGER NOT NULL, " +
                "`serviceCost` REAL, " +
                "`volume` REAL, " +
                "`replacementIntervalMileage` INTEGER, " +
                "`replacementIntervalDays` INTEGER, " +
                "`isActive` INTEGER NOT NULL, " +
                "`notes` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`carId`) REFERENCES `cars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        // 2. Копируем только расходники, у которых машина ещё существует
        //    (заодно чистим возможных "сирот" от удалённых ранее машин и не нарушаем новый FK).
        db.execSQL(
            "INSERT INTO `consumables_new` (" +
                "id, carId, category, manufacturer, articleNumber, installationMileage, installationDate, " +
                "linkedMaintenanceId, replacementMileage, replacementDate, cost, isInstalledAtService, " +
                "serviceCost, volume, replacementIntervalMileage, replacementIntervalDays, isActive, notes, " +
                "createdAt, updatedAt) " +
                "SELECT id, carId, category, manufacturer, articleNumber, installationMileage, installationDate, " +
                "linkedMaintenanceId, replacementMileage, replacementDate, cost, isInstalledAtService, " +
                "serviceCost, volume, replacementIntervalMileage, replacementIntervalDays, isActive, notes, " +
                "createdAt, updatedAt " +
                "FROM `consumables` WHERE carId IN (SELECT id FROM cars)"
        )

        // 3. Удаляем старую таблицу и переименовываем новую
        db.execSQL("DROP TABLE `consumables`")
        db.execSQL("ALTER TABLE `consumables_new` RENAME TO `consumables`")

        // 4. Индекс по carId (имя должно совпадать с ожидаемым Room)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_consumables_carId` ON `consumables` (`carId`)")
    }
}


val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Создаём таблицу документов автомобиля (страховки, налог и т.п.).
        // Схема дословно совпадает с тем, что генерирует Room для @Entity CarDocument.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documents` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`carId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`customName` TEXT, " +
                "`number` TEXT, " +
                "`organization` TEXT, " +
                "`startDate` INTEGER, " +
                "`expiryDate` INTEGER NOT NULL, " +
                "`cost` REAL, " +
                "`photoPath` TEXT, " +
                "`notes` TEXT, " +
                "`isActive` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`carId`) REFERENCES `cars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_carId` ON `documents` (`carId`)")
    }
}

/**
 * Ремонт связей «ТО ↔ расходники», порванных прежним багом: формы редактирования собирали
 * запись заново и обнуляли поля, которых нет в форме. Из-за этого:
 * - редактирование ТО стирало `breakdowns.linkedConsumableIds` — удаление ТО переставало
 *   удалять его расходники;
 * - редактирование расходника стирало `consumables.linkedMaintenanceId` — его стоимость
 *   начинала считаться в статистике и внутри ТО, и как «отдельная».
 *
 * Обе стороны связи дублируют друг друга, поэтому уцелевшая половина позволяет восстановить
 * потерянную **точно**, без догадок. Что восстановить нельзя (статус поломки запчасти,
 * стоимость работ по ДТП, createdAt) — здесь не трогаем.
 *
 * Списки id хранятся как JSON-массив Gson (`[1,2,3]`), отсюда сборка строки вручную.
 */
/** Разбор JSON-массива id (`[1,2,3]`), как его пишет Gson-конвертер */
private fun parseIdList(json: String?): List<Long> =
    json?.trim()
        ?.removePrefix("[")?.removeSuffix("]")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?: emptyList()

/**
 * Восстановление связи «событие → его запчасти», потерянной при редактировании
 * (`installedPartIds` обнулялся). Чиним **только однозначные** случаи, иначе не трогаем:
 *
 * - запчасть создавалась событием, поэтому её `installationType` = «Сервис» (обслуживание)
 *   или «ДТП», а `installDate`/`installMileage` скопированы из события **точно**;
 * - на один такой набор признаков должно приходиться ровно одно событие — иначе непонятно,
 *   к какому из них привязывать;
 * - запчасти, уже привязанные к другому событию, кандидатами не считаются;
 * - для обслуживания дополнительно требуется **точное совпадение суммы**: `partsCost`
 *   считался как сумма цен своих запчастей и при поломке связи уцелел. Если в набор попадёт
 *   посторонняя запчасть — сумма не сойдётся, и случай будет пропущен.
 *
 * У ДТП проверки суммы нет: `repairCost` тем же багом обнулялся. Зато `installationType`
 * = «ДТП» ставится только запчастям, созданным аварией, — принадлежность событию известна,
 * неоднозначным может быть лишь выбор между двумя авариями в один день и пробег, а такие
 * пропускаются.
 *
 * Остальное разбирает экран «Проверка данных» — с показом кандидатов и решением пользователя.
 */
private fun repairEventPartLinks(db: SupportSQLiteDatabase) {
    // Запчасти, уже закреплённые за каким-либо событием
    val claimed = HashSet<Long>()
    db.query("SELECT installedPartIds FROM breakdowns WHERE installedPartIds IS NOT NULL").use { c ->
        while (c.moveToNext()) claimed.addAll(parseIdList(c.getString(0)))
    }
    db.query("SELECT installedPartIds FROM accidents WHERE installedPartIds IS NOT NULL").use { c ->
        while (c.moveToNext()) claimed.addAll(parseIdList(c.getString(0)))
    }

    val parts = mutableListOf<RepairPart>()
    db.query(
        "SELECT id, carId, installDate, installMileage, installationType, price, maintenanceType FROM parts"
    ).use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            parts.add(
                RepairPart(
                    id = id,
                    carId = c.getLong(1),
                    installDate = c.getLong(2),
                    installMileage = c.getInt(3),
                    installationType = c.getString(4),
                    maintenanceType = if (c.isNull(6)) null else c.getString(6),
                    price = c.getDouble(5),
                    claimed = claimed.contains(id)
                )
            )
        }
    }

    val breakdowns = mutableListOf<RepairEvent>()
    db.query(
        "SELECT id, carId, breakdownDate, breakdownMileage, partsCost, installedPartIds, maintenanceType FROM breakdowns"
    ).use { c ->
        while (c.moveToNext()) {
            breakdowns.add(
                RepairEvent(
                    id = c.getLong(0),
                    carId = c.getLong(1),
                    date = c.getLong(2),
                    mileage = c.getInt(3),
                    maintenanceType = if (c.isNull(6)) null else c.getString(6),
                    partsCost = c.getDouble(4),
                    alreadyLinked = !c.isNull(5)
                )
            )
        }
    }

    val accidents = mutableListOf<RepairEvent>()
    db.query("SELECT id, carId, date, mileage, installedPartIds FROM accidents").use { c ->
        while (c.moveToNext()) {
            accidents.add(
                RepairEvent(
                    id = c.getLong(0),
                    carId = c.getLong(1),
                    date = c.getLong(2),
                    mileage = c.getInt(3),
                    alreadyLinked = !c.isNull(4)
                )
            )
        }
    }

    // Правила сопоставления и их тесты — в EventPartLinkRepair
    val breakdownLinks = EventPartLinkRepair.resolveBreakdownLinks(breakdowns, parts)
    breakdownLinks.forEach { (breakdownId, partIds) ->
        db.execSQL(
            "UPDATE breakdowns SET installedPartIds = '${partIds.joinToString(",", "[", "]")}' " +
                "WHERE id = $breakdownId"
        )
    }

    // Запчасти, только что привязанные к обслуживаниям, для ДТП уже заняты
    val linkedToBreakdowns = breakdownLinks.values.flatten().toSet()
    val partsAfterBreakdowns = parts.map {
        if (linkedToBreakdowns.contains(it.id)) it.copy(claimed = true) else it
    }

    EventPartLinkRepair.resolveAccidentLinks(accidents, partsAfterBreakdowns).forEach { (accidentId, partIds) ->
        db.execSQL(
            "UPDATE accidents SET installedPartIds = '${partIds.joinToString(",", "[", "]")}' " +
                "WHERE id = $accidentId"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        repairEventPartLinks(db)

        // 1. Расходник знает своё ТО, а ТО его потеряло — восстанавливаем список у ТО
        db.execSQL(
            "UPDATE breakdowns SET linkedConsumableIds = (" +
                "SELECT '[' || group_concat(c.id) || ']' FROM consumables c " +
                "WHERE c.linkedMaintenanceId = breakdowns.id" +
                ") WHERE linkedConsumableIds IS NULL AND EXISTS (" +
                "SELECT 1 FROM consumables c WHERE c.linkedMaintenanceId = breakdowns.id)"
        )

        // 2. Расходник ссылается на несуществующее ТО: так бывало, если ТО сначала
        // отредактировали (связь у него обнулялась), а потом удалили — каскад не сработал,
        // расходник остался «привязанным» к пропавшей записи и выпадал из статистики
        // (не отдельный и ни в одном ТО). Ссылку снимаем — расходник становится отдельным
        db.execSQL(
            "UPDATE consumables SET linkedMaintenanceId = NULL " +
                "WHERE linkedMaintenanceId IS NOT NULL " +
                "AND linkedMaintenanceId NOT IN (SELECT id FROM breakdowns)"
        )

        // 3. Обратный случай: ТО помнит расходник, а расходник потерял ссылку на ТО.
        // Сравниваем по подстроке ,id, — id уникальны, поэтому совпадение однозначно
        db.execSQL(
            "UPDATE consumables SET linkedMaintenanceId = (" +
                "SELECT b.id FROM breakdowns b WHERE b.linkedConsumableIds IS NOT NULL " +
                "AND (',' || replace(replace(b.linkedConsumableIds, '[', ''), ']', '') || ',') " +
                "LIKE ('%,' || consumables.id || ',%') LIMIT 1" +
                ") WHERE linkedMaintenanceId IS NULL AND EXISTS (" +
                "SELECT 1 FROM breakdowns b WHERE b.linkedConsumableIds IS NOT NULL " +
                "AND (',' || replace(replace(b.linkedConsumableIds, '[', ''), ']', '') || ',') " +
                "LIKE ('%,' || consumables.id || ',%'))"
        )
    }
}

/**
 * Новый отсчёт среднего расхода топлива: пропущенная заправка навсегда сбивала среднее,
 * теперь пользователь может назначить новую точку отсчёта (§4.5 бизнес-логики).
 *
 * `refuelings.isConsumptionResetPoint` — заправка, с которой расход считается заново;
 * `cars.fuelResetPending` — запрос пользователя: точкой отсчёта станет следующая заправка.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE refuelings ADD COLUMN isConsumptionResetPoint INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE cars ADD COLUMN fuelResetPending INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * Расходники «Другое» в ТО, список работ с ценами и скрытие мелочи из модуля «Запчасти».
 *
 * `consumables.customName` — название позиции категории «Другое»;
 * `breakdowns.workItems` — JSON-список работ (сумма их стоимостей лежит в `serviceCost`);
 * `parts.showInPartsList` — показывать ли запчасть в модуле «Запчасти» (по умолчанию да,
 * поведение существующих записей не меняется).
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE consumables ADD COLUMN customName TEXT")
        db.execSQL("ALTER TABLE breakdowns ADD COLUMN workItems TEXT")
        db.execSQL("ALTER TABLE parts ADD COLUMN showInPartsList INTEGER NOT NULL DEFAULT 1")
    }
}
