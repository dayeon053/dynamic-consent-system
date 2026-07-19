package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 기업상세 '정보' 탭에 표시되는 사업자 정보.
 */
@Serializable
data class CompanyInfo(
    val serviceName: String,
    val legalName: String,
    val privacyCertification: String,
    val privacyPolicyUrl: String,
)
