package com.dynamicconsent.data.model

/**
 * 위험기관리스트 카드 및 기업상세 헤더에 쓰이는 기관 요약 정보.
 */
data class Organization(
    val id: String,
    val name: String,
    val category: String,
    val riskScore: Double,
    val riskGrade: RiskGrade,
    val logoText: String,
    val logoColor: Long,
)
