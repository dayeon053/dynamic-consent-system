package com.dynamicconsent.data.remote.dto

import com.dynamicconsent.data.model.RiskGrade
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * 백엔드 API 응답/요청 DTO.
 * 필드 구성은 backend 엔티티(Company, ConsentItem, RiskScore)와 Sprint 02 계획의
 * 엔드포인트 명세를 기준으로 작성했다. Controller 확정 시 @SerialName만 맞추면 된다.
 */

/** GET /companies?user_id=&sort=risk_score_desc 응답 항목 */
@Serializable
data class CompanyResponse(
    @SerialName("company_id") val companyId: Long,
    @SerialName("company_name") val companyName: String,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("privacy_url") val privacyUrl: String? = null,
    @SerialName("isms_certified") val ismsCertified: Boolean = false,
    /** 기업 대표 위험도 (RiskScore.is_representative=true 값) */
    @SerialName("risk_score") val riskScore: Double? = null,
    val grade: RiskGrade? = null,
    @SerialName("consent_items") val consentItems: List<ConsentItemResponse> = emptyList(),
)

/** 동의 항목 + 5대 변수 + 사용자 체크 현황 */
@Serializable
data class ConsentItemResponse(
    @SerialName("consent_item_id") val consentItemId: Long,
    @SerialName("item_type") val itemType: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("ds_score") val dsScore: Int = 1,
    @SerialName("es_score") val esScore: Int = 1,
    @SerialName("tf_score") val tfScore: Int = 1,
    @SerialName("pc_score") val pcScore: Double = 1.0,
    @SerialName("ai_score") val aiScore: Double = 1.0,
    /** 사용자의 현재 체크 상태 (UserConsentCheck) */
    @SerialName("is_checked") val isChecked: Boolean = true,
) {
    companion object {
        const val TYPE_REQUIRED = "REQUIRED"
        const val TYPE_OPTIONAL = "OPTIONAL"
    }
}

/** PATCH /users/{user_id}/consents/{consent_item_id} 요청 본문 */
@Serializable
data class ConsentPatchRequest(
    @SerialName("is_checked") val isChecked: Boolean,
)

/** PATCH 응답 — 서버가 재산출한 위험도 */
@Serializable
data class ConsentPatchResponse(
    @SerialName("new_risk_score") val newRiskScore: Double,
    val grade: RiskGrade? = null,
)
