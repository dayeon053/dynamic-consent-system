package com.dynamicconsent.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.ConsentRepository
import com.dynamicconsent.data.repository.DummyConsentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ConsentRepository = DummyConsentRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadConsentItems()
    }

    private fun loadConsentItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = repository.getConsentItems()
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }
}
