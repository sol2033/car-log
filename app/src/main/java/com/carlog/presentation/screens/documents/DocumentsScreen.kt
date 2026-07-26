package com.carlog.presentation.screens.documents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.domain.model.CarDocument
import com.carlog.domain.model.DocumentTypes
import com.carlog.util.DocumentStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    carId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit,
    onNavigateToAddDocument: (Long, String?) -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_section_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { onNavigateToAddDocument(carId, null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_document))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.active_tab)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.history_tab)) }
                )
            }

            when (val state = uiState) {
                is DocumentsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DocumentsUiState.Success -> {
                    if (selectedTab == 0) {
                        ActiveDocumentsGrid(
                            carId = carId,
                            documents = state.activeDocuments,
                            onDocumentClick = onNavigateToDocumentDetail,
                            onAddDocument = onNavigateToAddDocument
                        )
                    } else {
                        ArchivedDocumentsList(
                            documents = state.archivedDocuments,
                            onDocumentClick = onNavigateToDocumentDetail
                        )
                    }
                }
                is DocumentsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveDocumentsGrid(
    carId: Long,
    documents: List<DocumentWithStatus>,
    onDocumentClick: (Long) -> Unit,
    onAddDocument: (Long, String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(documents) { documentWithStatus ->
            DocumentCard(
                documentWithStatus = documentWithStatus,
                onClick = {
                    if (documentWithStatus.document != null) {
                        onDocumentClick(documentWithStatus.document.id)
                    } else {
                        // Открыть форму добавления с предзаполненным типом
                        onAddDocument(carId, documentWithStatus.type)
                    }
                }
            )
        }
    }
}

@Composable
fun DocumentCard(
    documentWithStatus: DocumentWithStatus,
    onClick: () -> Unit
) {
    val document = documentWithStatus.document
    val statusInfo = documentWithStatus.statusInfo
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Цвета карточек — те же, что у расходников
    val cardColor = if (document == null) {
        if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFBBDEFB)
    } else {
        when (statusInfo?.status) {
            DocumentStatus.Status.NORMAL -> if (isDarkTheme) Color(0xFF2E5C3A) else Color(0xFFC8E6C9)
            DocumentStatus.Status.WARNING -> if (isDarkTheme) Color(0xFF5C5230) else Color(0xFFFFF9C4)
            DocumentStatus.Status.CRITICAL -> if (isDarkTheme) Color(0xFF5C2E2E) else Color(0xFFFFCDD2)
            else -> MaterialTheme.colorScheme.surface
        }
    }

    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = documentWithStatus.type,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Start
            )

            if (document == null) {
                Text(
                    text = stringResource(R.string.document_not_added),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1976D2),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    document.organization?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = stringResource(
                            if (document.type == DocumentTypes.VEHICLE_TAX) {
                                R.string.document_tax_next_accrual_until
                            } else {
                                R.string.document_valid_until
                            },
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(document.expiryDate))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    statusInfo?.let { info ->
                        val expired = info.remainingDays < 0
                        val isTax = document.type == DocumentTypes.VEHICLE_TAX
                        Text(
                            text = when {
                                expired && isTax ->
                                    stringResource(R.string.document_tax_accrued_days_ago, -info.remainingDays)
                                expired ->
                                    stringResource(R.string.document_expired_days, -info.remainingDays)
                                // Для налога срок длинный — показываем месяцы, дни только под конец
                                isTax && info.remainingDays >= 30 ->
                                    stringResource(R.string.document_expires_in_months, info.remainingDays / 30)
                                else ->
                                    stringResource(R.string.document_expires_in_days, info.remainingDays)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                expired || info.status == DocumentStatus.Status.CRITICAL ->
                                    MaterialTheme.colorScheme.error
                                info.status == DocumentStatus.Status.WARNING ->
                                    if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFF57C00)
                                else ->
                                    if (isDarkTheme) Color(0xFF81C784) else Color(0xFF388E3C)
                            },
                            fontWeight = if (expired) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedDocumentsList(
    documents: List<CarDocument>,
    onDocumentClick: (Long) -> Unit
) {
    if (documents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.documents_history_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(documents) { document ->
            OutlinedCard(
                onClick = { onDocumentClick(document.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = document.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        document.cost?.let {
                            Text(
                                text = "$it ₽",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    document.organization?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        text = stringResource(
                            if (document.type == DocumentTypes.VEHICLE_TAX) {
                                R.string.document_tax_next_accrual_until
                            } else {
                                R.string.document_valid_until
                            },
                            dateFormat.format(Date(document.expiryDate))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
