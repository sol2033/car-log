package com.carlog.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Период времени для статистики
 */
enum class StatisticsPeriod {
    WEEK,           // Последняя неделя
    TWO_WEEKS,      // Последние 2 недели
    MONTH,          // Последний месяц
    THREE_MONTHS,   // Последние 3 месяца
    SIX_MONTHS,     // Последние 6 месяцев
    YEAR,           // Последний год
    ALL_TIME;       // За всё время
    
    fun getDisplayName(): String = when(this) {
        WEEK -> "Неделя"
        TWO_WEEKS -> "2 недели"
        MONTH -> "Месяц"
        THREE_MONTHS -> "3 месяца"
        SIX_MONTHS -> "6 месяцев"
        YEAR -> "Год"
        ALL_TIME -> "Всё время"
    }
}

/**
 * Конкретный месяц для статистики
 */
data class SpecificMonthPeriod(
    val yearMonth: YearMonth
) {
    fun getDisplayName(): String {
        val monthName = when(yearMonth.monthValue) {
            1 -> "Январь"
            2 -> "Февраль"
            3 -> "Март"
            4 -> "Апрель"
            5 -> "Май"
            6 -> "Июнь"
            7 -> "Июль"
            8 -> "Август"
            9 -> "Сентябрь"
            10 -> "Октябрь"
            11 -> "Ноябрь"
            12 -> "Декабрь"
            else -> ""
        }
        return "$monthName ${yearMonth.year}"
    }
}


/**
 * Общая статистика по автомобилю
 */
data class GeneralStatistics(
    val totalCost: Double,                      // Общие расходы
    val costPerKm: Double,                      // Стоимость за км
    val averageKmPerDay: Double,                // Средний пробег в день
    val averageKmPerMonth: Double,              // Средний пробег в месяц
    val mostExpensiveMonth: String?,            // Самый дорогой месяц (формат "январь 2024")
    val costDistribution: List<CostDistributionItem>, // Распределение расходов по категориям
    val costTrend: List<CostTrendItem>          // Тренд расходов по времени
)

/**
 * Элемент распределения расходов (для круговой диаграммы)
 */
data class CostDistributionItem(
    val category: String,       // Категория (Топливо, Ремонты, Расходы, Расходники, ДТП)
    val amount: Double,         // Сумма
    val percentage: Float       // Процент от общей суммы
)

/**
 * Элемент тренда расходов (для линейного графика)
 */
data class CostTrendItem(
    val date: LocalDate,        // Дата
    val amount: Double,         // Сумма расходов
    val label: String           // Метка для отображения на графике
)

/**
 * Статистика по топливу
 */
data class FuelStatistics(
    val fuelTypes: List<FuelTypeStatistics>,   // Статистика по каждому типу топлива
    val totalFuelCost: Double,                  // Общая стоимость топлива (всех типов)
    val monthlyFuelCosts: List<MonthlyCostItem> // Расходы на топливо по месяцам (всех типов)
)

/**
 * Статистика по конкретному типу топлива
 */
data class FuelTypeStatistics(
    val fuelType: String,                       // Тип топлива (Бензин, Газ, Дизель, Электро)
    val averageConsumption: Double,             // Средний расход (л/100км)
    val totalCost: Double,                      // Стоимость этого типа топлива
    val totalLiters: Double,                    // Общее количество литров/кВт·ч
    val consumptionTrend: List<ConsumptionTrendItem>, // Тренд расхода для этого типа (л/100км)
    val monthlyLiters: List<MonthlyCostItem>    // Расход в литрах по месяцам
)

/**
 * Элемент тренда расхода топлива (для линейного графика)
 */
data class ConsumptionTrendItem(
    val date: LocalDate,        // Дата заправки
    val consumption: Double     // Расход (л/100км)
)

/**
 * Элемент месячных расходов (для столбчатой диаграммы)
 */
data class MonthlyCostItem(
    val month: String,          // Месяц (формат "янв 2024")
    val amount: Double          // Сумма
)

/**
 * Статистика по ремонтам
 */
data class RepairsStatistics(
    val totalRepairsCost: Double,               // Общая стоимость ремонтов
    val repairsCount: Int,                      // Количество ремонтов
    val averageRepairCost: Double,              // Средняя стоимость ремонта
    val monthlyRepairCosts: List<MonthlyCostItem>, // Расходы на ремонты по месяцам
    val partsVsLaborDistribution: List<CostDistributionItem> // Распределение: запчасти vs работа
)

/**
 * Статистика по прочим расходам
 */
data class ExpensesStatistics(
    val totalExpensesCost: Double,              // Общая стоимость расходов
    val categoryDistribution: List<CostDistributionItem>, // Распределение по категориям
    val monthlyExpenses: List<MonthlyCostItem>, // Расходы по месяцам
    val topCategories: List<CategoryTotalItem>  // Топ категорий по сумме
)

/**
 * Элемент суммы по категории
 */
data class CategoryTotalItem(
    val category: String,       // Название категории
    val amount: Double          // Общая сумма
)

/**
 * Статистика по расходникам
 */
data class ConsumablesStatistics(
    val averageMaintenanceCost: Double,         // Средняя стоимость ТО (только запчасти)
    val averageMaintenanceCostWithService: Double, // Средняя стоимость ТО (запчасти + сервис)
    val totalConsumablesCost: Double,           // Общая стоимость расходников
    val categorySpending: List<MonthlyCostItem>, // Расходы по категориям (используем MonthlyCostItem, где month = название категории)
    val categoryDistribution: List<CostDistributionItem>, // Распределение по категориям
    val averagePerCategory: List<CategoryAverageItem> // Средняя стоимость по категориям
)

/**
 * Элемент средней стоимости по категории
 */
data class CategoryAverageItem(
    val category: String,       // Название категории
    val average: Double,        // Средняя стоимость
    val count: Int              // Количество замен
)

/**
 * Общий контейнер для всей статистики
 */
data class CarStatistics(
    val general: GeneralStatistics?,
    val fuel: FuelStatistics?,
    val repairs: RepairsStatistics?,
    val expenses: ExpensesStatistics?,
    val consumables: ConsumablesStatistics?,
    val isEmpty: Boolean = general == null && fuel == null && repairs == null && expenses == null && consumables == null
)
