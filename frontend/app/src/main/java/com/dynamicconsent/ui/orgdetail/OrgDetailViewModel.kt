package com.dynamicconsent.ui.orgdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    /** 실 API 모드일 때만 존재한다 (mock 모드면 null). 서버 전송·이력 조회에 쓴다. */
    private val apiRepository = RepositoryProvider.apiRepositoryOrNull()

    /**
     * 실 API 모드일 때만 동작하는 서버 동기화기.
     * 스위치 연타를 흡수해 마지막 상태만 PATCH로 전송하고, 성공 시 캐시를 무효화해 다음 조회에 서버값을 반영한다.
     * mock 모드면 apiRepository가 null이라 sync 자체가 생성되지 않는다.
     */
    private val consentSync: ConsentSyncManager? =
        apiRepository?.let { apiRepo ->
            ConsentSyncManager(scope = viewModelScope) { consentItemId, enabled ->
                apiRepo.patchConsent(consentItemId, enabled).also { apiRepo.invalidateCache() }
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

            // 실 API 모드면 서버에 쌓인 변경 이력을 채운다 (앱을 다시 켜도 이력이 남도록).
            // 이력 조회가 실패해도 상세 화면 자체는 그대로 보여준다 — 부가 정보이기 때문.
            // runCatching은 CancellationException까지 삼켜 화면을 벗어나도 코루틴이 계속 진행되므로 쓰지 않는다.
            apiRepository?.let { apiRepo ->
                try {
                    ConsentStateStore.seedHistory(orgId, apiRepo.getConsentHistory(orgId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 이력 없이 상세만 보여준다
                }
            }

            // 스위치 토글 시 위험도 점수·등급·분석 정보가 즉시 재산출되고, 변경 기록도 함께 갱신된다.
            combine(
                ConsentStateStore.enabledConsents,
                ConsentStateStore.changeHistory,
            ) { consentStates, history ->
                val recalculated =
                    RiskRecalculator.recalculate(baseDetail, consentStates[orgId].orEmpty())
                recalculated to history[orgId].orEmpty()
            }.collect { (recalculated, history) ->
                _uiState.update {
                    it.copy(isLoading = false, error = null, detail = recalculated, changeHistory = history)
                }
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
