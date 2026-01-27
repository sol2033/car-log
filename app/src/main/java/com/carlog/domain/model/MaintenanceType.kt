package com.carlog.domain.model

/**
 * Тип обслуживания/ремонта
 */
enum class MaintenanceType {
    REPAIR,              // Ремонт (поломка)
    SCHEDULED_SERVICE,   // Плановое ТО
    MODIFICATION;        // Модификация/Тюнинг
    
    fun getDisplayName(): String = when (this) {
        REPAIR -> "Ремонт"
        SCHEDULED_SERVICE -> "ТО"
        MODIFICATION -> "Тюнинг"
    }
    
    companion object {
        fun fromString(value: String?): MaintenanceType? {
            return value?.let { 
                try {
                    valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}
