package com.dynamicconsent.data.model

/**
 * 기업상세 화면 전체를 구성하는 데이터 묶음.
 */
data class OrganizationDetail(
    val organization: Organization,
    val consentDetail: ConsentDetail,
    val riskAnalysis: RiskAnalysis,
    val companyInfo: CompanyInfo,
)
