package com.dynamicconsent.ui.orgdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrgDetailViewModel(
    private val repository: OrganizationRepository = DummyOrganizationRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrgDetailUiState())
    val uiState: StateFlow<OrgDetailUiState> = _uiState.asStateFlow()

    fun loadOrganization(orgId: String, initialTab: OrgDetailTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, activeTab = initialTab) }
            val detail = repository.getOrganizationDetail(orgId)
            _uiState.update { it.copy(isLoading = false, detail = detail) }
        }
    }

    fun selectTab(tab: OrgDetailTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }
}
