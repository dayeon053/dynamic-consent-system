package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 기업상세 '제3자 제공' 탭에 표시되는 개인정보 제공처 정보.
 */
@Serializable
data class ThirdPartyProvider(
    val name: String,
    val purpose: String,
    val sharedItems: String,
    val retentionPeriod: String,
)
