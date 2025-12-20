package com.carlog.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carlog.presentation.screens.car.AddCarScreen
import com.carlog.presentation.screens.home.CarListScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.CarList.route
    ) {
        // Car List Screen
        composable(route = Screen.CarList.route) {
            CarListScreen(
                onCarClick = { carId ->
                    navController.navigate(Screen.CarDetail.createRoute(carId))
                },
                onAddCarClick = {
                    navController.navigate(Screen.AddCar.route)
                },
                onStatisticsClick = {
                    navController.navigate(Screen.Statistics.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // Add Car Screen
        composable(route = Screen.AddCar.route) {
            AddCarScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Car Detail Screen (будет реализован позже)
        composable(
            route = Screen.CarDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) {
            // TODO: Implement CarDetailScreen
        }
        
        // Statistics Screen (будет реализован позже)
        composable(route = Screen.Statistics.route) {
            // TODO: Implement StatisticsScreen
        }
        
        // Settings Screen (будет реализован позже)
        composable(route = Screen.Settings.route) {
            // TODO: Implement SettingsScreen
        }
    }
}
