package com.dynamicconsent.data.remote.dto

import com.dynamicconsent.data.model.RiskGrade
import kotlinx.serialization.Serializable

/*
 * 백엔드 API 응답 DTO.
 * PR #18의 실제 Controller(ConsentApiController)와 응답 DTO(CompanyRiskResponse,
 * ConsentPatchResponse) 기준으로 확정했다. Spring/Jackson 기본 직렬화라 필드명은 camelCase다.
 */

/** GET /companies?userId=&sort=risk_score_desc 응답 항목 (backend CompanyRiskResponse 대응) */
@Serializable
data class CompanyResponse(
    val companyId: Long,
    val companyName: String,
    val packageName: String? = null,
    val privacyUrl: String? = null,
    val ismsCertified: Boolean = false,
    /** 기업 대표 위험도 (RiskScore.is_representative=true). 산출 전이면 null */
    val riskScore: Double? = null,
    val riskGrade: RiskGrade? = null,
    /**
     * 동의 항목 목록 (5대 변수 + 사용자 체크 상태).
     * 현재 백엔드 응답에는 아직 없어 기본값 emptyList로 파싱된다.
     * 기업상세 화면에 필요해 백엔드에 추가 요청 중 (PR #18 코멘트 참고).
     */
    val consentItems: List<ConsentItemResponse> = emptyList(),
)

/** 동의 항목 + 5대 변수 + 사용자 체크 현황 (백엔드 추가 예정 스펙) */
@Serializable
data class ConsentItemResponse(
    val consentItemId: Long,
    val itemType: String,
    val itemName: String,
    val dsScore: Int = 1,
    val esScore: Int = 1,
    val tfScore: Int = 1,
    val pcScore: Double = 1.0,
    val aiScore: Double = 1.0,
    /** 사용자의 현재 체크 상태 (UserConsentCheck) */
    val checked: Boolean = true,
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
