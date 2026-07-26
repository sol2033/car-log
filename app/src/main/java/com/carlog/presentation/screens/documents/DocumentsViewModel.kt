package com.carlog.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.repository.CarDocumentRepository
import com.carlog.domain.model.CarDocument
import com.carlog.domain.model.DocumentTypes
import com.carlog.util.DocumentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentWithStatus(
    val type: String,
    val document: CarDocument?,
    val statusInfo: DocumentStatus.StatusInfo?
)

sealed class DocumentsUiState {
    object Loading : DocumentsUiState()
    data class Success(
        val activeDocuments: List<DocumentWithStatus>,
        val archivedDocuments: List<CarDocument>
    ) : DocumentsUiState()
    data class Error(val message: String) : DocumentsUiState()
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: CarDocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val carId: Long = savedStateHandle.get<Long>("carId") ?: 0L

    private val _uiState = MutableStateFlow<DocumentsUiState>(DocumentsUiState.Loading)
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            combine<List<CarDocument>, List<CarDocument>, DocumentsUiState>(
                documentRepository.getActiveDocuments(carId),
                documentRepository.getArchivedDocuments(carId)
            ) { active, archived ->
                val activeByType = active.groupBy { it.type }

                // Плитки стандартных типов — всегда (пустые = «не добавлен»)
                val standardTiles = DocumentTypes.STANDARD.map { type ->
                    val document = activeByType[type]?.firstOrNull()
                    DocumentWithStatus(
                        type = type,
                        document = document,
                        statusInfo = document?.let { DocumentStatus.calculateStatus(it.expiryDate) }
                    )
                }

                // Произвольные документы — только существующие. Фильтр «не стандартный»
                // (а не «== Другое») ловит и записи упразднённых типов,
                // чтобы они не пропали с экрана
                val customTiles = active
                    .filter { it.type !in DocumentTypes.STANDARD }
                    .map { document ->
                        DocumentWithStatus(
                            type = document.displayName,
                            document = document,
                            statusInfo = DocumentStatus.calculateStatus(document.expiryDate)
                        )
                    }

                DocumentsUiState.Success(
                    activeDocuments = standardTiles + customTiles,
                    archivedDocuments = archived
                )
            }.catch { e ->
                emit(DocumentsUiState.Error(e.message ?: "Ошибка загрузки"))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
