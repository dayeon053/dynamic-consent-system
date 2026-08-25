package com.dynamicconsent.data.remote

import com.dynamicconsent.data.model.ConsentChangeRecord
import com.dynamicconsent.data.remote.dto.ConsentHistoryResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 동의 변경 이력(2-8) 응답을 화면 모델로 변환한다.
 * 4-7(동의 변경 내역 탭)의 단일 소스 — PATCH(2-3) 응답엔 changed_at이 없다
 * (api_spec_v2_final.md 확정 사항 4번).
 */
object ConsentHistoryMapper {

    /** 서버가 타임존 없는 KST 시각을 내려주므로 해석 기준을 명시한다(NoticeMapper와 동일 관례). */
    private val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 전체 기업 통합 이력 중 [companyId]에 해당하는 것만 걸러 최신순으로 반환한다.
     * 서버는 변경 시각 오름차순으로 주지만, 화면(최신 항목이 위)에 맞춰 뒤집는다.
     * 시각을 해석할 수 없는 항목은 목록 전체를 잃지 않도록 그 항목만 건너뛴다.
     */
    fun toChangeRecords(responses: List<ConsentHistoryResponse>, companyId: Long): List<ConsentChangeRecord> =
        responses
            .asSequence()
            .filter { it.companyId == companyId }
            .mapNotNull { item ->
                val millis = parseKstToEpochMillis(item.changedAt) ?: return@mapNotNull null
                ConsentChangeRecord(
                    consentTitle = item.itemName,
                    enabled = item.isChecked,
                    timestampMillis = millis,
                )
            }
            .sortedByDescending { it.timestampMillis }
            .toList()

    private fun parseKstToEpochMillis(changedAt: String): Long? = try {
        LocalDateTime.parse(changedAt)
            .atZone(SERVER_ZONE)
            .toInstant()
            .toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
