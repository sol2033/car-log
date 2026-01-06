package com.carlog.domain.model

import java.time.LocalDate
import java.time.YearMonth

sealed class TimePeriod {
    abstract fun getStartDate(): LocalDate
    abstract fun getDisplayName(): String
    
    data object Week : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusWeeks(1)
        override fun getDisplayName(): String = "Неделя"
    }
    
    data object TwoWeeks : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusWeeks(2)
        override fun getDisplayName(): String = "2 недели"
    }
    
    data object Month : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusMonths(1)
        override fun getDisplayName(): String = "Месяц"
    }
    
    data object ThreeMonths : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusMonths(3)
        override fun getDisplayName(): String = "3 месяца"
    }
    
    data object SixMonths : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusMonths(6)
        override fun getDisplayName(): String = "6 месяцев"
    }
    
    data object Year : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.now().minusYears(1)
        override fun getDisplayName(): String = "Год"
    }
    
    data class SpecificMonth(val yearMonth: YearMonth) : TimePeriod() {
        override fun getStartDate(): LocalDate = yearMonth.atDay(1)
        override fun getDisplayName(): String = "${yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${yearMonth.year}"
        
        fun getEndDate(): LocalDate = yearMonth.atEndOfMonth()
    }
    
    data object AllTime : TimePeriod() {
        override fun getStartDate(): LocalDate = LocalDate.of(2000, 1, 1) // Far in the past
        override fun getDisplayName(): String = "Всё время"
    }
    
    companion object {
        fun getDefaultPeriods(): List<TimePeriod> = listOf(
            Week, TwoWeeks, Month, ThreeMonths, SixMonths, Year, AllTime
        )
    }
}
