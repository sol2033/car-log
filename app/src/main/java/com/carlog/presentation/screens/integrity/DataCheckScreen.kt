package com.carlog.presentation.screens.integrity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carlog.R
import com.carlog.data.integrity.EventCandidate
import com.carlog.data.integrity.EventType
import com.carlog.data.integrity.IntegrityFinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран «Проверка данных»: находит следы бага редактирования и другие расхождения,
 * которые автоматика не вправе решать сама, и позволяет исправить их на месте —
 * не уходя в разделы приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCheckScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    viewModel: DataCheckViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_check_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.rescan() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.data_check_rescan))
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    BackupReminder(onNavigateToBackup = onNavigateToBackup)
                }

                state.error?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (state.findings.isEmpty()) {
                    item { EmptyState() }
                }

                items(state.findings, key = { it.id }) { finding ->
                    FindingCard(
                        finding = finding,
                        onIgnore = { viewModel.ignoreFinding(finding) },
                        onLinkPart = { candidate ->
                            viewModel.linkPart(finding as IntegrityFinding.UnlinkedPart, candidate)
                        },
                        onApplyRepairCost = { serviceCost ->
                            viewModel.applyAccidentRepairCost(
                                finding as IntegrityFinding.AccidentWithoutRepairCost,
                                serviceCost
                            )
                        },
                        onAlignCost = {
                            viewModel.alignBreakdownCost(finding as IntegrityFinding.BreakdownCostMismatch)
                        },
                        onSetPurchaseInfo = { date, mileage ->
                            viewModel.setPurchaseInfo(
                                finding as IntegrityFinding.CarWithoutPurchaseInfo,
                                date,
                                mileage
                            )
                        },
                        onKeepSingleActive = {
                            viewModel.keepSingleActiveConsumable(
                                finding as IntegrityFinding.DuplicateActiveConsumables
                            )
                        }
                    )
                }

                if (state.hiddenCount > 0 || state.showHidden) {
                    item {
                        HiddenFindingsRow(
                            hiddenCount = state.hiddenCount,
                            showHidden = state.showHidden,
                            onToggle = { viewModel.toggleShowHidden() },
                            onRestore = { viewModel.restoreHidden() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupReminder(onNavigateToBackup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.data_check_backup_hint),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onNavigateToBackup) {
                Text(stringResource(R.string.data_check_open_backup))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = stringResource(R.string.data_check_empty),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.data_check_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HiddenFindingsRow(
    hiddenCount: Int,
    showHidden: Boolean,
    onToggle: () -> Unit,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onToggle) {
            Text(
                if (showHidden) stringResource(R.string.data_check_hide_ignored)
                else stringResource(R.string.data_check_show_ignored, hiddenCount)
            )
        }
        if (showHidden) {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.data_check_restore_ignored))
            }
        }
    }
}

@Composable
private fun FindingCard(
    finding: IntegrityFinding,
    onIgnore: () -> Unit,
    onLinkPart: (EventCandidate) -> Unit,
    onApplyRepairCost: (Double) -> Unit,
    onAlignCost: () -> Unit,
    onSetPurchaseInfo: (Long, Int) -> Unit,
    onKeepSingleActive: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = finding.carLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (finding) {
                is IntegrityFinding.UnlinkedPart ->
                    UnlinkedPartBody(finding, onLinkPart)

                is IntegrityFinding.AccidentWithoutRepairCost ->
                    AccidentRepairCostBody(finding, onApplyRepairCost)

                is IntegrityFinding.BreakdownCostMismatch ->
                    BreakdownMismatchBody(finding, onAlignCost)

                is IntegrityFinding.CarWithoutPurchaseInfo ->
                    PurchaseInfoBody(finding, onSetPurchaseInfo)

                is IntegrityFinding.DuplicateActiveConsumables ->
                    DuplicateConsumablesBody(finding, onKeepSingleActive)
            }

            TextButton(onClick = onIgnore) {
                Text(stringResource(R.string.data_check_ignore))
            }
        }
    }
}

@Composable
private fun UnlinkedPartBody(
    finding: IntegrityFinding.UnlinkedPart,
    onLink: (EventCandidate) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    var selected by remember(finding.id) { mutableStateOf(finding.candidates.first()) }

    FindingTitle(stringResource(R.string.data_check_unlinked_part_title))
    Text(
        text = "${finding.part.name} · %.2f ₽ · %s · %d км".format(
            finding.part.price,
            dateFormat.format(Date(finding.part.installDate)),
            finding.part.installMileage
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = stringResource(R.string.data_check_unlinked_part_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column {
        finding.candidates.forEach { candidate ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == candidate,
                    onClick = { selected = candidate }
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = candidate.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (candidate.type) {
                            EventType.BREAKDOWN -> stringResource(
                                R.string.data_check_candidate_breakdown,
                                candidate.declaredPartsCost ?: 0.0
                            )
                            EventType.ACCIDENT -> stringResource(
                                R.string.data_check_candidate_accident,
                                candidate.declaredPartsCost ?: 0.0
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Button(onClick = { onLink(selected) }) {
        Text(stringResource(R.string.data_check_link_part))
    }
}

@Composable
private fun AccidentRepairCostBody(
    finding: IntegrityFinding.AccidentWithoutRepairCost,
    onApply: (Double) -> Unit
) {
    var serviceCost by remember(finding.id) { mutableStateOf("") }

    FindingTitle(stringResource(R.string.data_check_accident_cost_title))
    Text(
        text = stringResource(R.string.data_check_accident_cost_body, finding.linkedPartsSum),
        style = MaterialTheme.typography.bodyMedium
    )

    OutlinedTextField(
        value = serviceCost,
        onValueChange = { input -> serviceCost = input.filter { it.isDigit() || it == '.' || it == ',' } },
        label = { Text(stringResource(R.string.data_check_service_cost_label)) },
        supportingText = { Text(stringResource(R.string.data_check_service_cost_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    Button(onClick = { onApply(serviceCost.replace(',', '.').toDoubleOrNull() ?: 0.0) }) {
        Text(
            stringResource(
                R.string.data_check_apply_repair_cost,
                finding.linkedPartsSum + (serviceCost.replace(',', '.').toDoubleOrNull() ?: 0.0)
            )
        )
    }
}

@Composable
private fun BreakdownMismatchBody(
    finding: IntegrityFinding.BreakdownCostMismatch,
    onAlign: () -> Unit
) {
    FindingTitle(stringResource(R.string.data_check_cost_mismatch_title))
    Text(
        text = stringResource(
            R.string.data_check_cost_mismatch_body,
            finding.breakdown.title,
            finding.breakdown.partsCost,
            finding.linkedPartsSum
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = onAlign) {
        Text(stringResource(R.string.data_check_align_cost, finding.linkedPartsSum))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseInfoBody(
    finding: IntegrityFinding.CarWithoutPurchaseInfo,
    onApply: (Long, Int) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    var date by remember(finding.id) { mutableStateOf(finding.car.purchaseDate ?: System.currentTimeMillis()) }
    var mileage by remember(finding.id) {
        mutableStateOf(finding.car.purchaseMileage?.toString() ?: "")
    }
    var showDatePicker by remember(finding.id) { mutableStateOf(false) }

    FindingTitle(stringResource(R.string.data_check_purchase_title))
    Text(
        text = stringResource(R.string.data_check_purchase_body),
        style = MaterialTheme.typography.bodyMedium
    )

    OutlinedTextField(
        value = dateFormat.format(Date(date)),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.car_purchase_date)) },
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.DateRange, stringResource(R.string.select_date))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = mileage,
        onValueChange = { input -> mileage = input.filter { it.isDigit() } },
        label = { Text(stringResource(R.string.car_purchase_mileage)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = { onApply(date, mileage.toIntOrNull() ?: 0) },
        enabled = mileage.toIntOrNull() != null
    ) {
        Text(stringResource(R.string.save))
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DuplicateConsumablesBody(
    finding: IntegrityFinding.DuplicateActiveConsumables,
    onKeepSingle: () -> Unit
) {
    FindingTitle(stringResource(R.string.data_check_duplicate_title))
    Text(
        text = stringResource(
            R.string.data_check_duplicate_body,
            finding.category,
            finding.consumables.size
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    finding.consumables.forEach { consumable ->
        Text(
            text = "· %d км".format(consumable.installationMileage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Button(onClick = onKeepSingle) {
        Text(stringResource(R.string.data_check_keep_newest))
    }
}

@Composable
private fun FindingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
