package com.carlog.data.repository

import com.carlog.data.local.dao.PartDao
import com.carlog.util.FileHelper

/**
 * Удаляет запчасти, созданные событием (обслуживанием или ДТП), вместе с их фото.
 *
 * Берутся только запчасти той же машины: ссылка в `installedPartIds` хранится без FK,
 * и чужую запись по повисшему id трогать нельзя.
 */
internal suspend fun deleteEventParts(partDao: PartDao, carId: Long, partIds: List<Long>?) {
    if (partIds.isNullOrEmpty()) return
    partDao.getPartsByIds(partIds.distinct())
        .filter { it.carId == carId }
        .forEach { part ->
            FileHelper.deleteFiles(part.photosPaths)
            partDao.deletePart(part)
        }
}
