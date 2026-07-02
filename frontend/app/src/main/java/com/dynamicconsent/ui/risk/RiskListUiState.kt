package com.dynamicconsent.ui.risk

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail

data class RiskListUiState(
    val isLoading: Boolean = false,
    val organizations: List<Organization> = emptyList(),
    val selectedOrganizationId: String? = null,
    val selectedDetail: OrganizationDetail? = null,
)
