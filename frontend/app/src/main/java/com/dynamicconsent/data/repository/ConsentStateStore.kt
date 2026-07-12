package com.dynamicconsent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 기관별 선택동의 체크 상태(동의 중인 항목 id 집합)를 앱 전역에서 공유하는 인메모리 저장소.
 * 기업상세에서 스위치를 토글하면 위험기관리스트가 같은 상태를 보고 즉시 재정렬된다.
 * TODO: 실제 API 연동 시 서버의 사용자 체크 현황과 동기화하는 구현으로 교체
 */
object ConsentStateStore {

    private val _enabledConsents = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())

    /** orgId → 동의 중인 선택동의 항목 id 집합 */
    val enabledConsents: StateFlow<Map<String, Set<Int>>> = _enabledConsents.asStateFlow()

    /** 아직 상태가 없는 기관만 초기값(mock JSON의 enabled)을 등록한다. */
    fun initialize(orgId: String, enabledIds: Set<Int>) {
        _enabledConsents.update { current ->
            if (orgId in current) current else current + (orgId to enabledIds)
        }
    }

    fun setConsent(orgId: String, consentId: Int, enabled: Boolean) {
        _enabledConsents.update { current ->
            val ids = current[orgId].orEmpty()
            current + (orgId to if (enabled) ids + consentId else ids - consentId)
        }
    }

    /** 단위 테스트 간 상태 격리용. */
    fun reset() {
        _enabledConsents.value = emptyMap()
    }
}
