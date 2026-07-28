package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 위험기관리스트 카드 및 기업상세 헤더에 쓰이는 기관 요약 정보.
 */
@Serializable
data class Organization(
    val id: String,
    /**
     * 이 기관 앱의 안드로이드 패키지명 (예: com.kakao.talk).
     * 앱 실행 감지 시 패키지명 → 기관 id 매핑의 근거가 된다 (WatchedAppRegistry).
     * 서버가 등록하지 않은 기업은 null이며, 그런 기업은 감시 대상에서 제외된다.
     */
    val packageName: String? = null,
    val name: String,
    val category: String,
    val riskScore: Double,
    val riskGrade: RiskGrade,
    val logoText: String,
    val logoColor: Long,
)
