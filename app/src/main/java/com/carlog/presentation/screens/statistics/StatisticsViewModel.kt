package com.carlog.presentation.screens.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.local.dao.AccidentDao
import com.carlog.data.local.dao.BreakdownDao
import com.carlog.data.local.dao.ConsumableDao
import com.carlog.data.local.dao.CarDao
import com.carlog.data.local.dao.ExpenseDao
import com.carlog.data.local.dao.PartDao
import com.carlog.data.local.dao.RefuelingDao
import com.carlog.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject

data class StatisticsUiState(
    val statistics: CarStatistics = CarStatistics(null, null, null, null, null, true),
    val selectedPeriod: StatisticsPeriod = StatisticsPeriod.ALL_TIME,
    val specificMonth: YearMonth? = null, // Конкретный месяц для фильтрации
    val excludeAccidents: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val accidentDao: AccidentDao,
    private val breakdownDao: BreakdownDao,
    private val consumableDao: ConsumableDao,
    private val carDao: CarDao,
    private val partDao: PartDao,
    private val refuelingDao: RefuelingDao,
    private val expenseDao: ExpenseDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L
    
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    
    init {
        loadStatistics()
    }
    
    fun setPeriod(period: StatisticsPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period, specificMonth = null)
        loadStatistics()
    }
    
    fun setSpecificMonth(yearMonth: YearMonth) {
        _uiState.value = _uiState.value.copy(specificMonth = yearMonth)
        loadStatistics()
    }
    
    fun toggleExcludeAccidents() {
        _uiState.value = _uiState.value.copy(excludeAccidents = !_uiState.value.excludeAccidents)
        loadStatistics()
    }
    
    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val period = _uiState.value.selectedPeriod
                val specificMonth = _uiState.value.specificMonth
                val excludeAccidents = _uiState.value.excludeAccidents
                val (startDate, endDate) = if (specificMonth != null) {
                    getSpecificMonthDates(specificMonth)
                } else {
                    getPeriodDates(period)
                }
                
                // Load all data
                val car = carDao.getCarById(carId).firstOrNull()
                val accidents = accidentDao.getAccidentsByPeriod(carId, startDate, endDate).firstOrNull() ?: emptyList()
                val breakdowns = breakdownDao.getBreakdownsByCarId(carId).firstOrNull() ?: emptyList()
                val consumables = consumableDao.getConsumablesByCarId(carId).firstOrNull() ?: emptyList()
                val parts = partDao.getPartsByCarId(carId).firstOrNull() ?: emptyList()
                val refuelings = refuelingDao.getRefuelingsByCarId(carId).firstOrNull() ?: emptyList()
                val expenses = expenseDao.getExpensesByCarId(carId).firstOrNull() ?: emptyList()
                
                // Debug logging
                android.util.Log.d("StatisticsViewModel", "Loaded ${expenses.size} expenses for carId=$carId")
                expenses.forEach { expense ->
                    android.util.Log.d("StatisticsViewModel", "Expense: id=${expense.id}, category=${expense.category}, cost=${expense.cost}, date=${expense.date}")
                }
                
                // Filter by period
                val filteredBreakdowns = filterByPeriod(breakdowns, startDate, endDate)
                val filteredConsumables = filterByPeriod(consumables, startDate, endDate)
                val filteredExpenses = filterByPeriod(expenses, startDate, endDate)
                
                android.util.Log.d("StatisticsViewModel", "Filtered ${filteredExpenses.size} expenses after period filter")
                
                // Get part IDs that are already counted in breakdowns to avoid double counting
                val partIdsInBreakdowns = breakdowns.flatMap { it.installedPartIds ?: emptyList() }.toSet()
                
                // Filter parts: only those not already counted in breakdowns
                val filteredParts = filterByPeriod(parts, startDate, endDate)
                    .filter { !partIdsInBreakdowns.contains(it.id) }
                
                val filteredAccidents = if (excludeAccidents) emptyList() else accidents
                
                // Filter refuelings by period
                val filteredRefuelings = filterByPeriod(refuelings, startDate, endDate)
                
                // Pre-calculate all periods once for reuse (optimization)
                val groupingType = getGroupingType(period, specificMonth)
                val allPeriods = generateAllPeriods(startDate, endDate, groupingType, specificMonth)
                
                // Calculate statistics
                val fuelStats = calculateFuelStatistics(filteredRefuelings, allPeriods, groupingType)
                val generalStats = calculateGeneralStatistics(
                    car, filteredBreakdowns, filteredConsumables, filteredParts, filteredAccidents, filteredRefuelings, filteredExpenses, period, allPeriods, groupingType
                )
                val repairsStats = calculateRepairsStatistics(filteredBreakdowns, filteredParts, filteredAccidents, allPeriods, groupingType)
                val consumablesStats = calculateConsumablesStatistics(filteredConsumables)
                val expensesStats = calculateExpensesStatistics(filteredExpenses, allPeriods, groupingType)
                
                android.util.Log.d("StatisticsViewModel", "ExpensesStats: ${if (expensesStats == null) "NULL" else "total=${expensesStats.totalExpensesCost}, categories=${expensesStats.categoryDistribution.size}"}")
                
                val statistics = CarStatistics(
                    general = generalStats,
                    fuel = fuelStats,
                    repairs = repairsStats,
                    expenses = expensesStats,
                    consumables = consumablesStats
                )
                
                _uiState.value = _uiState.value.copy(
                    statistics = statistics,
                    isLoading = false,
                    error = null
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка при загрузке статистики"
                )
            }
        }
    }
    
    private fun getPeriodDates(period: StatisticsPeriod): Pair<Long, Long> {
        val now = LocalDate.now()
        
        val (startDate, endDate) = when (period) {
            StatisticsPeriod.WEEK -> {
                // Текущая неделя: понедельник - воскресенье
                val monday = now.minusDays(now.dayOfWeek.value.toLong() - 1)
                val sunday = monday.plusDays(6)
                monday to sunday
            }
            StatisticsPeriod.TWO_WEEKS -> {
                // 2 недели: предыдущая неделя + текущая неделя (до сегодня)
                val currentMonday = now.minusDays(now.dayOfWeek.value.toLong() - 1)
                val previousMonday = currentMonday.minusWeeks(1)
                previousMonday to now
            }
            StatisticsPeriod.MONTH -> {
                now.minusMonths(1) to now
            }
            StatisticsPeriod.THREE_MONTHS -> {
                now.minusMonths(3) to now
            }
            StatisticsPeriod.SIX_MONTHS -> {
                now.minusMonths(6) to now
            }
            StatisticsPeriod.YEAR -> {
                // Текущий год: с 1 января текущего года по сегодня
                val yearStart = LocalDate.of(now.year, 1, 1)
                yearStart to now
            }
            StatisticsPeriod.ALL_TIME -> {
                LocalDate.of(2000, 1, 1) to now
            }
        }
        
        return Pair(
            startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    
    private fun getSpecificMonthDates(yearMonth: YearMonth): Pair<Long, Long> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        
        return Pair(
            startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    
    private enum class GroupingType {
        BY_DAY,              // Для недели (7 дней)
        BY_WEEK_TWO_WEEKS,   // Для 2 недель (2 точки)
        BY_WEEK_MONTH,       // Для месяца (4 недели)
        BY_MONTH,            // Для 3 месяцев (3 месяца)
        BY_TWO_MONTHS,       // Для 6 месяцев (3 точки по 2 месяца)
        BY_FOUR_MONTHS       // Для года (3 точки по 4 месяца)
    }
    
    private fun getGroupingType(period: StatisticsPeriod, specificMonth: YearMonth?): GroupingType {
        return when {
            specificMonth != null -> GroupingType.BY_DAY
            period == StatisticsPeriod.WEEK -> GroupingType.BY_DAY
            period == StatisticsPeriod.TWO_WEEKS -> GroupingType.BY_WEEK_TWO_WEEKS
            period == StatisticsPeriod.MONTH -> GroupingType.BY_WEEK_MONTH
            period == StatisticsPeriod.THREE_MONTHS -> GroupingType.BY_MONTH
            period == StatisticsPeriod.SIX_MONTHS -> GroupingType.BY_TWO_MONTHS
            period == StatisticsPeriod.YEAR -> GroupingType.BY_FOUR_MONTHS
            else -> GroupingType.BY_MONTH // ALL_TIME
        }
    }
    
    private fun formatDateLabel(date: LocalDate, groupingType: GroupingType, index: Int = 0): String {
        return when (groupingType) {
            GroupingType.BY_DAY -> {
                // Только число дня
                date.dayOfMonth.toString()
            }
            GroupingType.BY_WEEK_TWO_WEEKS -> {
                // Просто "неделя 1", "неделя 2"
                "неделя ${index + 1}"
            }
            GroupingType.BY_WEEK_MONTH -> {
                // Для месяца: "неделя 1", "неделя 2", "неделя 3", "неделя 4"
                "неделя ${index + 1}"
            }
            GroupingType.BY_MONTH -> {
                // Названия месяцев: дек, янв, фев
                date.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
            }
            GroupingType.BY_TWO_MONTHS -> {
                // Названия месяцев для 6 месяцев (по 2 месяца)
                val month1 = date.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
                val month2 = date.plusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale("ru"))
                "$month1-$month2"
            }
            GroupingType.BY_FOUR_MONTHS -> {
                // Названия месяцев для года (по 4 месяца)
                val month1 = date.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
                val month4 = date.plusMonths(3).month.getDisplayName(TextStyle.SHORT, Locale("ru"))
                "$month1-$month4"
            }
        }
    }
    
    // Генерация всех периодов в диапазоне (включая пустые)
    private fun generateAllPeriods(startDate: Long, endDate: Long, groupingType: GroupingType, specificMonth: YearMonth?): List<LocalDate> {
        val start = LocalDate.ofEpochDay(startDate / (24 * 60 * 60 * 1000))
        val end = LocalDate.ofEpochDay(endDate / (24 * 60 * 60 * 1000))
        val now = LocalDate.now()
        
        val periods = mutableListOf<LocalDate>()
        
        when (groupingType) {
            GroupingType.BY_DAY -> {
                // Генерируем все дни в диапазоне
                var current = start
                while (current.isBefore(end) || current.isEqual(end)) {
                    periods.add(current)
                    current = current.plusDays(1)
                }
            }
            GroupingType.BY_WEEK_TWO_WEEKS -> {
                // 2 недели: 2 точки (неделя 1, неделя 2)
                val week1Start = start.minusDays(start.dayOfWeek.value.toLong() - 1)
                periods.add(week1Start)
                periods.add(week1Start.plusWeeks(1))
            }
            GroupingType.BY_WEEK_MONTH -> {
                // Месяц: 4 недели
                var current = start.minusDays(start.dayOfWeek.value.toLong() - 1)
                repeat(4) {
                    periods.add(current)
                    current = current.plusWeeks(1)
                }
            }
            GroupingType.BY_MONTH -> {
                // 3 месяца: 3 точки (по 1 месяцу)
                // Последний месяц - текущий
                val currentMonth = now.withDayOfMonth(1)
                periods.add(currentMonth.minusMonths(2))
                periods.add(currentMonth.minusMonths(1))
                periods.add(currentMonth)
            }
            GroupingType.BY_TWO_MONTHS -> {
                // 6 месяцев: 3 точки (по 2 месяца)
                // Последний месяц - текущий
                val currentMonth = now.withDayOfMonth(1)
                periods.add(currentMonth.minusMonths(5)) // месяцы 1-2
                periods.add(currentMonth.minusMonths(3)) // месяцы 3-4
                periods.add(currentMonth.minusMonths(1)) // месяцы 5-6
            }
            GroupingType.BY_FOUR_MONTHS -> {
                // Год: 3 точки (по 4 месяца)
                val currentMonth = now.withDayOfMonth(1)
                periods.add(currentMonth.minusMonths(11)) // месяцы 1-4
                periods.add(currentMonth.minusMonths(7))  // месяцы 5-8
                periods.add(currentMonth.minusMonths(3))  // месяцы 9-12
            }
        }
        
        return periods
    }
    
    // Заполнение пустых периодов нулями
    private fun fillEmptyPeriods(
        data: Map<LocalDate, Double>,
        allPeriods: List<LocalDate>,
        groupingType: GroupingType
    ): List<MonthlyCostItem> {
        return allPeriods.mapIndexed { index, period ->
            MonthlyCostItem(
                month = formatDateLabel(period, groupingType, index),
                amount = data[period] ?: 0.0
            )
        }
    }
    
    private fun <T> filterByPeriod(items: List<T>, startDate: Long, endDate: Long): List<T> where T : Any {
        return items.filter {
            val date = when (it) {
                is Breakdown -> it.breakdownDate
                is Consumable -> it.installationDate
                is Part -> it.installDate
                is Refueling -> it.date
                is Expense -> it.date
                else -> return@filter false
            }
            date in startDate..endDate
        }
    }
    
    private fun calculateGeneralStatistics(
        car: Car?,
        breakdowns: List<Breakdown>,
        consumables: List<Consumable>,
        parts: List<Part>,
        accidents: List<Accident>,
        refuelings: List<Refueling>,
        expenses: List<Expense>,
        period: StatisticsPeriod,
        allPeriods: List<LocalDate>,
        groupingType: GroupingType
    ): GeneralStatistics? {
        if (car == null) return null
        
        // Calculate total costs
        // Services = breakdown service costs + part installation service costs
        val servicesCost = breakdowns.sumOf { it.serviceCost ?: 0.0 } + 
                          parts.sumOf { it.servicePrice ?: 0.0 }
        
        // Parts = breakdown parts costs + standalone parts prices (without service)
        val partsCost = breakdowns.sumOf { it.partsCost } + 
                       parts.sumOf { it.price }
        
        val consumablesCost = consumables.sumOf { it.cost ?: 0.0 }
        val accidentsCost = accidents.sumOf { it.repairCost ?: 0.0 }
        val fuelCost = refuelings.sumOf { it.totalCost ?: 0.0 }
        val expensesCost = expenses.sumOf { it.cost }
        val totalCost = servicesCost + partsCost + consumablesCost + accidentsCost + fuelCost + expensesCost
        
        android.util.Log.d("StatisticsViewModel", "GeneralStats calculation: expensesCost=$expensesCost (from ${expenses.size} expenses), totalCost=$totalCost")
        
        if (totalCost == 0.0 && breakdowns.isEmpty() && consumables.isEmpty() && parts.isEmpty() && accidents.isEmpty() && refuelings.isEmpty() && expenses.isEmpty()) {
            return null
        }
        
        // Calculate cost per km (always excluding accidents)
        val costForKmCalculation = servicesCost + partsCost + consumablesCost + fuelCost + expensesCost
        val costPerKm = if (car.currentMileage > 0) {
            costForKmCalculation / car.currentMileage
        } else 0.0
        
        // Calculate average km per day and month
        val carAgeInDays = if (car.purchaseDate != null) {
            ChronoUnit.DAYS.between(
                LocalDate.ofEpochDay(car.purchaseDate / (24 * 60 * 60 * 1000)),
                LocalDate.now()
            ).toDouble()
        } else 1.0
        
        val averageKmPerDay = if (carAgeInDays > 0) car.currentMileage / carAgeInDays else 0.0
        val averageKmPerMonth = averageKmPerDay * 30.0
        
        // Find most expensive month
        val mostExpensiveMonth = findMostExpensiveMonth(breakdowns, consumables, parts, accidents, refuelings, expenses)
        
        // Cost distribution
        val costDistribution = buildList {
            if (servicesCost > 0) {
                add(CostDistributionItem(
                    category = "Услуги сервисов",
                    amount = servicesCost,
                    percentage = (servicesCost / totalCost * 100).toFloat()
                ))
            }
            if (partsCost > 0) {
                add(CostDistributionItem(
                    category = "Запчасти",
                    amount = partsCost,
                    percentage = (partsCost / totalCost * 100).toFloat()
                ))
            }
            if (consumablesCost > 0) {
                add(CostDistributionItem(
                    category = "Расходники",
                    amount = consumablesCost,
                    percentage = (consumablesCost / totalCost * 100).toFloat()
                ))
            }
            if (fuelCost > 0) {
                add(CostDistributionItem(
                    category = "Топливо",
                    amount = fuelCost,
                    percentage = (fuelCost / totalCost * 100).toFloat()
                ))
            }
            if (expensesCost > 0) {
                add(CostDistributionItem(
                    category = "Прочие расходы",
                    amount = expensesCost,
                    percentage = (expensesCost / totalCost * 100).toFloat()
                ))
            }
            if (accidentsCost > 0) {
                add(CostDistributionItem(
                    category = "ДТП",
                    amount = accidentsCost,
                    percentage = (accidentsCost / totalCost * 100).toFloat()
                ))
            }
        }
        
        // Cost trend (по месяцам)
        val costTrend = calculateCostTrend(breakdowns, consumables, parts, accidents, refuelings, expenses, period, allPeriods, groupingType)
        
        return GeneralStatistics(
            totalCost = totalCost,
            costPerKm = costPerKm,
            averageKmPerDay = averageKmPerDay,
            averageKmPerMonth = averageKmPerMonth,
            mostExpensiveMonth = mostExpensiveMonth,
            costDistribution = costDistribution,
            costTrend = costTrend
        )
    }
    
    // Вспомогательная функция для получения ключа группировки
    private fun getGroupKey(date: LocalDate, groupingType: GroupingType, allPeriods: List<LocalDate>): LocalDate {
        return when (groupingType) {
            GroupingType.BY_DAY -> date
            GroupingType.BY_WEEK_TWO_WEEKS -> {
                // Находим ближайший период (начало недели)
                val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)
                allPeriods.minByOrNull { Math.abs(ChronoUnit.DAYS.between(it, weekStart)) } ?: weekStart
            }
            GroupingType.BY_WEEK_MONTH -> {
                // Находим начало недели
                val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)
                allPeriods.minByOrNull { Math.abs(ChronoUnit.DAYS.between(it, weekStart)) } ?: weekStart
            }
            GroupingType.BY_MONTH -> {
                // Первое число месяца
                date.withDayOfMonth(1)
            }
            GroupingType.BY_TWO_MONTHS -> {
                // Находим ближайший период (начало 2-месячного блока)
                val monthStart = date.withDayOfMonth(1)
                allPeriods.minByOrNull { Math.abs(ChronoUnit.MONTHS.between(it, monthStart)) } ?: monthStart
            }
            GroupingType.BY_FOUR_MONTHS -> {
                // Находим ближайший период (начало 4-месячного блока)
                val monthStart = date.withDayOfMonth(1)
                allPeriods.minByOrNull { Math.abs(ChronoUnit.MONTHS.between(it, monthStart)) } ?: monthStart
            }
        }
    }
    
    private fun findMostExpensiveMonth(
        breakdowns: List<Breakdown>,
        consumables: List<Consumable>,
        parts: List<Part>,
        accidents: List<Accident>,
        refuelings: List<Refueling>,
        expenses: List<Expense>
    ): String? {
        // Group all costs by month
        val monthlyCosts = mutableMapOf<String, Double>()
        
        breakdowns.forEach { breakdown ->
            val date = LocalDate.ofEpochDay(breakdown.breakdownDate / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            // Use totalCost which includes both parts and service
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + breakdown.totalCost
        }
        
        consumables.forEach { consumable ->
            val date = LocalDate.ofEpochDay(consumable.installationDate / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + (consumable.cost ?: 0.0)
        }
        
        parts.forEach { part ->
            val date = LocalDate.ofEpochDay(part.installDate / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            // Count standalone parts (part price + service price for installation)
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + part.price + (part.servicePrice ?: 0.0)
        }
        
        accidents.forEach { accident ->
            val date = LocalDate.ofEpochDay(accident.date / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + (accident.repairCost ?: 0.0)
        }
        
        refuelings.forEach { refueling ->
            val date = LocalDate.ofEpochDay(refueling.date / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + (refueling.totalCost ?: 0.0)
        }
        
        expenses.forEach { expense ->
            val date = LocalDate.ofEpochDay(expense.date / (24 * 60 * 60 * 1000))
            val monthKey = "${date.year}-${date.monthValue}"
            monthlyCosts[monthKey] = (monthlyCosts[monthKey] ?: 0.0) + expense.cost
        }
        
        val maxEntry = monthlyCosts.maxByOrNull { it.value } ?: return null
        val dateParts = maxEntry.key.split("-")
        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt()
        val monthName = LocalDate.of(year, month, 1).month
            .getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        
        return "$monthName $year"
    }
    
    private fun calculateCostTrend(
        breakdowns: List<Breakdown>,
        consumables: List<Consumable>,
        parts: List<Part>,
        accidents: List<Accident>,
        refuelings: List<Refueling>,        expenses: List<Expense>,        @Suppress("UNUSED_PARAMETER") period: StatisticsPeriod,
        allPeriods: List<LocalDate>,
        groupingType: GroupingType
    ): List<CostTrendItem> {
        val periodCosts = mutableMapOf<LocalDate, Double>()
        
        // Aggregate costs by period
        breakdowns.forEach { breakdown ->
            val date = LocalDate.ofEpochDay(breakdown.breakdownDate / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + breakdown.totalCost
        }
        
        consumables.forEach { consumable ->
            val date = LocalDate.ofEpochDay(consumable.installationDate / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + (consumable.cost ?: 0.0)
        }
        
        parts.forEach { part ->
            val date = LocalDate.ofEpochDay(part.installDate / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + part.price + (part.servicePrice ?: 0.0)
        }
        
        accidents.forEach { accident ->
            val date = LocalDate.ofEpochDay(accident.date / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + (accident.repairCost ?: 0.0)
        }
        
        refuelings.forEach { refueling ->
            val date = LocalDate.ofEpochDay(refueling.date / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + (refueling.totalCost ?: 0.0)
        }
        
        expenses.forEach { expense ->
            val date = LocalDate.ofEpochDay(expense.date / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodCosts[groupKey] = (periodCosts[groupKey] ?: 0.0) + expense.cost
        }
        
        // Заполняем пустые периоды
        val filledData = fillEmptyPeriods(periodCosts, allPeriods, groupingType)
        
        return filledData.mapIndexed { index, item ->
            CostTrendItem(
                date = allPeriods[index],
                amount = item.amount,
                label = item.month
            )
        }
    }
    
    private fun calculateRepairsStatistics(
        breakdowns: List<Breakdown>,
        parts: List<Part>,
        accidents: List<Accident>,
        allPeriods: List<LocalDate>,
        groupingType: GroupingType
    ): RepairsStatistics? {
        if (breakdowns.isEmpty() && parts.isEmpty() && accidents.isEmpty()) return null
        
        // Calculate totals (avoid duplication: breakdowns already include parts costs)
        val breakdownsCost = breakdowns.sumOf { it.totalCost }
        // Standalone parts only (not included in breakdowns)
        val partsCost = parts.sumOf { it.price + (it.servicePrice ?: 0.0) }
        val accidentsCost = accidents.sumOf { it.repairCost ?: 0.0 }
        val totalRepairsCost = breakdownsCost + partsCost + accidentsCost
        val repairsCount = breakdowns.size + parts.size + accidents.size
        val averageRepairCost = if (repairsCount > 0) totalRepairsCost / repairsCount else 0.0
        
        // Period-based repair costs
        val periodRepairCosts = mutableMapOf<LocalDate, Double>()
        
        breakdowns.forEach { breakdown ->
            val date = LocalDate.ofEpochDay(breakdown.breakdownDate / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodRepairCosts[groupKey] = (periodRepairCosts[groupKey] ?: 0.0) + breakdown.totalCost
        }
        
        parts.forEach { part ->
            val date = LocalDate.ofEpochDay(part.installDate / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodRepairCosts[groupKey] = (periodRepairCosts[groupKey] ?: 0.0) + part.price + (part.servicePrice ?: 0.0)
        }
        
        accidents.forEach { accident ->
            val date = LocalDate.ofEpochDay(accident.date / (24 * 60 * 60 * 1000))
            val groupKey = getGroupKey(date, groupingType, allPeriods)
            periodRepairCosts[groupKey] = (periodRepairCosts[groupKey] ?: 0.0) + (accident.repairCost ?: 0.0)
        }
        
        // Заполняем пустые периоды
        val monthlyCostItems = fillEmptyPeriods(periodRepairCosts, allPeriods, groupingType)
        
        // Parts vs Labor distribution
        val totalPartsCost = breakdowns.sumOf { breakdown ->
            breakdown.partsCost
        } + parts.sumOf { it.price }
        val totalLaborCost = breakdowns.sumOf { it.serviceCost ?: 0.0 } + parts.sumOf { it.servicePrice ?: 0.0 }
        val total = totalPartsCost + totalLaborCost
        
        val partsVsLabor = buildList {
            if (totalPartsCost > 0) {
                add(CostDistributionItem(
                    category = "Запчасти",
                    amount = totalPartsCost,
                    percentage = (totalPartsCost / total * 100).toFloat()
                ))
            }
            if (totalLaborCost > 0) {
                add(CostDistributionItem(
                    category = "Работа",
                    amount = totalLaborCost,
                    percentage = (totalLaborCost / total * 100).toFloat()
                ))
            }
        }
        
        return RepairsStatistics(
            totalRepairsCost = totalRepairsCost,
            repairsCount = repairsCount,
            averageRepairCost = averageRepairCost,
            monthlyRepairCosts = monthlyCostItems,
            partsVsLaborDistribution = partsVsLabor
        )
    }
    
    private fun calculateConsumablesStatistics(consumables: List<Consumable>): ConsumablesStatistics? {
        if (consumables.isEmpty()) return null
        
        val totalCost = consumables.sumOf { it.cost ?: 0.0 }
        
        // Calculate average maintenance cost (for standard consumables only)
        val standardConsumables = consumables.filter { consumable ->
            ConsumableCategories.STANDARD_CATEGORIES.contains(consumable.category) 
        }
        
        // Средняя стоимость ТО без учета работ сервисов (только запчасти)
        val averageMaintenanceCost = if (standardConsumables.isNotEmpty()) {
            standardConsumables.sumOf { it.cost ?: 0.0 } / standardConsumables.size
        } else 0.0
        
        // Средняя стоимость ТО с учетом работ сервисов (запчасти + сервис)
        val averageMaintenanceCostWithService = if (standardConsumables.isNotEmpty()) {
            standardConsumables.sumOf { (it.cost ?: 0.0) + (it.serviceCost ?: 0.0) } / standardConsumables.size
        } else 0.0
        
        // Category spending
        val categorySpending = consumables
            .groupBy { it.category }
            .map { (category, items) ->
                MonthlyCostItem(
                    month = category, // Using month field for category name
                    amount = items.sumOf { it.cost ?: 0.0 }
                )
            }
            .sortedByDescending { it.amount }
        
        // Category distribution
        val categoryDistribution = consumables
            .groupBy { it.category }
            .map { (category, items) ->
                val amount = items.sumOf { it.cost ?: 0.0 }
                CostDistributionItem(
                    category = category,
                    amount = amount,
                    percentage = (amount / totalCost * 100).toFloat()
                )
            }
            .sortedByDescending { it.amount }
        
        // Average per category
        val averagePerCategory = consumables
            .groupBy { it.category }
            .map { (category, items) ->
                CategoryAverageItem(
                    category = category,
                    average = items.sumOf { it.cost ?: 0.0 } / items.size,
                    count = items.size
                )
            }
            .sortedBy { it.category }
        
        return ConsumablesStatistics(
            averageMaintenanceCost = averageMaintenanceCost,
            averageMaintenanceCostWithService = averageMaintenanceCostWithService,
            totalConsumablesCost = totalCost,
            categorySpending = categorySpending,
            categoryDistribution = categoryDistribution,
            averagePerCategory = averagePerCategory
        )
    }
    
    private fun calculateFuelStatistics(refuelings: List<Refueling>, allPeriods: List<LocalDate>, groupingType: GroupingType): FuelStatistics? {
        if (refuelings.isEmpty()) return null
        
        // Normalize fuel types: group all petrol types as "Бензин", gas types as "Газ"
        fun normalizeFuelType(fuelType: String): String {
            return when {
                fuelType.contains("АИ", ignoreCase = true) || fuelType == "Бензин" -> "Бензин"
                fuelType.contains("Метан", ignoreCase = true) || fuelType.contains("Пропан", ignoreCase = true) || fuelType.contains("CNG", ignoreCase = true) -> "Газ"
                fuelType.contains("Дизель", ignoreCase = true) -> "Дизель"
                fuelType.contains("Электр", ignoreCase = true) -> "Электро"
                else -> fuelType
            }
        }
        
        // Group refuelings by normalized fuel type
        val refuelingsByType = refuelings.groupBy { normalizeFuelType(it.fuelType) }
        
        // Calculate statistics for each fuel type
        val fuelTypeStatistics = refuelingsByType.map { (fuelType, typeRefuelings) ->
            // Calculate average consumption for this fuel type based on ALL refuelings
            val averageConsumption = if (typeRefuelings.size >= 2) {
                // Sort by mileage to get the range
                val sortedByMileage = typeRefuelings.sortedBy { it.mileage }
                val minMileage = sortedByMileage.first().mileage
                val maxMileage = sortedByMileage.last().mileage
                val totalDistance = maxMileage - minMileage
                
                if (totalDistance > 0) {
                    val totalLitersForConsumption = typeRefuelings.sumOf { it.liters }
                    (totalLitersForConsumption / totalDistance) * 100
                } else 0.0
            } else 0.0
            
            // Calculate total cost for this fuel type
            val totalCost = typeRefuelings.sumOf { it.totalCost ?: 0.0 }
            
            // Calculate total liters for this fuel type
            val totalLiters = typeRefuelings.sumOf { it.liters }
            
            // Consumption trend for this fuel type (л/100км) - based on full tank refuelings
            val refuelingsWithConsumption = typeRefuelings.filter { it.fuelConsumption != null }
            val consumptionTrend = refuelingsWithConsumption
                .sortedBy { it.date }
                .map { refueling ->
                    ConsumptionTrendItem(
                        date = LocalDate.ofEpochDay(refueling.date / (24 * 60 * 60 * 1000)),
                        consumption = refueling.fuelConsumption ?: 0.0
                    )
                }
            
            // Period-based liters consumption for this fuel type
            val periodLiters = typeRefuelings
                .groupBy { refueling ->
                    val date = LocalDate.ofEpochDay(refueling.date / (24 * 60 * 60 * 1000))
                    getGroupKey(date, groupingType, allPeriods)
                }
                .mapValues { (_, refuelingsInGroup) ->
                    refuelingsInGroup.sumOf { it.liters }
                }
            
            val monthlyLiters = fillEmptyPeriods(periodLiters, allPeriods, groupingType)
            
            FuelTypeStatistics(
                fuelType = fuelType,
                averageConsumption = averageConsumption,
                totalCost = totalCost,
                totalLiters = totalLiters,
                consumptionTrend = consumptionTrend,
                monthlyLiters = monthlyLiters
            )
        }.sortedByDescending { it.totalCost }
        
        // Calculate total fuel cost (all types)
        val totalFuelCost = refuelings.sumOf { it.totalCost ?: 0.0 }
        
        // Period-based fuel costs (all types combined)
        val periodFuelCosts = refuelings
            .groupBy { refueling ->
                val date = LocalDate.ofEpochDay(refueling.date / (24 * 60 * 60 * 1000))
                getGroupKey(date, groupingType, allPeriods)
            }
            .mapValues { (_, refuelingsInGroup) ->
                refuelingsInGroup.sumOf { it.totalCost ?: 0.0 }
            }
        
        val monthlyFuelCosts = fillEmptyPeriods(periodFuelCosts, allPeriods, groupingType)
        
        return FuelStatistics(
            fuelTypes = fuelTypeStatistics,
            totalFuelCost = totalFuelCost,
            monthlyFuelCosts = monthlyFuelCosts
        )
    }
    
    private fun calculateExpensesStatistics(expenses: List<Expense>, allPeriods: List<LocalDate>, groupingType: GroupingType): ExpensesStatistics? {
        if (expenses.isEmpty()) return null
        
        val totalCost = expenses.sumOf { it.cost }
        
        // Category distribution
        val categoryDistribution = expenses
            .groupBy { it.category }
            .map { (category, items) ->
                val amount = items.sumOf { it.cost }
                CostDistributionItem(
                    category = category,
                    amount = amount,
                    percentage = (amount / totalCost * 100).toFloat()
                )
            }
            .sortedByDescending { it.amount }
        
        // Monthly expenses
        val periodExpenses = expenses
            .groupBy { expense ->
                val date = LocalDate.ofEpochDay(expense.date / (24 * 60 * 60 * 1000))
                getGroupKey(date, groupingType, allPeriods)
            }
            .mapValues { (_, expensesInGroup) ->
                expensesInGroup.sumOf { it.cost }
            }
        
        val monthlyExpenses = fillEmptyPeriods(periodExpenses, allPeriods, groupingType)
        
        // Top categories
        val topCategories = expenses
            .groupBy { it.category }
            .map { (category, items) ->
                CategoryTotalItem(
                    category = category,
                    amount = items.sumOf { it.cost }
                )
            }
            .sortedByDescending { it.amount }
            .take(5)
        
        return ExpensesStatistics(
            totalExpensesCost = totalCost,
            categoryDistribution = categoryDistribution,
            monthlyExpenses = monthlyExpenses,
            topCategories = topCategories
        )
    }
}
