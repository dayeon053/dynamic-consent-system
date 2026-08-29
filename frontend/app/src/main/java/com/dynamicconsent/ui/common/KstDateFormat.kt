package com.dynamicconsent.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 서버가 내려주는 시각을 KST로 고정해 표시한다.
 *
 * 기기 타임존으로 보여주면 순간 자체는 맞아도 서버가 말한 시각과 달라 보인다
 * (해외 로밍·에뮬레이터 등). 국내 서비스이고 서버 기준도 KST라 표시 기준을 서버에 맞춘다.
 */
private val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

private fun format(pattern: String, millis: Long): String =
    SimpleDateFormat(pattern, Locale.KOREA)
        .apply { timeZone = KST }
        .format(Date(millis))

/** 예: 2026.08.26 17:16 */
fun formatKstDateTime(millis: Long): String = format("yyyy.MM.dd HH:mm", millis)

/** 예: 26.08.26 — 목록에서 자리를 아껴야 할 때 */
fun formatKstShortDate(millis: Long): String = format("yy.MM.dd", millis)
