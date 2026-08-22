package com.dynamicconsent.data

import com.dynamicconsent.data.remote.NoticeMapper
import com.dynamicconsent.data.remote.dto.NoticeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NoticeMapperTest {

    private fun response(
        companyId: Long = 1,
        companyName: String = "카카오",
        crawledAt: String = "2026-07-30T20:14:21.473038",
        isChanged: Boolean = false,
    ) = NoticeResponse(
        companyId = companyId,
        companyName = companyName,
        crawledAt = crawledAt,
        isChanged = isChanged,
    )

    @Test
    fun `소수점 이하 초가 붙은 시각을 KST로 해석한다`() {
        val notices = NoticeMapper.toNotices(listOf(response()))

        val expected = LocalDateTime.of(2026, 7, 30, 20, 14, 21, 473_038_000)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, notices.single().checkedAtMillis)
    }

    @Test
    fun `소수점 없는 시각도 해석한다`() {
        val notices = NoticeMapper.toNotices(listOf(response(crawledAt = "2026-07-30T20:14:21")))

        val expected = LocalDateTime.of(2026, 7, 30, 20, 14, 21)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, notices.single().checkedAtMillis)
    }

    @Test
    fun `확인 시각 내림차순으로 정렬한다`() {
        val notices = NoticeMapper.toNotices(
            listOf(
                response(companyId = 1, companyName = "먼저", crawledAt = "2026-07-29T10:00:00"),
                response(companyId = 2, companyName = "나중", crawledAt = "2026-07-30T09:00:00"),
            ),
        )

        assertEquals(listOf("나중", "먼저"), notices.map { it.companyName })
    }

    @Test
    fun `isChanged를 그대로 옮긴다`() {
        val notices = NoticeMapper.toNotices(
            listOf(
                response(companyId = 1, companyName = "바뀜", isChanged = true, crawledAt = "2026-07-30T10:00:00"),
                response(companyId = 2, companyName = "안바뀜", isChanged = false, crawledAt = "2026-07-29T10:00:00"),
            ),
        )

        assertTrue(notices[0].isChanged)
        assertFalse(notices[1].isChanged)
    }

    @Test
    fun `해석할 수 없는 시각만 건너뛰고 나머지는 유지한다`() {
        val notices = NoticeMapper.toNotices(
            listOf(
                response(companyId = 1, companyName = "정상", crawledAt = "2026-07-30T10:00:00"),
                response(companyId = 2, companyName = "깨짐", crawledAt = "not-a-timestamp"),
            ),
        )

        assertEquals(listOf("정상"), notices.map { it.companyName })
    }

    @Test
    fun `빈 응답은 빈 목록이 된다`() {
        assertTrue(NoticeMapper.toNotices(emptyList()).isEmpty())
    }
}
