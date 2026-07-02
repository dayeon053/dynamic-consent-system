package com.dynamicconsent.ui.detail

import com.dynamicconsent.data.model.ConsentItem

data class DetailUiState(
    val isLoading: Boolean = false,
    val item: ConsentItem? = null,
)
