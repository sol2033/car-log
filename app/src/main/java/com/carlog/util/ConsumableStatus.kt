package com.carlog.util

import com.carlog.domain.model.Consumable
import java.util.concurrent.TimeUnit

object ConsumableStatus {
    
    enum class Status {
        NORMAL,   // Зеленый - более 50% ресурса
        WARNING,  // Желтый - от 50% до порога замены
        CRITICAL  // Красный - пора менять или просрочен
    }
    
    data class StatusInfo(
        val status: Status,
        val progressPercent: Float, // 0.0 - 1.0
        val remainingMileage: Int?, // Сколько км осталось
        val remainingDays: Int? // Сколько дней осталось
    )
    
    fun calculateStatus(
        consumable: Consumable,
        currentMileage: Int
    ): StatusInfo {
        if (!consumable.isActive) {
            return StatusInfo(Status.NORMAL, 0f, null, null)
        }
        
        var worstStatus = Status.NORMAL
        var maxProgress = 0f
        var remainingMileage: Int? = null
        var remainingDays: Int? = null
        
        // Проверка по пробегу
        if (consumable.replacementIntervalMileage != null) {
            val mileagePassed = currentMileage - consumable.installationMileage
            val mileageProgress = mileagePassed.toFloat() / consumable.replacementIntervalMileage
            maxProgress = maxOf(maxProgress, mileageProgress)
            
            val remaining = consumable.replacementIntervalMileage - mileagePassed
            remainingMileage = remaining
            
            worstStatus = when {
                mileageProgress >= 1.0f || remaining <= 500 -> Status.CRITICAL
                mileageProgress >= 0.5f -> Status.WARNING
                else -> Status.NORMAL
            }
        }
        
        // Проверка по дате
        if (consumable.replacementIntervalDays != null) {
            val currentTime = System.currentTimeMillis()
            val daysPassed = TimeUnit.MILLISECONDS.toDays(
                currentTime - consumable.installationDate
            ).toInt()
            val dateProgress = daysPassed.toFloat() / consumable.replacementIntervalDays
            maxProgress = maxOf(maxProgress, dateProgress)
            
            val remaining = consumable.replacementIntervalDays - daysPassed
            remainingDays = remaining
            
            val dateStatus = when {
                dateProgress >= 1.0f || remaining <= 14 -> Status.CRITICAL
                dateProgress >= 0.5f -> Status.WARNING
                else -> Status.NORMAL
            }
            
            // Берем худший статус
            if (dateStatus.ordinal > worstStatus.ordinal) {
                worstStatus = dateStatus
            }
        }
        
        return StatusInfo(
            status = worstStatus,
            progressPercent = maxProgress.coerceIn(0f, 1f),
            remainingMileage = remainingMileage,
            remainingDays = remainingDays
        )
    }
}
