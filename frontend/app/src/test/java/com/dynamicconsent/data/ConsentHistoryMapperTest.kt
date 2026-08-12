package com.dynamicconsent.data

import com.dynamicconsent.data.remote.ConsentHistoryMapper
import com.dynamicconsent.data.remote.dto.ConsentHistoryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 서버는 전체 기업 이력을 오름차순으로 내려주므로,
 * 기업별 필터·최신순 정렬·KST 시각 해석이 매퍼에서 올바르게 되는지 검증한다.
 */
class ConsentHistoryMapperTest {

    private fun item(
        companyId: Long,
        itemName: String,
        isChecked: Boolean,
        changedAt: String,
        consentItemId: Long = 1,
    ) = ConsentHistoryResponse(
        consentItemId = consentItemId,
        itemName = itemName,
        companyId = companyId,
        companyName = "기관$companyId",
        isChecked = isChecked,
        changedAt = changedAt,
    )

    @Test
    fun `요청한 기업의 이력만 골라낸다`() {
        val responses = listOf(
            item(1, "카카오 마케팅 동의", true, "2026-07-29T10:15:00"),
            item(2, "토스 신용정보 동의", false, "2026-07-29T11:00:00"),
            item(1, "카카오 위치정보 동의", false, "2026-07-29T12:00:00"),
        )

        val records = ConsentHistoryMapper.toRecords(responses, orgId = "1")

        assertEquals(listOf("카카오 위치정보 동의", "카카오 마케팅 동의"), records.map { it.consentTitle })
    }

    @Test
    fun `서버의 오름차순 이력을 최신순으로 뒤집는다`() {
        val responses = listOf(
            item(1, "먼저", true, "2026-07-29T10:00:00"),
            item(1, "나중", false, "2026-07-29T18:30:00"),
        )

        val records = ConsentHistoryMapper.toRecords(responses, orgId = "1")

        assertEquals(listOf("나중", "먼저"), records.map { it.consentTitle })
        assertTrue(records[0].timestampMillis > records[1].timestampMillis)
    }

    @Test
    fun `타임존 없는 시각을 KST로 해석한다`() {
        val responses = listOf(item(1, "마케팅 동의", true, "2026-07-29T10:15:00"))

        val records = ConsentHistoryMapper.toRecords(responses, orgId = "1")

        val expected = LocalDateTime.of(2026, 7, 29, 10, 15, 0)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, records.single().timestampMillis)
    }

    @Test
    fun `isChecked가 동의 여부로 그대로 매핑된다`() {
        val responses = listOf(
            item(1, "켬", true, "2026-07-29T10:00:00"),
            item(1, "끔", false, "2026-07-29T11:00:00"),
        )

        val records = ConsentHistoryMapper.toRecords(responses, orgId = "1").associate {
            it.consentTitle to it.enabled
        }

        assertEquals(true, records.getValue("켬"))
        assertEquals(false, records.getValue("끔"))
    }

    @Test
    fun `시각을 해석할 수 없는 항목만 건너뛰고 나머지는 유지한다`() {
        val responses = listOf(
            item(1, "정상", true, "2026-07-29T10:00:00"),
            item(1, "깨진 시각", false, "not-a-timestamp"),
        )

        val records = ConsentHistoryMapper.toRecords(responses, orgId = "1")

        assertEquals(listOf("정상"), records.map { it.consentTitle })
    }

    @Test
    fun `해당 기업 이력이 없으면 빈 목록을 반환한다`() {
        val responses = listOf(item(2, "다른 기업", true, "2026-07-29T10:00:00"))

        assertTrue(ConsentHistoryMapper.toRecords(responses, orgId = "1").isEmpty())
    }
}
