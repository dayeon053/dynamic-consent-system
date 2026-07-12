package com.dynamicconsent.ui.orgdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.ConsentStateStore
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.domain.RiskRecalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrgDetailViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OrganizationRepository = DummyOrganizationRepository(application.assets),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OrgDetailUiState())
    val uiState: StateFlow<OrgDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun loadOrganization(orgId: String, initialTab: OrgDetailTab) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, activeTab = initialTab) }
            val baseDetail = repository.getOrganizationDetail(orgId)
            if (baseDetail == null) {
                _uiState.update { it.copy(isLoading = false, detail = null) }
                return@launch
            }

            ConsentStateStore.initialize(
                orgId = orgId,
                enabledIds = baseDetail.consentDetail.optionalConsents
                    .filter { it.enabled }
                    .map { it.id }
                    .toSet(),
            )

            // 스위치 토글 시 위험도 점수·등급·분석 정보가 즉시 재산출된다.
            ConsentStateStore.enabledConsents.collect { consentStates ->
                val recalculated =
                    RiskRecalculator.recalculate(baseDetail, consentStates[orgId].orEmpty())
                _uiState.update { it.copy(isLoading = false, detail = recalculated) }
            }
        }
    }

    fun toggleConsent(consentId: Int, enabled: Boolean) {
        val orgId = _uiState.value.detail?.organization?.id ?: return
        ConsentStateStore.setConsent(orgId, consentId, enabled)
    }

    fun selectTab(tab: OrgDetailTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }
}
