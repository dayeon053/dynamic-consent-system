package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.ConsentItem
import com.dynamicconsent.data.model.RiskGrade

/**
 * 실제 API가 붙기 전까지 사용하는 임시 데이터 소스.
 * TODO: 실제 API 연동 구현체로 교체
 */
class DummyConsentRepository : ConsentRepository {

    private val items = listOf(
        ConsentItem(
            id = "1",
            serviceName = "예시 서비스 A",
            category = "쇼핑",
            riskScore = 12.5,
            riskGrade = RiskGrade.LOW,
            description = "임시 더미 데이터입니다.",
        ),
        ConsentItem(
            id = "2",
            serviceName = "예시 서비스 B",
            category = "SNS",
            riskScore = 28.0,
            riskGrade = RiskGrade.HIGH,
            description = "임시 더미 데이터입니다.",
        ),
        ConsentItem(
            id = "3",
            serviceName = "예시 서비스 C",
            category = "금융",
            riskScore = 19.0,
            riskGrade = RiskGrade.MEDIUM,
            description = "임시 더미 데이터입니다.",
        ),
    )

    override suspend fun getConsentItems(): List<ConsentItem> = items

    override suspend fun getConsentItem(id: String): ConsentItem? = items.find { it.id == id }
}
