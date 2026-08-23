package com.dynamicconsent.data.model

/**
 * 공지사항 탭에 보여줄 약관 확인 기록 1건.
 *
 * ⚠️ [checkedAtMillis]는 **약관이 바뀐 시각이 아니라 서버가 약관을 확인한 시각**이다.
 * 서버는 매일 새벽 크롤링마다 내용이 그대로여도 확인 시각을 갱신한다.
 * 실제로 변경이 있었는지는 [isChanged]로 판단해야 하며, 화면 문구도 이 구분을 지켜야 한다.
 */
data class Notice(
    val companyId: Long,
    val companyName: String,
    val checkedAtMillis: Long,
    val isChanged: Boolean,
)
