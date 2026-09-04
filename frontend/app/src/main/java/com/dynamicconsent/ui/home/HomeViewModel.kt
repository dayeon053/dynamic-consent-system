package com.dynamicconsent.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.RecentConsentChange
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.repository.ConsentStateStore
import com.dynamicconsent.data.repository.NoticeRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.data.repository.RepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OrganizationRepository =
        RepositoryProvider.organizationRepository(application.assets),
    private val noticeRepository: NoticeRepository =
        RepositoryProvider.noticeRepository(application.assets),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 실 API 모드에서만 존재. 전체 기업의 변경 이력을 서버에서 받아올 때 쓴다. */
    private val apiRepository = RepositoryProvider.apiRepositoryOrNull()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val organizations = try {
                repository.getOrganizations().organizations
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "기관 정보를 불러오지 못했습니다. 다시 시도해 주세요.")
                }
                return@launch
            }

            // 공지사항·변경 내역은 부가 정보라, 실패해도 홈 화면 자체는 보여준다.
            val notices = runCatchingExceptCancellation { noticeRepository.getNotices() } ?: emptyList()
            val recentChanges = loadRecentChanges(organizations)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    riskyOrganizationCount = organizations.count { org -> org.riskGrade >= RISK_THRESHOLD },
                    categories = organizations.map(Organization::category).distinct(),
                    recentChanges = recentChanges,
                    notices = notices.take(MAX_HOME_ITEMS),
                )
            }
        }
    }

    /**
     * 실 API 모드면 서버 이력을, mock 모드면 이 세션에서 쌓인 토글 기록을 쓴다.
     * mock에는 서버 이력이라는 개념이 없어 앱을 켜고 아무것도 안 만졌으면 비어 있는 게 정상이다.
     */
    private suspend fun loadRecentChanges(organizations: List<Organization>): List<RecentConsentChange> {
        apiRepository?.let { apiRepo ->
            return runCatchingExceptCancellation { apiRepo.getRecentConsentChanges(MAX_HOME_ITEMS) }
                ?: emptyList()
        }

        val nameByOrgId = organizations.associate { it.id to it.name }
        return ConsentStateStore.changeHistory.value
            .flatMap { (orgId, records) ->
                records.map { record ->
                    RecentConsentChange(
                        companyName = nameByOrgId[orgId] ?: orgId,
                        consentTitle = record.consentTitle,
                        enabled = record.enabled,
                        timestampMillis = record.timestampMillis,
                    )
                }
            }
            .sortedByDescending { it.timestampMillis }
            .take(MAX_HOME_ITEMS)
    }

    /** 취소는 그대로 전파하고 나머지 실패만 null로 삼킨다. */
    private suspend fun <T> runCatchingExceptCancellation(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** 홈의 각 섹션에 보여줄 최대 건수 (더 보려면 해당 화면으로 이동) */
        const val MAX_HOME_ITEMS = 2

        /**
         * "위험도가 감지되었어요"로 셀 최소 등급.
         * 보통(MEDIUM) 이상이면 사용자가 확인해볼 가치가 있다고 보고 센다.
         */
        val RISK_THRESHOLD = RiskGrade.MEDIUM
    }
}
