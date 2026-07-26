package com.carlog.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.data.backup.DataChangeNotifier
import com.carlog.data.repository.CarDocumentRepository
import com.carlog.domain.model.CarDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val documentRepository: CarDocumentRepository,
    private val dataChangeNotifier: DataChangeNotifier,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: Long = savedStateHandle.get<Long>("documentId") ?: 0L

    val document: StateFlow<CarDocument?> = documentRepository.getDocumentById(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    fun deleteDocument() {
        viewModelScope.launch {
            document.value?.let {
                documentRepository.deleteDocument(it)
                dataChangeNotifier.notifyDataChanged()
                _deleteSuccess.value = true
            }
        }
    }
}
