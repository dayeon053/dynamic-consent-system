package com.dynamicconsent.ui.home

import com.dynamicconsent.data.model.ConsentItem

data class HomeUiState(
    val isLoading: Boolean = false,
    val items: List<ConsentItem> = emptyList(),
)
