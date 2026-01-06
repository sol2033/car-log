package com.carlog.presentation.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    onNavigateBack: () -> Unit,
    isFirstTime: Boolean = false,
    viewModel: AppInfoViewModel = hiltViewModel()
) {
    var expandedSection by remember { mutableStateOf<String?>("important") }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_info_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isFirstTime) {
                            scope.launch {
                                viewModel.completeFirstLaunch()
                                onNavigateBack()
                            }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ВАЖНО - красный раздел
            InfoSection(
                title = stringResource(R.string.info_important_title),
                content = stringResource(R.string.info_important_content),
                isExpanded = expandedSection == "important",
                onToggle = { expandedSection = if (expandedSection == "important") null else "important" },
                titleColor = Color.Red
            )
            
            HorizontalDivider()
            
            // Автомобили
            InfoSection(
                title = stringResource(R.string.info_cars_title),
                content = stringResource(R.string.info_cars_content),
                isExpanded = expandedSection == "cars",
                onToggle = { expandedSection = if (expandedSection == "cars") null else "cars" }
            )
            
            HorizontalDivider()
            
            // Расходники
            InfoSection(
                title = stringResource(R.string.info_consumables_title),
                content = stringResource(R.string.info_consumables_content),
                isExpanded = expandedSection == "consumables",
                onToggle = { expandedSection = if (expandedSection == "consumables") null else "consumables" }
            )
            
            HorizontalDivider()
            
            // Детали
            InfoSection(
                title = stringResource(R.string.info_parts_title),
                content = stringResource(R.string.info_parts_content),
                isExpanded = expandedSection == "parts",
                onToggle = { expandedSection = if (expandedSection == "parts") null else "parts" }
            )
            
            HorizontalDivider()
            
            // Поломки
            InfoSection(
                title = stringResource(R.string.info_breakdowns_title),
                content = stringResource(R.string.info_breakdowns_content),
                isExpanded = expandedSection == "breakdowns",
                onToggle = { expandedSection = if (expandedSection == "breakdowns") null else "breakdowns" }
            )
            
            HorizontalDivider()
            
            // ДТП
            InfoSection(
                title = stringResource(R.string.info_accidents_title),
                content = stringResource(R.string.info_accidents_content),
                isExpanded = expandedSection == "accidents",
                onToggle = { expandedSection = if (expandedSection == "accidents") null else "accidents" }
            )
            
            HorizontalDivider()
            
            // Заправки
            InfoSection(
                title = stringResource(R.string.info_refueling_title),
                content = stringResource(R.string.info_refueling_content),
                isExpanded = expandedSection == "refueling",
                onToggle = { expandedSection = if (expandedSection == "refueling") null else "refueling" }
            )
            
            HorizontalDivider()
            
            // Прочие расходы
            InfoSection(
                title = stringResource(R.string.info_expenses_title),
                content = stringResource(R.string.info_expenses_content),
                isExpanded = expandedSection == "expenses",
                onToggle = { expandedSection = if (expandedSection == "expenses") null else "expenses" }
            )
            
            HorizontalDivider()
            
            // Статистика
            InfoSection(
                title = stringResource(R.string.info_statistics_title),
                content = stringResource(R.string.info_statistics_content),
                isExpanded = expandedSection == "statistics",
                onToggle = { expandedSection = if (expandedSection == "statistics") null else "statistics" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSection(
    title: String,
    content: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            },
            modifier = Modifier
                .fillMaxWidth()
        )
        
        if (isExpanded) {
            Text(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        TextButton(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isExpanded) stringResource(R.string.info_collapse) else stringResource(R.string.info_expand)
            )
        }
    }
}
