package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.ConsentChangeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 기관별 선택동의 체크 상태(동의 중인 항목 id 집합)를 앱 전역에서 공유하는 인메모리 저장소.
 * 기업상세에서 스위치를 토글하면 위험기관리스트가 같은 상태를 보고 즉시 재정렬되고,
 * 토글 이력은 '동의 변경 내역' 탭에 표시할 수 있도록 기관별로 기록된다.
 * TODO: 실제 API 연동 시 서버의 사용자 체크 현황과 동기화하는 구현으로 교체
 */
object ConsentStateStore {

    private const val MAX_HISTORY_PER_ORG = 50

    private val _enabledConsents = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())

    /** orgId → 동의 중인 선택동의 항목 id 집합 */
    val enabledConsents: StateFlow<Map<String, Set<Int>>> = _enabledConsents.asStateFlow()

    private val _changeHistory = MutableStateFlow<Map<String, List<ConsentChangeRecord>>>(emptyMap())

    /** orgId → 동의 변경 기록 (최신순) */
    val changeHistory: StateFlow<Map<String, List<ConsentChangeRecord>>> = _changeHistory.asStateFlow()

    /** 아직 상태가 없는 기관만 초기값(mock JSON의 enabled)을 등록한다. */
    fun initialize(orgId: String, enabledIds: Set<Int>) {
        _enabledConsents.update { current ->
            if (orgId in current) current else current + (orgId to enabledIds)
        }
    }

    /**
     * 서버 전송 결과에 맞춰 체크 상태만 바로잡는다. **변경 이력은 남기지 않는다.**
     *
     * 사용자가 직접 누른 것이 아니라 전송 실패를 되돌리거나 서버 응답에 맞추는 보정이라,
     * 이력에 남기면 '동의 변경 내역'에 사용자가 하지 않은 기록이 쌓인다.
     */
    fun correctConsent(orgId: String, consentId: Int, enabled: Boolean) {
        _enabledConsents.update { current ->
            val ids = current[orgId].orEmpty()
            current + (orgId to if (enabled) ids + consentId else ids - consentId)
        }
    }

    fun setConsent(orgId: String, consentId: Int, enabled: Boolean, consentTitle: String) {
        _enabledConsents.update { current ->
            val ids = current[orgId].orEmpty()
            current + (orgId to if (enabled) ids + consentId else ids - consentId)
        }
        _changeHistory.update { current ->
            val record = ConsentChangeRecord(
                consentTitle = consentTitle,
                enabled = enabled,
                timestampMillis = System.currentTimeMillis(),
            )
            val records = (listOf(record) + current[orgId].orEmpty()).take(MAX_HISTORY_PER_ORG)
            current + (orgId to records)
        }
    }

    /** 단위 테스트 간 상태 격리용. */
    fun reset() {
        _enabledConsents.value = emptyMap()
        _changeHistory.value = emptyMap()
    }
}
