package com.dynamicconsent.data.model

/**
 * 사용자가 동의한 개인정보 처리 항목 하나(= 특정 서비스/기업)를 나타내는 모델.
 */
data class ConsentItem(
    val id: String,
    val serviceName: String,
    val category: String,
    val riskScore: Double,
    val riskGrade: RiskGrade,
    val description: String,
)
