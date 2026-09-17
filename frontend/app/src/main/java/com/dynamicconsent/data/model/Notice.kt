package com.dynamicconsent.data.model

/**
 * 공지사항 탭에 보여줄 약관 변경 기록 1건.
 *
 * 서버(GET /notices)는 **실제로 변경이 있었던 건만** 내려준다
 * (api_spec_v2_final.md 확정 사항 1번, 2026-08-25). 변경이 없는 날은 새 레코드가 생기지 않고
 * 기존 최신 레코드의 확인 시각만 갱신되며, 그런 레코드는 목록에 오지 않는다.
 *
 * ⚠️ [checkedAtMillis]는 서버가 약관을 **확인한** 시각이다. 이 목록의 항목은 모두 변경 건이므로
 * 실질적으로 "그 변경을 확인한 시각"이지만, 변경이 일어난 정확한 시각은 아니다.
 *
 * [isChanged]는 서버가 필터를 걸기 전 형태와 직렬화 사고(`isChanged` → `changed`)까지 감안해
 * 그대로 들고 있는다. 정상 서버에서는 항상 true다.
 */
data class Notice(
    val companyId: Long,
    val companyName: String,
    val checkedAtMillis: Long,
    val isChanged: Boolean,
)
