package com.carlog.presentation.navigation

sealed class Screen(val route: String) {
    // Main screens
    object CarList : Screen("car_list")
    object CarDetail : Screen("car_detail/{carId}") {
        fun createRoute(carId: Long) = "car_detail/$carId"
    }
    object AddCar : Screen("add_car")
    object EditCar : Screen("edit_car/{carId}") {
        fun createRoute(carId: Long) = "edit_car/$carId"
    }
    
    // Statistics
    object Statistics : Screen("statistics")
    
    // Settings
    object Settings : Screen("settings")
}
