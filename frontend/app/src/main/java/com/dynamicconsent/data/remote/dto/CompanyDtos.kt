package com.dynamicconsent.data.remote.dto

import com.dynamicconsent.data.model.RiskGrade
import kotlinx.serialization.Serializable

/*
 * 백엔드 API 응답 DTO.
 * 실제 Controller(ConsentApiController)와 응답 DTO(CompanyRiskResponse, ConsentItemResponse,
 * ConsentPatchResponse) 기준으로 확정. Spring/Jackson 기본 직렬화라 필드명은 camelCase다.
 * 동의 항목은 GET /companies가 아니라 GET /companies/{id}/consent-items로 별도 조회한다.
 */

/** GET /companies?userId=&sort=risk_score_desc 응답 항목 (backend CompanyRiskResponse 대응) */
@Serializable
data class CompanyResponse(
    val companyId: Long,
    val companyName: String,
    val packageName: String? = null,
    val privacyUrl: String? = null,
    val ismsCertified: Boolean = false,
    /** 개인 맞춤 위험도(필수동의 + 사용자 체크 선택동의 기준). 산출 전이면 null */
    val riskScore: Double? = null,
    val riskGrade: RiskGrade? = null,
)

/**
 * GET /companies/{companyId}/consent-items?userId= 응답 항목 (backend ConsentItemResponse 대응).
 * itemType은 ConsentItem.ItemType enum이 문자열("REQUIRED"/"OPTIONAL")로 직렬화된 값.
 * REQUIRED는 checked 항상 true, OPTIONAL은 이 사용자의 실제 체크 여부.
 */
@Serializable
data class ConsentItemResponse(
    val consentItemId: Long,
    val itemName: String,
    val itemType: String,
    val checked: Boolean = true,
    val dsScore: Int = 1,
    val esScore: Int = 1,
    val tfScore: Int = 1,
    val pcScore: Double = 1.0,
    val aiScore: Double = 1.0,
) {
    companion object {
        const val TYPE_REQUIRED = "REQUIRED"
        const val TYPE_OPTIONAL = "OPTIONAL"
    }
}

/**
 * PATCH /users/{userId}/consents/{consentItemId} 응답 (backend ConsentPatchResponse 대응).
 * 요청 본문은 없다 — 서버가 현재 상태를 반전(토글)시키고 결과를 돌려주는 방식.
 * 화면 상태와 어긋나지 않도록 응답의 [checked]를 신뢰 기준으로 삼아 보정해야 한다.
 */
@Serializable
data class ConsentPatchResponse(
    val consentItemId: Long,
    val checked: Boolean,
    /** 동의 항목이 하나도 없는 기업의 경우 서버가 null을 반환할 수 있다. */
    val newRiskScore: Double? = null,
    val newRiskGrade: RiskGrade? = null,
)
