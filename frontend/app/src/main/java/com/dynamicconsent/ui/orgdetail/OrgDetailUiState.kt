package com.dynamicconsent.ui.orgdetail

import com.dynamicconsent.data.model.ConsentChangeRecord
import com.dynamicconsent.data.model.OrganizationDetail

enum class OrgDetailTab(val label: String) {
    CONSENT("동의 세부 사항"),
    RISK("위험도"),
    THIRD_PARTY("제3자 제공"),
    CHANGE_HISTORY("동의 변경 내역"),
    INFO("정보"),
}

data class OrgDetailUiState(
    val isLoading: Boolean = false,
    val detail: OrganizationDetail? = null,
    val activeTab: OrgDetailTab = OrgDetailTab.CONSENT,
    val changeHistory: List<ConsentChangeRecord> = emptyList(),
    /** 상세 로드 실패 메시지. null이면 정상. */
    val error: String? = null,
    /**
     * 토글을 서버에 저장하면서 생긴 안내 메시지 (스낵바용). 한 번 보여준 뒤 비운다.
     * 화면 전체를 막는 [error]와 달리 상세 화면은 그대로 두고 알리기만 한다.
     */
    val toggleMessage: String? = null,
)
