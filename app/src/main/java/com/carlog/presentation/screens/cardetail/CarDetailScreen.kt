package com.carlog.presentation.screens.cardetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.carlog.R
import com.carlog.domain.model.Car

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToParts: ((Long) -> Unit)? = null,
    onNavigateToBreakdowns: ((Long) -> Unit)? = null,
    onNavigateToAccidents: ((Long) -> Unit)? = null,
    onNavigateToConsumables: ((Long) -> Unit)? = null,
    onNavigateToStatistics: ((Long) -> Unit)? = null,
    onNavigateToRefuelings: ((Long) -> Unit)? = null,
    onNavigateToExpenses: ((Long) -> Unit)? = null,
    viewModel: CarDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showUpdateMileageDialog by viewModel.showUpdateMileageDialog.collectAsState()
    val showMileageInputDialog by viewModel.showMileageInputDialog.collectAsState()

    LaunchedEffect(carId) {
        viewModel.loadCar(carId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    when (val state = uiState) {
                        is CarDetailUiState.Success -> Text("${state.car.brand} ${state.car.model}")
                        else -> Text(stringResource(R.string.car_details))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState is CarDetailUiState.Success) {
                        IconButton(onClick = { onNavigateToEdit(carId) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CarDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is CarDetailUiState.Success -> {
                CarDetailContent(
                    car = state.car,
                    onNavigateToParts = onNavigateToParts,
                    onNavigateToBreakdowns = onNavigateToBreakdowns,
                    onNavigateToAccidents = onNavigateToAccidents,
                    onNavigateToConsumables = onNavigateToConsumables,
                    onNavigateToStatistics = onNavigateToStatistics,
                    onNavigateToRefuelings = onNavigateToRefuelings,
                    onNavigateToExpenses = onNavigateToExpenses,
                    onUpdateMileageClick = { viewModel.showUpdateMileageDialog() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            is CarDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && uiState is CarDetailUiState.Success) {
        val car = (uiState as CarDetailUiState.Success).car
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_car)) },
            text = { Text(stringResource(R.string.delete_car_confirmation, "${car.brand} ${car.model}")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCar(car) {
                            onNavigateBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Mileage update confirmation dialog
    if (showUpdateMileageDialog && uiState is CarDetailUiState.Success) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateMileageDialog() },
            title = { Text(stringResource(R.string.update_mileage_dialog_title)) },
            text = { Text(stringResource(R.string.update_mileage_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateMileageDialog()
                        viewModel.showMileageInputDialog()
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateMileageDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Mileage input dialog
    if (showMileageInputDialog && uiState is CarDetailUiState.Success) {
        val car = (uiState as CarDetailUiState.Success).car
        var newMileage by remember { mutableStateOf(car.currentMileage.toString()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissMileageInputDialog() },
            title = { Text(stringResource(R.string.new_mileage_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newMileage,
                        onValueChange = {
                            newMileage = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.new_mileage_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it) } }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mileageValue = newMileage.toIntOrNull()
                        when {
                            mileageValue == null -> {
                                errorMessage = "Введите корректное число"
                            }
                            mileageValue < car.currentMileage -> {
                                errorMessage = "Пробег не может быть меньше текущего"
                            }
                            else -> {
                                viewModel.updateMileage(car.id, mileageValue)
                                viewModel.dismissMileageInputDialog()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMileageInputDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun CarDetailContent(
    car: Car,
    onNavigateToParts: ((Long) -> Unit)? = null,
    onNavigateToBreakdowns: ((Long) -> Unit)? = null,
    onNavigateToAccidents: ((Long) -> Unit)? = null,
    onNavigateToConsumables: ((Long) -> Unit)? = null,
    onNavigateToStatistics: ((Long) -> Unit)? = null,
    onNavigateToRefuelings: ((Long) -> Unit)? = null,
    onNavigateToExpenses: ((Long) -> Unit)? = null,
    onUpdateMileageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photos Gallery
        if (!car.photosPaths.isNullOrEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.photos_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(car.photosPaths) { photoPath ->
                            Card(
                                modifier = Modifier.size(150.dp)
                            ) {
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = "Фото автомобиля",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Current Mileage Card
        ElevatedCard(
            onClick = onUpdateMileageClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.current_mileage),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${car.currentMileage} ${stringResource(R.string.km)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Basic Information
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.basic_information),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                InfoRow(label = stringResource(R.string.brand), value = car.brand)
                InfoRow(label = stringResource(R.string.model), value = car.model)
                car.year?.let { InfoRow(label = stringResource(R.string.year), value = it.toString()) }
                car.color?.let { InfoRow(label = stringResource(R.string.color), value = it) }
                car.licensePlate?.let { InfoRow(label = stringResource(R.string.license_plate), value = it) }
                car.vin?.let { InfoRow(label = stringResource(R.string.vin), value = it) }
            }
        }

        // Technical Specifications
        if (car.engineModel != null || car.engineVolume != null || 
            car.transmissionType != null || car.driveType != null || car.bodyType != null) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.technical_specifications),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    car.bodyType?.let { InfoRow(label = stringResource(R.string.body_type), value = it) }
                    car.engineModel?.let { InfoRow(label = stringResource(R.string.engine_model), value = it) }
                    car.engineVolume?.let { InfoRow(label = stringResource(R.string.engine_volume), value = "$it ${stringResource(R.string.liters)}") }
                    car.transmissionType?.let { InfoRow(label = stringResource(R.string.transmission_type), value = it) }
                    car.driveType?.let { InfoRow(label = stringResource(R.string.drive_type), value = it) }
                    InfoRow(label = stringResource(R.string.fuel_type), value = car.fuelType)
                }
            }
        }

        // Notes
        car.notes?.let { notes ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Navigation buttons for Parts and Breakdowns
        if (onNavigateToParts != null || onNavigateToBreakdowns != null || onNavigateToAccidents != null) {
            Text(
                text = stringResource(R.string.sections_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Заправки - на первом месте с синим цветом
            if (onNavigateToRefuelings != null) {
                val isElectric = car.fuelType == "Электро"
                OutlinedCard(
                    onClick = { onNavigateToRefuelings(car.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3).copy(alpha = 0.05f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF2196F3).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(if (isElectric) R.string.charging_title else R.string.refuelings),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(if (isElectric) R.string.charging_history else R.string.refueling_history),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            // Статистика - на втором месте
            if (onNavigateToStatistics != null) {
                OutlinedCard(
                    onClick = { onNavigateToStatistics(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.statistics_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.statistics_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            if (onNavigateToParts != null) {
                OutlinedCard(
                    onClick = { onNavigateToParts(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.parts_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.parts_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            if (onNavigateToBreakdowns != null) {
                OutlinedCard(
                    onClick = { onNavigateToBreakdowns(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.breakdowns_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.breakdowns_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            if (onNavigateToAccidents != null) {
                OutlinedCard(
                    onClick = { onNavigateToAccidents(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.accidents_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.accidents_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            if (onNavigateToConsumables != null) {
                OutlinedCard(
                    onClick = { onNavigateToConsumables(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.consumables_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.consumables_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
            
            // Прочие расходы - теперь бесцветные
            if (onNavigateToExpenses != null) {
                OutlinedCard(
                    onClick = { onNavigateToExpenses(car.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.expenses_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.expenses_section_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.then(Modifier.graphicsLayer(rotationZ = 180f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
