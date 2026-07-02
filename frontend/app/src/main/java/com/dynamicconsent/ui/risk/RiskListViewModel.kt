package com.dynamicconsent.ui.risk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RiskListViewModel(
    private val repository: OrganizationRepository = DummyOrganizationRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(RiskListUiState())
    val uiState: StateFlow<RiskListUiState> = _uiState.asStateFlow()

    init {
        loadOrganizations()
    }

    private fun loadOrganizations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val organizations = repository.getOrganizations()
            val firstId = organizations.firstOrNull()?.id
            _uiState.update {
                it.copy(isLoading = false, organizations = organizations, selectedOrganizationId = firstId)
            }
            firstId?.let { selectOrganization(it) }
        }
    }

    fun selectOrganization(id: String) {
        viewModelScope.launch {
            val detail = repository.getOrganizationDetail(id)
            _uiState.update { it.copy(selectedOrganizationId = id, selectedDetail = detail) }
        }
    }
}
