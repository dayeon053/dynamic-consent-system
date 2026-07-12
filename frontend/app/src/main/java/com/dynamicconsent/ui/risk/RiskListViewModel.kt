package com.dynamicconsent.ui.risk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.repository.ConsentStateStore
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.domain.RiskRecalculator
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

    /** 동의 상태를 반영해 재산출된 최신 상세 데이터 (orgId 기준) */
    private var recalculatedDetails: Map<String, OrganizationDetail> = emptyMap()

    init {
        observeOrganizations()
    }

    private fun observeOrganizations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val organizations = repository.getOrganizations()
            val baseDetails = organizations
                .mapNotNull { repository.getOrganizationDetail(it.id) }
                .associateBy { it.organization.id }

            baseDetails.values.forEach { detail ->
                ConsentStateStore.initialize(
                    orgId = detail.organization.id,
                    enabledIds = detail.consentDetail.optionalConsents
                        .filter { it.enabled }
                        .map { it.id }
                        .toSet(),
                )
            }

            // 동의 상태가 바뀔 때마다 전체 기관의 점수를 재산출하고 위험도 내림차순으로 재정렬한다.
            ConsentStateStore.enabledConsents.collect { consentStates ->
                recalculatedDetails = baseDetails.mapValues { (orgId, detail) ->
                    RiskRecalculator.recalculate(detail, consentStates[orgId].orEmpty())
                }
                val sortedOrganizations = recalculatedDetails.values
                    .map { it.organization }
                    .sortedByDescending { it.riskScore }

                _uiState.update { state ->
                    val selectedId = state.selectedOrganizationId
                        ?: sortedOrganizations.firstOrNull()?.id
                    state.copy(
                        isLoading = false,
                        organizations = sortedOrganizations,
                        selectedOrganizationId = selectedId,
                        selectedDetail = selectedId?.let { recalculatedDetails[it] },
                    )
                }
            }
        }
    }

    fun selectOrganization(id: String) {
        _uiState.update {
            it.copy(selectedOrganizationId = id, selectedDetail = recalculatedDetails[id])
        }
    }
}
