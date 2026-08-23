package com.dynamicconsent.data.remote

import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.data.remote.dto.NoticeResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 약관 확인 기록 응답을 화면 모델로 변환한다.
 */
object NoticeMapper {

    /** 서버가 타임존 없는 KST 시각을 내려주므로 해석 기준을 명시한다. */
    private val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 확인 시각 내림차순(최신순)으로 반환한다.
     * 서버도 같은 순서로 주지만, mock 데이터나 페이지 병합에서 순서가 흐트러져도 화면이 흔들리지 않게 다시 정렬한다.
     * 시각을 해석할 수 없는 항목은 목록 전체를 잃지 않도록 그 항목만 건너뛴다.
     */
    fun toNotices(responses: List<NoticeResponse>): List<Notice> =
        responses
            .mapNotNull { item ->
                val millis = parseKstToEpochMillis(item.crawledAt) ?: return@mapNotNull null
                Notice(
                    companyId = item.companyId,
                    companyName = item.companyName,
                    checkedAtMillis = millis,
                    isChanged = item.isChanged,
                )
            }
            .sortedByDescending { it.checkedAtMillis }

    private fun parseKstToEpochMillis(crawledAt: String): Long? = try {
        LocalDateTime.parse(crawledAt)
            .atZone(SERVER_ZONE)
            .toInstant()
            .toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
