package com.dynamicconsent.data.remote

import com.dynamicconsent.data.model.ConsentChangeRecord
import com.dynamicconsent.data.remote.dto.ConsentHistoryResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 서버 동의 변경 이력을 화면 모델로 변환한다.
 *
 * 서버는 이 사용자의 **모든 기업 이력**을 변경 시각 오름차순으로 한 번에 내려주므로,
 * 기업별 필터와 최신순 정렬을 여기서 처리한다(화면은 최신 항목을 위에 보여준다).
 */
object ConsentHistoryMapper {

    /** 서버가 타임존 없는 KST 시각을 내려주므로 해석 기준을 명시한다. */
    private val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * [orgId]에 해당하는 기업의 이력만 골라 최신순으로 반환한다.
     * 시각을 해석할 수 없는 항목은 목록 전체를 잃지 않도록 건너뛴다.
     */
    fun toRecords(responses: List<ConsentHistoryResponse>, orgId: String): List<ConsentChangeRecord> =
        responses
            .filter { it.companyId.toString() == orgId }
            .mapNotNull { item ->
                val millis = parseKstToEpochMillis(item.changedAt) ?: return@mapNotNull null
                ConsentChangeRecord(
                    consentTitle = item.itemName,
                    enabled = item.isChecked,
                    timestampMillis = millis,
                )
            }
            .sortedByDescending { it.timestampMillis }

    private fun parseKstToEpochMillis(changedAt: String): Long? = try {
        LocalDateTime.parse(changedAt)
            .atZone(SERVER_ZONE)
            .toInstant()
            .toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
