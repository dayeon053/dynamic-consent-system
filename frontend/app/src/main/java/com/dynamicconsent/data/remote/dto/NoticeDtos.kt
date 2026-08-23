package com.dynamicconsent.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET /notices?page=&size= 응답 항목 (backend NoticeResponse 대응).
 *
 * 필드명 주의: 백엔드가 getter에 `@JsonProperty("isChanged")`를 붙여 `isChanged`로 고정했다.
 * 이걸 빼면 Jackson이 "is"를 벗겨 `changed`로 내려보내므로, 서버 쪽 수정 시 같이 확인해야 한다.
 */
@Serializable
data class NoticeResponse(
    val companyId: Long,
    val companyName: String,
    /**
     * 약관을 **확인한** 시각. 타임존 표기가 없는 KST이며 소수점 이하 초가 붙는다
     * (예: "2026-07-30T20:14:21.473038"). 변경 시각이 아니다 — [isChanged]와 함께 읽어야 한다.
     */
    val crawledAt: String,
    val isChanged: Boolean = false,
)
