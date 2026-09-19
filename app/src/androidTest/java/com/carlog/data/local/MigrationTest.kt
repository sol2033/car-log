package com.carlog.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Прогон миграций на настоящем SQLite устройства.
 *
 * Запуск: `./gradlew connectedDebugAndroidTest` (нужен подключённый телефон или эмулятор).
 *
 * Главный кейс — 19→20: ремонт связей «ТО ↔ расходники», порванных багом редактирования
 * (см. §16 технической документации). Миграция обязана чинить только достоверные случаи
 * и не трогать законно отдельные записи.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CarLogDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun insertCar(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long) {
        db.execSQL(
            "INSERT INTO cars (id, brand, model, fuelType, hasGasEquipment, currentMileage, createdAt, updatedAt) " +
                "VALUES ($id, 'Lada', 'Vesta', 'Бензин', 0, 100000, 0, 0)"
        )
    }

    private fun insertBreakdown(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        linkedConsumableIds: String?
    ) {
        val linked = linkedConsumableIds?.let { "'$it'" } ?: "NULL"
        db.execSQL(
            "INSERT INTO breakdowns (id, carId, title, description, breakdownDate, breakdownMileage, " +
                "maintenanceType, linkedConsumableIds, isServiceMaintenance, partsCost, totalCost, " +
                "isWarrantyRepair, createdAt, updatedAt) " +
                "VALUES ($id, 1, 'ТО', 'описание', 0, 90000, 'SCHEDULED_SERVICE', $linked, 0, 5000, 5000, 0, 0, 0)"
        )
    }

    private fun insertConsumable(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        linkedMaintenanceId: Long?
    ) {
        val linked = linkedMaintenanceId?.toString() ?: "NULL"
        db.execSQL(
            "INSERT INTO consumables (id, carId, category, installationMileage, installationDate, " +
                "linkedMaintenanceId, isInstalledAtService, isActive, createdAt, updatedAt) " +
                "VALUES ($id, 1, 'Масло в двигателе', 90000, 0, $linked, 0, 1, 0, 0)"
        )
    }

    @Test
    fun migrate19To20_восстанавливаетСвязиТОиРасходников() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)

            // ТО 10 потеряло список, расходники 3 и 4 связь помнят
            insertBreakdown(this, id = 10, linkedConsumableIds = null)
            insertConsumable(this, id = 3, linkedMaintenanceId = 10)
            insertConsumable(this, id = 4, linkedMaintenanceId = 10)

            // ТО 20 список помнит, а расходник 6 связь потерял (5 — цел)
            insertBreakdown(this, id = 20, linkedConsumableIds = "[5,6]")
            insertConsumable(this, id = 5, linkedMaintenanceId = 20)
            insertConsumable(this, id = 6, linkedMaintenanceId = null)

            // Расходник 7 законно отдельный, ТО 30 законно без расходников
            insertBreakdown(this, id = 30, linkedConsumableIds = null)
            insertConsumable(this, id = 7, linkedMaintenanceId = null)

            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        db.query("SELECT linkedConsumableIds FROM breakdowns WHERE id = 10").use {
            it.moveToFirst()
            assertEquals("[3,4]", it.getString(0))
        }
        db.query("SELECT linkedMaintenanceId FROM consumables WHERE id = 6").use {
            it.moveToFirst()
            assertEquals(20L, it.getLong(0))
        }

        // Законно отдельные записи не тронуты
        db.query("SELECT linkedConsumableIds FROM breakdowns WHERE id = 30").use {
            it.moveToFirst()
            assertNull(it.getString(0))
        }
        db.query("SELECT linkedMaintenanceId FROM consumables WHERE id = 7").use {
            it.moveToFirst()
            assertEquals(true, it.isNull(0))
        }
    }

    @Test
    fun migrate19To20_освобождаетРасходникиУдалённогоТО() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            // ТО, на которое ссылается расходник, было удалено (каскад не сработал из-за бага)
            insertConsumable(this, id = 8, linkedMaintenanceId = 999)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        db.query("SELECT linkedMaintenanceId FROM consumables WHERE id = 8").use {
            it.moveToFirst()
            assertEquals(
                "ссылка на несуществующее ТО должна сняться — иначе расходник невидим в статистике",
                true,
                it.isNull(0)
            )
        }
    }

    private fun insertRepairBreakdown(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        mileage: Int,
        partsCost: Double,
        date: Long = 1000,
        installedPartIds: String? = null
    ) {
        val linked = installedPartIds?.let { "'$it'" } ?: "NULL"
        db.execSQL(
            "INSERT INTO breakdowns (id, carId, title, description, breakdownDate, breakdownMileage, " +
                "maintenanceType, installedPartIds, isServiceMaintenance, partsCost, totalCost, " +
                "isWarrantyRepair, createdAt, updatedAt) " +
                "VALUES ($id, 1, 'Ремонт', 'описание', $date, $mileage, 'REPAIR', $linked, 0, " +
                "$partsCost, $partsCost, 0, 0, 0)"
        )
    }

    private fun insertPart(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        mileage: Int,
        price: Double,
        installationType: String,
        date: Long = 1000,
        maintenanceType: String? = "REPAIR"
    ) {
        val type = maintenanceType?.let { "'$it'" } ?: "NULL"
        db.execSQL(
            "INSERT INTO parts (id, carId, name, installDate, installMileage, installationType, " +
                "price, isBroken, maintenanceType, createdAt, updatedAt) " +
                "VALUES ($id, 1, 'Деталь $id', $date, $mileage, '$installationType', $price, 0, $type, 0, 0)"
        )
    }

    private fun insertAccident(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        mileage: Int,
        date: Long = 1000
    ) {
        db.execSQL(
            "INSERT INTO accidents (id, carId, date, mileage, damageDescription, severity, " +
                "isUserAtFault, repairCost, createdAt, updatedAt) " +
                "VALUES ($id, 1, $date, $mileage, 'Бампер', 'Средняя', 0, 0, 0, 0)"
        )
    }

    private fun linkedPartIds(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, id: Long): String? {
        db.query("SELECT installedPartIds FROM $table WHERE id = $id").use {
            it.moveToFirst()
            return if (it.isNull(0)) null else it.getString(0)
        }
    }

    @Test
    fun migrate19To20_привязываетЗапчастиКогдаСуммаСходится() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            insertRepairBreakdown(this, id = 40, mileage = 80_000, partsCost = 7_500.0)
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Сервис")
            insertPart(this, id = 2, mileage = 80_000, price = 2_500.0, installationType = "Сервис")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertEquals("[1,2]", linkedPartIds(db, "breakdowns", 40))
    }

    @Test
    fun migrate19To20_неТрогаетЕслиСуммаНеСходится() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            // Сумма запчастей 5 000, а в записи 7 500 — значит набор неполный или чужой
            insertRepairBreakdown(this, id = 41, mileage = 80_000, partsCost = 7_500.0)
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Сервис")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertNull(linkedPartIds(db, "breakdowns", 41))
    }

    @Test
    fun migrate19To20_неТрогаетПриДвухКандидатах() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            // Два обслуживания в один день и с одним пробегом — к какому привязывать, неизвестно
            insertRepairBreakdown(this, id = 42, mileage = 80_000, partsCost = 5_000.0)
            insertRepairBreakdown(this, id = 43, mileage = 80_000, partsCost = 5_000.0)
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Сервис")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertNull(linkedPartIds(db, "breakdowns", 42))
        assertNull(linkedPartIds(db, "breakdowns", 43))
    }

    @Test
    fun migrate19To20_неЗабираетЗапчастьУДругогоСобытия() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            // Запчасть 1 уже принадлежит обслуживанию 44
            insertRepairBreakdown(this, id = 44, mileage = 80_000, partsCost = 5_000.0, installedPartIds = "[1]")
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Сервис")
            // Другое обслуживание в тот же день, но с другим пробегом — своих запчастей у него нет
            insertRepairBreakdown(this, id = 45, mileage = 81_000, partsCost = 5_000.0)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertEquals("[1]", linkedPartIds(db, "breakdowns", 44))
        assertNull(linkedPartIds(db, "breakdowns", 45))
    }

    @Test
    fun migrate19To20_привязываетЗапчастиКДТПбезПроверкиСуммы() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            // У ДТП стоимость ремонта обнулена тем же багом, сверять сумму не с чем
            insertAccident(this, id = 50, mileage = 70_000)
            insertPart(
                this, id = 1, mileage = 70_000, price = 9_000.0,
                installationType = "ДТП", maintenanceType = null
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertEquals("[1]", linkedPartIds(db, "accidents", 50))
    }

    @Test
    fun migrate19To20_неТрогаетЗапчастьБезТипаОбслуживания() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            insertRepairBreakdown(this, id = 47, mileage = 80_000, partsCost = 5_000.0)
            // Запчасть добавлена вручную через раздел «Запчасти»: тип обслуживания не проставлен
            insertPart(
                this, id = 1, mileage = 80_000, price = 5_000.0,
                installationType = "Сервис", maintenanceType = null
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertNull(linkedPartIds(db, "breakdowns", 47))
    }

    @Test
    fun migrate19To20_неТрогаетСамостоятельноДобавленнуюЗапчасть() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            insertRepairBreakdown(this, id = 46, mileage = 80_000, partsCost = 5_000.0)
            // Запчасть установлена самостоятельно — событием создана быть не могла
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Самостоятельно")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)

        assertNull(linkedPartIds(db, "breakdowns", 46))
    }

    /** Схема между 19 и 20 не менялась — миграция только чинит данные */
    @Test
    fun migrate19To20_схемаНеМеняется() {
        helper.createDatabase(testDb, 19).apply {
            insertCar(this, 1)
            close()
        }

        // runMigrationsAndValidate упадёт, если фактическая схема разойдётся с 20.json
        helper.runMigrationsAndValidate(testDb, 20, true, MIGRATION_19_20)
    }

    /**
     * 20→21: точка отсчёта расхода топлива. Существующие заправки и машины должны
     * получить выключенные флаги — расход продолжает считаться по всей истории.
     */
    @Test
    fun migrate20To21_добавляетФлагиНовогоОтсчётаРасхода() {
        helper.createDatabase(testDb, 20).apply {
            insertCar(this, 1)
            execSQL(
                "INSERT INTO refuelings (id, carId, date, mileage, liters, fuelType, isFullTank, " +
                    "createdAt, updatedAt) VALUES (1, 1, 0, 90000, 40.0, 'АИ-95', 1, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 21, true, MIGRATION_20_21)

        db.query("SELECT isConsumptionResetPoint FROM refuelings WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT fuelResetPending FROM cars WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    /**
     * 21→22: позиции «Другое» в ТО, список работ и скрытие запчастей из модуля.
     * Существующие запчасти обязаны остаться видимыми — иначе обновление «спрячет» раздел.
     */
    @Test
    fun migrate21To22_существующиеЗапчастиОстаютсяВидимыми() {
        helper.createDatabase(testDb, 21).apply {
            insertCar(this, 1)
            insertPart(this, id = 1, mileage = 80_000, price = 5_000.0, installationType = "Сервис")
            insertRepairBreakdown(this, id = 40, mileage = 80_000, partsCost = 5_000.0)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 22, true, MIGRATION_21_22)

        db.query("SELECT showInPartsList FROM parts WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT workItems FROM breakdowns WHERE id = 40").use {
            it.moveToFirst()
            assertNull(it.getString(0))
        }
    }

    /**
     * 22→23: модуль «Штрафы». Таблицы раньше не было, поэтому проверяем главное —
     * схема совпадает с ожидаемой Room (это делает сам runMigrationsAndValidate),
     * таблица пустая и в неё можно писать, а каскад по машине работает.
     */
    @Test
    fun migrate22To23_создаётПустуюТаблицуШтрафов() {
        helper.createDatabase(testDb, 22).apply {
            insertCar(this, 1)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 23, true, MIGRATION_22_23)

        db.query("SELECT COUNT(*) FROM fines").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }

        db.execSQL(
            "INSERT INTO fines (id, carId, date, amount, violationType, isPaid, createdAt, updatedAt) " +
                "VALUES (1, 1, 0, 500.0, 'Превышение скорости', 0, 0, 0)"
        )
        db.query("SELECT amount, isPaid FROM fines WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(500.0, it.getDouble(0), 0.01)
            assertEquals(0, it.getInt(1))
        }
    }

    /** Штрафы удалённой машины не должны пережить её: FK объявлен с ON DELETE CASCADE */
    @Test
    fun migrate22To23_штрафыУходятВместеСМашиной() {
        helper.createDatabase(testDb, 22).apply {
            insertCar(this, 1)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 23, true, MIGRATION_22_23)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO fines (id, carId, date, amount, violationType, isPaid, createdAt, updatedAt) " +
                "VALUES (1, 1, 0, 500.0, 'Парковка и остановка', 1, 0, 0)"
        )

        db.execSQL("DELETE FROM cars WHERE id = 1")

        db.query("SELECT COUNT(*) FROM fines").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    /**
     * 23→24: компенсация пострадавшему. Главное требование — **уже заведённые ДТП
     * не меняются**: колонка nullable и без значения по умолчанию, поэтому у старой записи
     * там NULL («не указана»), а не ноль, который соврал бы, что компенсации не было.
     * Стоимость ремонта и остальные поля обязаны дожить до новой версии нетронутыми.
     */
    @Test
    fun migrate23To24_староеДТПпереживаетДобавлениеКомпенсации() {
        helper.createDatabase(testDb, 23).apply {
            insertCar(this, 1)
            execSQL(
                "INSERT INTO accidents (id, carId, date, mileage, damageDescription, severity, " +
                    "isUserAtFault, osagoPayout, repairCost, createdAt, updatedAt) " +
                    "VALUES (60, 1, 1000, 70000, 'Бампер', 'Средняя', 0, 15000.0, 40000.0, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 24, true, MIGRATION_23_24)

        db.query("SELECT repairCost, osagoPayout, compensationPaid FROM accidents WHERE id = 60").use {
            it.moveToFirst()
            assertEquals(40_000.0, it.getDouble(0), 0.01)
            assertEquals(15_000.0, it.getDouble(1), 0.01)
            assertNull("у старой записи компенсация не указана, а не равна нулю",
                if (it.isNull(2)) null else it.getDouble(2))
        }
    }

    /** Дозаполнение старой карточки: колонка принимает значение и читается обратно */
    @Test
    fun migrate23To24_компенсациюМожноДозаполнить() {
        helper.createDatabase(testDb, 23).apply {
            insertCar(this, 1)
            execSQL(
                "INSERT INTO accidents (id, carId, date, mileage, damageDescription, severity, " +
                    "isUserAtFault, repairCost, createdAt, updatedAt) " +
                    "VALUES (61, 1, 1000, 70000, 'Бампер', 'Средняя', 1, 0.0, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 24, true, MIGRATION_23_24)
        db.execSQL("UPDATE accidents SET compensationPaid = 25000.0 WHERE id = 61")

        db.query("SELECT compensationPaid FROM accidents WHERE id = 61").use {
            it.moveToFirst()
            assertEquals(25_000.0, it.getDouble(0), 0.01)
        }
    }
}

