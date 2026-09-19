package com.carlog.data.repository

import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.PartDao
import com.carlog.domain.model.Part
import com.carlog.util.FileHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartRepository @Inject constructor(
    private val partDao: PartDao,
    private val breakdownDao: BreakdownDao,
    private val accidentDao: AccidentDao
) {
    fun getPartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getPartsByCarId(carId)
    
    fun getActivePartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getActivePartsByCarId(carId)
    
    fun getBrokenPartsByCarId(carId: Long): Flow<List<Part>> =
        partDao.getBrokenPartsByCarId(carId)
    
    fun getPartById(partId: Long): Flow<Part?> =
        partDao.getPartById(partId)
    
    suspend fun insertPart(part: Part): Long =
        partDao.insertPart(part)
    
    suspend fun updatePart(part: Part) =
        partDao.updatePart(part)
    
    suspend fun deletePart(part: Part) {
        // Фото принадлежат только этой записи — удаляем вместе с ней,
        // иначе файлы остаются в хранилище навсегда и раздувают бэкапы
        FileHelper.deleteFiles(part.photosPaths)
        // Ссылку на удалённую запчасть надо убрать из событий: FK тут нет, и повисшая
        // ссылка ломала форму обслуживания — список запчастей включался по ней и выходил
        // пустым, после чего валидация не давала сохранить запись вообще
        detachFromEvents(part)
        partDao.deletePart(part)
    }

    /**
     * Убирает id запчасти из `installedPartIds` обслуживаний и ДТП её машины.
     *
     * Стоимость события при этом **не трогаем**: сходится она или нет, решает пользователь
     * на экране «Проверка данных» (правило `breakdownCostMismatch`). Событие, у которого
     * позиций не осталось, становится событием с запчастями «общей суммой» — деньги
     * остаются на месте.
     */
    private suspend fun detachFromEvents(part: Part) {
        val now = System.currentTimeMillis()

        breakdownDao.getBreakdownsByCarId(part.carId).firstOrNull().orEmpty()
            .filter { it.installedPartIds?.contains(part.id) == true }
            .forEach { breakdown ->
                val kept = breakdown.installedPartIds.orEmpty().filterNot { it == part.id }
                breakdownDao.updateBreakdown(
                    breakdown.copy(installedPartIds = kept.ifEmpty { null }, updatedAt = now)
                )
            }

        accidentDao.getAccidentsByCarId(part.carId).firstOrNull().orEmpty()
            .filter { it.installedPartIds?.contains(part.id) == true }
            .forEach { accident ->
                val kept = accident.installedPartIds.orEmpty().filterNot { it == part.id }
                accidentDao.updateAccident(
                    accident.copy(installedPartIds = kept.ifEmpty { null }, updatedAt = now)
                )
            }
    }

    suspend fun deletePartsByCarId(carId: Long) {
        partDao.getPartsByCarId(carId).firstOrNull()?.forEach {
            FileHelper.deleteFiles(it.photosPaths)
        }
        partDao.deletePartsByCarId(carId)
    }
    
    suspend fun getPartsCountByCarId(carId: Long): Int =
        partDao.getPartsCountByCarId(carId)
    
    suspend fun getTotalPartsCostByCarId(carId: Long): Double =
        partDao.getTotalPartsCostByCarId(carId) ?: 0.0
}
