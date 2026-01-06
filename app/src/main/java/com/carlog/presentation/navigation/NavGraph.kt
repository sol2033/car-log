package com.carlog.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carlog.presentation.screens.accidents.AccidentDetailScreen
import com.carlog.presentation.screens.accidents.AccidentsScreen
import com.carlog.presentation.screens.accidents.AddAccidentScreen
import com.carlog.presentation.screens.breakdowns.AddBreakdownScreen
import com.carlog.presentation.screens.breakdowns.BreakdownDetailScreen
import com.carlog.presentation.screens.breakdowns.BreakdownsScreen
import com.carlog.presentation.screens.car.AddCarScreen
import com.carlog.presentation.screens.cardetail.CarDetailScreen
import com.carlog.presentation.screens.consumables.AddConsumableScreen
import com.carlog.presentation.screens.consumables.CategoryHistoryScreen
import com.carlog.presentation.screens.consumables.ConsumableDetailScreen
import com.carlog.presentation.screens.consumables.ConsumablesHistoryScreen
import com.carlog.presentation.screens.consumables.ConsumablesScreen
import com.carlog.presentation.screens.consumables.ConsumablesSettingsScreen
import com.carlog.presentation.screens.home.CarListScreen
import com.carlog.presentation.screens.parts.AddPartScreen
import com.carlog.presentation.screens.parts.PartDetailScreen
import com.carlog.presentation.screens.parts.PartsScreen

// Легковесные анимации для навигации
private const val ANIMATION_DURATION = 250

private fun enterTransition() = fadeIn(animationSpec = tween(ANIMATION_DURATION))

private fun exitTransition() = fadeOut(animationSpec = tween(ANIMATION_DURATION))

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.CarList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { enterTransition() },
        popExitTransition = { exitTransition() }
    ) {
        // Language Selection Screen (First Launch)
        composable(route = Screen.LanguageSelection.route) {
            com.carlog.presentation.screens.language.LanguageSelectionScreen(
                onLanguageSelected = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.LanguageSelection.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Welcome Screen (First Launch)
        composable(route = Screen.Welcome.route) {
            com.carlog.presentation.screens.welcome.WelcomeScreen(
                onSkip = {
                    navController.navigate(Screen.CarList.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onReadInfo = {
                    navController.navigate("${Screen.AppInfo.route}?firstTime=true") {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
        
        // App Info Screen
        composable(
            route = Screen.AppInfo.route + "?firstTime={firstTime}",
            arguments = listOf(
                navArgument("firstTime") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val isFirstTime = backStackEntry.arguments?.getBoolean("firstTime") ?: false
            com.carlog.presentation.screens.info.AppInfoScreen(
                isFirstTime = isFirstTime,
                onNavigateBack = {
                    navController.navigate(Screen.CarList.route) {
                        popUpTo(Screen.AppInfo.route) { inclusive = true }
                    }
                }
            )
        }
        
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
                },
                onInfoClick = {
                    navController.navigate(Screen.AppInfo.route)
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
        
        // Car Detail Screen
        composable(
            route = Screen.CarDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            CarDetailScreen(
                carId = carId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditCar.createRoute(id))
                },
                onNavigateToParts = { id ->
                    navController.navigate(Screen.Parts.createRoute(id))
                },
                onNavigateToBreakdowns = { id ->
                    navController.navigate(Screen.Breakdowns.createRoute(id))
                },
                onNavigateToAccidents = { id ->
                    navController.navigate(Screen.Accidents.createRoute(id))
                },
                onNavigateToConsumables = { id ->
                    navController.navigate(Screen.Consumables.createRoute(id))
                },
                onNavigateToStatistics = { id ->
                    navController.navigate(Screen.Statistics.createRoute(id))
                },
                onNavigateToRefuelings = { id ->
                    navController.navigate(Screen.Refuelings.createRoute(id))
                },
                onNavigateToExpenses = { id ->
                    navController.navigate(Screen.Expenses.createRoute(id))
                }
            )
        }
        
        // Edit Car Screen
        composable(
            route = Screen.EditCar.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            AddCarScreen(
                carId = carId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Statistics Screen (будет реализован позже)
        composable(route = Screen.Statistics.route) {
            // TODO: Implement StatisticsScreen
        }
        
        // Settings Screen
        composable(route = Screen.Settings.route) {
            com.carlog.presentation.screens.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Parts List Screen
        composable(
            route = Screen.Parts.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            PartsScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddPart = { id ->
                    navController.navigate(Screen.AddPart.createRoute(id))
                },
                onNavigateToPartDetail = { cId, partId ->
                    navController.navigate(Screen.PartDetail.createRoute(cId, partId))
                }
            )
        }
        
        // Part Detail Screen
        composable(
            route = Screen.PartDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("partId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val partId = backStackEntry.arguments?.getLong("partId") ?: return@composable
            PartDetailScreen(
                carId = carId,
                partId = partId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { cId, pId ->
                    navController.navigate(Screen.EditPart.createRoute(cId, pId))
                },
                onNavigateToAddPart = { cId ->
                    navController.navigate(Screen.AddPart.createRoute(cId))
                }
            )
        }
        
        // Add Part Screen
        composable(
            route = Screen.AddPart.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            AddPartScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Edit Part Screen
        composable(
            route = Screen.EditPart.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("partId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val partId = backStackEntry.arguments?.getLong("partId") ?: return@composable
            AddPartScreen(
                carId = carId,
                partId = partId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Breakdowns List Screen
        composable(
            route = Screen.Breakdowns.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            BreakdownsScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddBreakdown = { id ->
                    navController.navigate(Screen.AddBreakdown.createRoute(id))
                },
                onNavigateToBreakdownDetail = { cId, breakdownId ->
                    navController.navigate(Screen.BreakdownDetail.createRoute(cId, breakdownId))
                }
            )
        }
        
        // Breakdown Detail Screen
        composable(
            route = Screen.BreakdownDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("breakdownId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val breakdownId = backStackEntry.arguments?.getLong("breakdownId") ?: return@composable
            BreakdownDetailScreen(
                carId = carId,
                breakdownId = breakdownId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { cId, bId ->
                    navController.navigate(Screen.EditBreakdown.createRoute(cId, bId))
                }
            )
        }
        
        // Add Breakdown Screen
        composable(
            route = Screen.AddBreakdown.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            AddBreakdownScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Edit Breakdown Screen
        composable(
            route = Screen.EditBreakdown.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("breakdownId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val breakdownId = backStackEntry.arguments?.getLong("breakdownId") ?: return@composable
            AddBreakdownScreen(
                carId = carId,
                breakdownId = breakdownId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Accidents List Screen
        composable(
            route = Screen.Accidents.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            AccidentsScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddAccident = { id ->
                    navController.navigate(Screen.AddAccident.createRoute(id))
                },
                onNavigateToAccidentDetail = { cId, accidentId ->
                    navController.navigate(Screen.AccidentDetail.createRoute(cId, accidentId))
                }
            )
        }
        
        // Accident Detail Screen
        composable(
            route = Screen.AccidentDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("accidentId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val accidentId = backStackEntry.arguments?.getLong("accidentId") ?: return@composable
            AccidentDetailScreen(
                accidentId = accidentId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { aId ->
                    navController.navigate(Screen.EditAccident.createRoute(carId, aId))
                }
            )
        }
        
        // Add Accident Screen
        composable(
            route = Screen.AddAccident.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            AddAccidentScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Edit Accident Screen
        composable(
            route = Screen.EditAccident.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("accidentId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val accidentId = backStackEntry.arguments?.getLong("accidentId") ?: return@composable
            AddAccidentScreen(
                carId = carId,
                accidentId = accidentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Consumables Screen
        composable(
            route = Screen.Consumables.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            ConsumablesScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConsumableDetail = { consumableId ->
                    navController.navigate(Screen.ConsumableDetail.createRoute(carId, consumableId))
                },
                onNavigateToAddConsumable = { id, category ->
                    navController.navigate(Screen.AddConsumable.createRoute(id, category))
                },
                onNavigateToHistory = { id ->
                    navController.navigate(Screen.ConsumablesHistory.createRoute(id))
                },
                onNavigateToSettings = { id ->
                    navController.navigate(Screen.ConsumablesSettings.createRoute(id))
                }
            )
        }
        
        // Consumable Detail Screen
        composable(
            route = Screen.ConsumableDetail.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("consumableId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val consumableId = backStackEntry.arguments?.getLong("consumableId") ?: return@composable
            ConsumableDetailScreen(
                carId = carId,
                consumableId = consumableId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { cId, conId ->
                    navController.navigate(Screen.EditConsumable.createRoute(cId, conId))
                },
                onNavigateToAddConsumable = { cId, category ->
                    navController.navigate(Screen.AddConsumable.createRoute(cId, category))
                }
            )
        }
        
        // Add Consumable Screen
        composable(
            route = Screen.AddConsumable.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("category") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val category = backStackEntry.arguments?.getString("category") ?: return@composable
            AddConsumableScreen(
                carId = carId,
                category = category,
                consumableId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Edit Consumable Screen
        composable(
            route = Screen.EditConsumable.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("consumableId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val consumableId = backStackEntry.arguments?.getLong("consumableId") ?: return@composable
            AddConsumableScreen(
                carId = carId,
                category = "",
                consumableId = consumableId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Consumables History Screen
        composable(
            route = Screen.ConsumablesHistory.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            ConsumablesHistoryScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategoryHistory = { id, category ->
                    navController.navigate(Screen.CategoryHistory.createRoute(id, category))
                }
            )
        }
        
        // Category History Screen
        composable(
            route = Screen.CategoryHistory.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("category") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val encodedCategory = backStackEntry.arguments?.getString("category") ?: return@composable
            val category = java.net.URLDecoder.decode(encodedCategory, "UTF-8")
            CategoryHistoryScreen(
                carId = carId,
                category = category,
                onNavigateBack = { navController.popBackStack() },
                onConsumableClick = { consumableId ->
                    navController.navigate(Screen.ConsumableDetail.createRoute(carId, consumableId))
                }
            )
        }
        
        // Consumables Settings Screen
        composable(
            route = Screen.ConsumablesSettings.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            ConsumablesSettingsScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Statistics Screen
        composable(
            route = Screen.Statistics.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            com.carlog.presentation.screens.statistics.StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBreakdowns = { 
                    navController.navigate(Screen.Breakdowns.createRoute(carId))
                },
                onNavigateToConsumables = {
                    navController.navigate(Screen.Consumables.createRoute(carId))
                }
            )
        }
        
        // Refuelings Screen
        composable(
            route = Screen.Refuelings.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            com.carlog.presentation.screens.refuelings.RefuelingsScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddRefueling = { cId, refuelingId ->
                    navController.navigate(Screen.AddRefueling.createRoute(cId, refuelingId))
                }
            )
        }
        
        // Add/Edit Refueling Screen
        composable(
            route = Screen.AddRefueling.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("refuelingId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val refuelingIdString = backStackEntry.arguments?.getString("refuelingId")
            val refuelingId = refuelingIdString?.toLongOrNull()
            com.carlog.presentation.screens.refuelings.AddRefuelingScreen(
                carId = carId,
                refuelingId = refuelingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Expenses Screen
        composable(
            route = Screen.Expenses.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            com.carlog.presentation.screens.expenses.ExpensesScreen(
                carId = carId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddExpense = { cId, expenseId ->
                    navController.navigate(Screen.AddExpense.createRoute(cId, expenseId))
                },
                onNavigateToExpenseDetail = { expenseId ->
                    navController.navigate(Screen.ExpenseDetail.createRoute(expenseId))
                }
            )
        }
        
        // Add/Edit Expense Screen
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("expenseId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getLong("carId") ?: return@composable
            val expenseIdString = backStackEntry.arguments?.getString("expenseId")
            val expenseId = expenseIdString?.toLongOrNull()
            com.carlog.presentation.screens.expenses.AddExpenseScreen(
                carId = carId,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Expense Detail Screen
        composable(
            route = Screen.ExpenseDetail.route,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: return@composable
            com.carlog.presentation.screens.expenses.ExpenseDetailScreen(
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { cId, eId ->
                    navController.navigate(Screen.AddExpense.createRoute(cId, eId))
                }
            )
        }
    }
}
