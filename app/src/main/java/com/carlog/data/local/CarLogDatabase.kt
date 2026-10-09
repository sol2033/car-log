package com.carlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.NameSuggestionDao
import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.data.local.dao.TollPaymentDao
import com.carlog.domain.model.Accident
import com.carlog.domain.model.Breakdown
import com.carlog.domain.model.Car
import com.carlog.domain.model.CarDocument
import com.carlog.domain.model.Consumable
import com.carlog.domain.model.Expense
import com.carlog.domain.model.Fine
import com.carlog.domain.model.OilTopUp
import com.carlog.domain.model.Part
import com.carlog.domain.model.Refueling
import com.carlog.domain.model.TollPayment

@Database(
    entities = [
        Car::class,
        Part::class,
        Breakdown::class,
        Accident::class,
        Consumable::class,
        Refueling::class,
        Expense::class,
        CarDocument::class,
        Fine::class,
        OilTopUp::class,
        TollPayment::class
    ],
    version = 28,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CarLogDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun partDao(): PartDao
    abstract fun breakdownDao(): BreakdownDao
    abstract fun accidentDao(): AccidentDao
    abstract fun consumableDao(): ConsumableDao
    abstract fun refuelingDao(): RefuelingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun carDocumentDao(): CarDocumentDao
    abstract fun fineDao(): FineDao
    abstract fun oilTopUpDao(): OilTopUpDao
    abstract fun tollPaymentDao(): TollPaymentDao
    abstract fun nameSuggestionDao(): NameSuggestionDao
    
    companion object {
        const val DATABASE_NAME = "car_log_database"
    }
}
