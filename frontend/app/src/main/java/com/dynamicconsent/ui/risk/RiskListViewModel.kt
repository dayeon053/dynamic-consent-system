package com.dynamicconsent.ui.risk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RiskListViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OrganizationRepository = DummyOrganizationRepository(application.assets),
) : AndroidViewModel(application) {

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
