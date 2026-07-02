package com.dynamicconsent.data.model

/**
 * 화면 표시용 위험도 등급 (5단계).
 * 실제 산출 로직은 common-model의 위험도 알고리즘을 따르며,
 * 프론트는 서버 응답을 그대로 이 등급으로 매핑해서 사용한다.
 */
enum class RiskGrade(val displayName: String) {
    VERY_LOW("매우 안전"),
    LOW("안전"),
    MEDIUM("보통"),
    HIGH("위험"),
    VERY_HIGH("매우 위험"),
}
