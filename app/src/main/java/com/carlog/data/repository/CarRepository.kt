package com.carlog.data.repository

import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.CarDocumentDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.FineDao
import com.carlog.data.local.dao.OilTopUpDao
import com.carlog.domain.model.Car
import com.carlog.util.FileHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao,
    private val breakdownDao: BreakdownDao,
    private val refuelingDao: RefuelingDao,
    private val consumableDao: ConsumableDao,
    private val partDao: PartDao,
    private val accidentDao: AccidentDao,
    private val expenseDao: ExpenseDao,
    private val carDocumentDao: CarDocumentDao,
    private val oilTopUpDao: OilTopUpDao,
    private val fineDao: FineDao
) {
    fun getAllCars(): Flow<List<Car>> {
        return carDao.getAllCars()
    }
    
    fun getCarById(carId: Long): Flow<Car?> {
        return carDao.getCarById(carId)
    }
    
    suspend fun getCarByIdOnce(carId: Long): Car? {
        return carDao.getCarByIdOnce(carId)
    }
    
    suspend fun insertCar(car: Car): Long {
        return carDao.insertCar(car)
    }
    
    suspend fun updateCar(car: Car) {
        carDao.updateCar(car)
    }
    
    suspend fun deleteCar(car: Car) {
        // Записи машины удалит каскад на уровне БД, но файлы в хранилище так не исчезнут —
        // собираем и чистим их до удаления, иначе фото и PDF остаются навсегда
        // и продолжают попадать в каждый бэкап
        FileHelper.deleteFiles(car.photosPaths)
        FileHelper.deleteFiles(car.mainPhotoPath?.let { listOf(it) })

        partDao.getPartsByCarId(car.id).firstOrNull()?.forEach {
            FileHelper.deleteFiles(it.photosPaths)
        }
        breakdownDao.getBreakdownsByCarId(car.id).firstOrNull()?.forEach {
            FileHelper.deleteFiles(it.photosPaths)
        }
        accidentDao.getAccidentsByCarId(car.id).firstOrNull()?.forEach { accident ->
            FileHelper.deleteFiles(accident.photosPaths)
            FileHelper.deleteFiles(accident.documentPath?.let { listOf(it) })
        }
        carDocumentDao.getDocumentsByCarId(car.id).firstOrNull()?.forEach { document ->
            FileHelper.deleteFiles(document.photoPath?.let { listOf(it) })
        }
        // Штрафы (с 1.3.0) сюда не добавили — фото и PDF постановлений удалённой машины
        // оставались в хранилище навсегда. У платных дорог вложений нет
        fineDao.getFinesByCarId(car.id).firstOrNull()?.forEach { fine ->
            FileHelper.deleteFiles(fine.photosPaths)
        }

        carDao.deleteCar(car)
    }
    
    suspend fun updateMileage(carId: Long, mileage: Int) {
        carDao.updateMileage(carId, mileage, System.currentTimeMillis())
    }
    
    /**
     * Пользователь просит считать средний расход топлива заново (была пропущена заправка):
     * точкой отсчёта станет следующая добавленная заправка (§4.5 бизнес-логики).
     */
    suspend fun setFuelResetPending(carId: Long, pending: Boolean) {
        carDao.setFuelResetPending(carId, pending)
    }

    /**
     * Объём между MIN и MAX щупа. Точечный UPDATE, а не updateCar(снимок): снимок машины,
     * взятый экраном, мог устареть (пробег, флаги), и полная перезапись затёрла бы изменения
     */
    suspend fun setOilDipstickVolume(carId: Long, volume: Double, isGuess: Boolean) {
        carDao.setOilDipstickVolume(carId, volume, isGuess)
    }

    suspend fun isFuelResetPending(carId: Long): Boolean {
        return carDao.getCarByIdOnce(carId)?.fuelResetPending == true
    }

    /**
     * Забирает запрос нового отсчёта: возвращает true, если он был, и сразу снимает флаг —
     * точкой отсчёта становится ровно одна заправка, та, что добавляется сейчас.
     */
    suspend fun consumeFuelResetPending(carId: Long): Boolean {
        if (!isFuelResetPending(carId)) return false
        carDao.setFuelResetPending(carId, false)
        return true
    }

    suspend fun getCarsCount(): Int {
        return carDao.getCarsCount()
    }
    
    // Оптимизированное обновление пробега при добавлении/редактировании записи
    suspend fun updateCarMileageIfNeeded(carId: Long, newMileage: Int) {
        val currentCar = carDao.getCarByIdOnce(carId) ?: return
        
        // Если новый пробег не больше текущего - ничего не делаем
        if (newMileage <= currentCar.currentMileage) {
            return
        }
        
        // Обновляем пробег на новое значение
        updateMileage(carId, newMileage)
    }
    
    // Максимальный пробег, зафиксированный в записях машины (0 — если записей нет)
    suspend fun getMaxRecordedMileage(carId: Long): Int {
        val maxMileages = listOfNotNull(
            breakdownDao.getMaxMileage(carId),
            refuelingDao.getMaxMileage(carId),
            consumableDao.getMaxInstallationMileage(carId),
            consumableDao.getMaxReplacementMileage(carId),
            partDao.getMaxMileage(carId),
            partDao.getMaxBreakdownMileage(carId),
            accidentDao.getMaxMileage(carId),
            expenseDao.getMaxMileage(carId),
            oilTopUpDao.getMaxMileage(carId)
        )
        return maxMileages.maxOrNull() ?: 0
    }

    // Сколько записей машины содержат пробег больше указанного
    suspend fun getRecordsCountAboveMileage(carId: Long, mileage: Int): Int {
        return breakdownDao.getCountAboveMileage(carId, mileage) +
            refuelingDao.getCountAboveMileage(carId, mileage) +
            consumableDao.getCountAboveMileage(carId, mileage) +
            partDao.getCountAboveMileage(carId, mileage) +
            accidentDao.getCountAboveMileage(carId, mileage) +
            expenseDao.getCountAboveMileage(carId, mileage) +
            oilTopUpDao.getCountAboveMileage(carId, mileage)
    }

    /**
     * Уменьшение пробега вручную (с экрана деталей машины, после подтверждения пользователем).
     * Пробег — производная величина (максимум по всем записям), поэтому одного обновления
     * `cars.currentMileage` недостаточно: записи с большим пробегом «прижимаются» к новому
     * значению, иначе `syncAllCarsMileage()` вернёт прежний максимум при следующем запуске.
     */
    suspend fun lowerMileageWithRecords(carId: Long, newMileage: Int) {
        val now = System.currentTimeMillis()

        breakdownDao.clampMileageTo(carId, newMileage, now)
        refuelingDao.clampMileageTo(carId, newMileage, now)
        consumableDao.clampInstallationMileageTo(carId, newMileage, now)
        consumableDao.clampReplacementMileageTo(carId, newMileage, now)
        partDao.clampInstallMileageTo(carId, newMileage, now)
        partDao.clampBreakdownMileageTo(carId, newMileage, now)
        partDao.refreshMileageDriven(carId, now)
        accidentDao.clampMileageTo(carId, newMileage, now)
        expenseDao.clampMileageTo(carId, newMileage, now)
        oilTopUpDao.clampMileageTo(carId, newMileage, now)
        carDao.clampPurchaseMileage(carId, newMileage, now)

        updateMileage(carId, newMileage)
    }

    // Оптимизированное обновление пробега после удаления записи
    suspend fun updateCarMileageAfterDelete(carId: Long, deletedMileage: Int) {
        val currentCar = carDao.getCarByIdOnce(carId) ?: return
        
        // Если удаленный пробег меньше текущего - ничего не делаем
        // Если равен или больше - пересчитываем максимум
        if (deletedMileage < currentCar.currentMileage) {
            return
        }
        
        // Нижняя граница — пробег при покупке: раньше при удалении последней записи
        // пробег машины обнулялся (максимум по пустому списку превращался в 0).
        // Ни записей, ни пробега покупки — опереться не на что, пробег не трогаем
        val maxMileage = maxKnownMileage(currentCar) ?: return
        updateMileage(carId, maxMileage)
    }
    
    // Синхронизация пробега для всех автомобилей (для миграции существующих данных)
    suspend fun syncAllCarsMileage() {
        val cars = carDao.getAllCarsOnce()
        cars.forEach { car ->
            val maxMileage = maxKnownMileage(car)
            if (maxMileage != null && maxMileage > car.currentMileage) {
                updateMileage(car.id, maxMileage)
            }
        }
    }

    /**
     * Максимальный пробег, известный по записям машины и её пробегу при покупке;
     * null — нет ни того, ни другого.
     *
     * Набор источников тот же, что у [getMaxRecordedMileage] (включая пробег поломки
     * запчасти): раньше пересчёт после удаления и синхронизация при запуске его не видели,
     * и пробег машины мог оказаться ниже записанного в журнале
     */
    private suspend fun maxKnownMileage(car: Car): Int? {
        val recorded = getMaxRecordedMileage(car.id).takeIf { it > 0 }
        return listOfNotNull(recorded, car.purchaseMileage).maxOrNull()
    }
}
