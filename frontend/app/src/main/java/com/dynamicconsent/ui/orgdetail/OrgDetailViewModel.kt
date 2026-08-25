package com.dynamicconsent.ui.orgdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicconsent.data.repository.ApiOrganizationRepository
import com.dynamicconsent.data.repository.ConsentStateStore
import com.dynamicconsent.data.repository.ConsentSyncManager
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.data.repository.RepositoryProvider
import com.dynamicconsent.domain.RiskRecalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrgDetailViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OrganizationRepository =
        RepositoryProvider.organizationRepository(application.assets),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OrgDetailUiState())
    val uiState: StateFlow<OrgDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentOrgId: String? = null
    private var currentInitialTab: OrgDetailTab = OrgDetailTab.CONSENT

    /** 실 API 모드일 때만 존재. mock 모드면 null이라 서버 동기화·이력 조회가 전부 비활성화된다. */
    private val apiRepo: ApiOrganizationRepository? = RepositoryProvider.apiRepositoryOrNull()

    /**
     * 실 API 모드일 때만 동작하는 서버 동기화기.
     * 스위치 연타를 흡수해 마지막 상태만 PATCH로 전송하고, 성공 시 캐시를 무효화해 다음 조회에 서버값을 반영한다.
     * mock 모드면 apiRepo가 null이라 sync 자체가 생성되지 않는다.
     */
    private val consentSync: ConsentSyncManager? =
        apiRepo?.let { repo ->
            ConsentSyncManager(scope = viewModelScope) { consentItemId, enabled ->
                repo.patchConsent(consentItemId, enabled).also { repo.invalidateCache() }
            }
        }

    fun loadOrganization(orgId: String, initialTab: OrgDetailTab) {
        currentOrgId = orgId
        currentInitialTab = initialTab
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, activeTab = initialTab) }

            val baseDetail = try {
                repository.getOrganizationDetail(orgId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "기업 정보를 불러오지 못했습니다. 다시 시도해 주세요.")
                }
                return@launch
            }

            if (baseDetail == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "기업 정보를 찾을 수 없습니다.")
                }
                return@launch
            }

            ConsentStateStore.initialize(
                orgId = orgId,
                enabledIds = baseDetail.consentDetail.optionalConsents
                    .filter { it.enabled }
                    .map { it.id }
                    .toSet(),
            )

            // 실 API 모드: 동의 변경 내역 탭(4-7)은 2-8을 단일 소스로 쓴다(확정 사항 4번).
            // 아래 combine 블록은 API 모드에서는 changeHistory를 건드리지 않고 이 조회 결과를 유지한다.
            refreshConsentHistory(orgId)

            // 스위치 토글 시 위험도 점수·등급·분석 정보가 즉시 재산출된다. mock 모드에서는
            // 변경 기록도 ConsentStateStore의 로컬 이력을 그대로 함께 반영한다.
            combine(
                ConsentStateStore.enabledConsents,
                ConsentStateStore.changeHistory,
            ) { consentStates, history ->
                val recalculated =
                    RiskRecalculator.recalculate(baseDetail, consentStates[orgId].orEmpty())
                recalculated to history[orgId].orEmpty()
            }.collect { (recalculated, localHistory) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        detail = recalculated,
                        // API 모드면 refreshConsentHistory()가 채운 서버 이력을 유지하고,
                        // mock 모드면 로컬 이력을 그대로 쓴다.
                        changeHistory = if (apiRepo != null) it.changeHistory else localHistory,
                    )
                }
            }
        }
    }

    /**
     * 동의 변경 내역 탭(4-7)을 2-8(GET /users/{userId}/consents/history)로 채운다.
     * mock 모드(apiRepo == null)면 아무 것도 하지 않는다 — 그 경우 화면은 여전히
     * ConsentStateStore의 로컬 이력을 쓴다. 이력 조회 실패는 화면 전체를 막지 않고
     * 조용히 무시한다(재진입·재토글 시 다시 시도됨).
     */
    private fun refreshConsentHistory(orgId: String) {
        val repo = apiRepo ?: return
        viewModelScope.launch {
            try {
                val records = repo.getConsentHistory(orgId)
                _uiState.update { it.copy(changeHistory = records) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 무시 — 탭이 비어 보일 뿐 화면 전체 오류로 확대하지 않는다.
            }
        }
    }

    fun retry() {
        currentOrgId?.let { loadOrganization(it, currentInitialTab) }
    }

    fun toggleConsent(consentId: Int, enabled: Boolean) {
        val detail = _uiState.value.detail ?: return
        val orgId = detail.organization.id
        val title = detail.consentDetail.optionalConsents
            .firstOrNull { it.id == consentId }?.title ?: return
        // 화면은 클라이언트 재계산으로 즉시 반응
        ConsentStateStore.setConsent(orgId, consentId, enabled, title)
        // 실 API 모드면 서버에도 반영 (디바운스). mock 모드면 sync가 null이라 여기서 끝난다.
        consentSync?.onToggle(
            consentItemId = consentId,
            enabled = enabled,
            onSuccess = { response ->
                // 서버가 요청과 다른 상태를 돌려주면 서버를 정답으로 삼는다.
                if (response.checked != enabled) {
                    ConsentStateStore.correctConsent(orgId, consentId, response.checked)
                    showToggleMessage("서버에 반영된 상태로 맞췄습니다.")
                }
                // 서버 이력 테이블에 새 항목이 쌓였으니 변경 내역 탭도 최신화한다.
                refreshConsentHistory(orgId)
            },
            onError = {
                // 전송이 실패했으니 서버는 이전 상태 그대로다. 화면만 바뀐 채 두면
                // 다음 진입 때 조용히 되돌아가 더 혼란스러우므로, 지금 되돌리고 알린다.
                ConsentStateStore.correctConsent(orgId, consentId, !enabled)
                showToggleMessage("동의 변경을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.")
            },
        )
    }

    private fun showToggleMessage(message: String) {
        _uiState.update { it.copy(toggleMessage = message) }
    }

    /** 스낵바를 띄운 뒤 호출해 같은 메시지가 다시 뜨지 않게 한다. */
    fun consumeToggleMessage() {
        _uiState.update { it.copy(toggleMessage = null) }
    }

    fun selectTab(tab: OrgDetailTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    override fun onCleared() {
        consentSync?.cancelAll()
        super.onCleared()
    }
}
