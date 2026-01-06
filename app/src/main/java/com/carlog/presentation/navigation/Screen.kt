package com.carlog.presentation.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    // Onboarding
    object LanguageSelection : Screen("language_selection")
    object Welcome : Screen("welcome")
    object AppInfo : Screen("app_info")
    
    // Main screens
    object CarList : Screen("car_list")
    object CarDetail : Screen("car_detail/{carId}") {
        fun createRoute(carId: Long) = "car_detail/$carId"
    }
    object AddCar : Screen("add_car")
    object EditCar : Screen("edit_car/{carId}") {
        fun createRoute(carId: Long) = "edit_car/$carId"
    }
    
    // Parts
    object Parts : Screen("parts/{carId}") {
        fun createRoute(carId: Long) = "parts/$carId"
    }
    object PartDetail : Screen("part_detail/{carId}/{partId}") {
        fun createRoute(carId: Long, partId: Long) = "part_detail/$carId/$partId"
    }
    object AddPart : Screen("add_part/{carId}") {
        fun createRoute(carId: Long) = "add_part/$carId"
    }
    object EditPart : Screen("edit_part/{carId}/{partId}") {
        fun createRoute(carId: Long, partId: Long) = "edit_part/$carId/$partId"
    }
    
    // Breakdowns
    object Breakdowns : Screen("breakdowns/{carId}") {
        fun createRoute(carId: Long) = "breakdowns/$carId"
    }
    object BreakdownDetail : Screen("breakdown_detail/{carId}/{breakdownId}") {
        fun createRoute(carId: Long, breakdownId: Long) = "breakdown_detail/$carId/$breakdownId"
    }
    object AddBreakdown : Screen("add_breakdown/{carId}") {
        fun createRoute(carId: Long) = "add_breakdown/$carId"
    }
    object EditBreakdown : Screen("edit_breakdown/{carId}/{breakdownId}") {
        fun createRoute(carId: Long, breakdownId: Long) = "edit_breakdown/$carId/$breakdownId"
    }
    
    // Accidents
    object Accidents : Screen("accidents/{carId}") {
        fun createRoute(carId: Long) = "accidents/$carId"
    }
    object AccidentDetail : Screen("accident_detail/{carId}/{accidentId}") {
        fun createRoute(carId: Long, accidentId: Long) = "accident_detail/$carId/$accidentId"
    }
    object AddAccident : Screen("add_accident/{carId}") {
        fun createRoute(carId: Long) = "add_accident/$carId"
    }
    object EditAccident : Screen("edit_accident/{carId}/{accidentId}") {
        fun createRoute(carId: Long, accidentId: Long) = "edit_accident/$carId/$accidentId"
    }
    
    // Consumables
    object Consumables : Screen("consumables/{carId}") {
        fun createRoute(carId: Long) = "consumables/$carId"
    }
    object ConsumableDetail : Screen("consumable_detail/{carId}/{consumableId}") {
        fun createRoute(carId: Long, consumableId: Long) = "consumable_detail/$carId/$consumableId"
    }
    object AddConsumable : Screen("add_consumable/{carId}?category={category}") {
        fun createRoute(carId: Long, category: String? = null) = 
            if (category != null) "add_consumable/$carId?category=$category"
            else "add_consumable/$carId"
    }
    object EditConsumable : Screen("edit_consumable/{carId}/{consumableId}") {
        fun createRoute(carId: Long, consumableId: Long) = "edit_consumable/$carId/$consumableId"
    }
    object ConsumablesHistory : Screen("consumables_history/{carId}") {
        fun createRoute(carId: Long) = "consumables_history/$carId"
    }
    object CategoryHistory : Screen("category_history/{carId}/{category}") {
        fun createRoute(carId: Long, category: String): String {
            val encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8.toString())
            return "category_history/$carId/$encodedCategory"
        }
    }
    object ConsumablesSettings : Screen("consumables_settings/{carId}") {
        fun createRoute(carId: Long) = "consumables_settings/$carId"
    }
    
    // Statistics
    object Statistics : Screen("statistics/{carId}") {
        fun createRoute(carId: Long) = "statistics/$carId"
    }
    
    // Refuelings
    object Refuelings : Screen("refuelings/{carId}") {
        fun createRoute(carId: Long) = "refuelings/$carId"
    }
    object AddRefueling : Screen("add_refueling/{carId}?refuelingId={refuelingId}") {
        fun createRoute(carId: Long, refuelingId: Long? = null) = 
            if (refuelingId != null) "add_refueling/$carId?refuelingId=$refuelingId"
            else "add_refueling/$carId"
    }
    
    // Expenses (прочие расходы)
    object Expenses : Screen("expenses/{carId}") {
        fun createRoute(carId: Long) = "expenses/$carId"
    }
    object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun createRoute(expenseId: Long) = "expense_detail/$expenseId"
    }
    object AddExpense : Screen("add_expense/{carId}?expenseId={expenseId}") {
        fun createRoute(carId: Long, expenseId: Long? = null) = 
            if (expenseId != null) "add_expense/$carId?expenseId=$expenseId"
            else "add_expense/$carId"
    }
    
    // Settings
    object Settings : Screen("settings")
}
