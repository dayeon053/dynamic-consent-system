package com.dynamicconsent.ui.notice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.NoticeRepository
import com.dynamicconsent.data.repository.RepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NoticeViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NoticeRepository =
        RepositoryProvider.noticeRepository(application.assets),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NoticeUiState())
    val uiState: StateFlow<NoticeUiState> = _uiState.asStateFlow()

    /** 새로고침을 연타해도 요청이 겹치지 않도록 이전 작업을 취소한다. */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 첫 페이지만 불러온다 — 5개 기업 규모라 더 볼 것이 없다.
                // 기업이 늘어 목록이 길어지면 이 지점에 페이지 추가 로드를 붙이면 된다.
                val notices = repository.getNotices()
                _uiState.update { it.copy(isLoading = false, notices = notices, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "공지사항을 불러오지 못했습니다. 다시 시도해 주세요.")
                }
            }
        }
    }
}
