package com.dynamicconsent.data

import com.dynamicconsent.data.remote.ConsentRadarApi
import com.dynamicconsent.data.repository.ApiNoticeRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * GET /notices 응답 파싱과 요청 형식을 검증한다.
 * 응답 예시는 api_spec.md 2-5의 실 서버 호출 결과 기준.
 */
class ApiNoticeRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ApiNoticeRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(ConsentRadarApi::class.java)
        repository = ApiNoticeRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `공지 목록을 파싱해 최신순으로 반환한다`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "companyId": 5, "companyName": "당근마켓", "crawledAt": "2026-07-30T20:14:21.473038", "isChanged": false },
                  { "companyId": 1, "companyName": "카카오", "crawledAt": "2026-07-30T20:13:51.296765", "isChanged": true }
                ]
                """.trimIndent(),
            ),
        )

        val notices = repository.getNotices()

        assertEquals(listOf("당근마켓", "카카오"), notices.map { it.companyName })
        assertEquals(5L, notices[0].companyId)
        assertTrue(notices[1].isChanged)
        assertTrue(notices[0].checkedAtMillis > notices[1].checkedAtMillis)
    }

    @Test
    fun `명세대로 page와 size를 붙여 요청한다`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        repository.getNotices(page = 2, size = 10)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/notices?"))
        assertTrue(request.path!!.contains("page=2"))
        assertTrue(request.path!!.contains("size=10"))
    }

    @Test
    fun `isChanged가 없으면 변경 없음으로 본다`() = runTest {
        // 백엔드가 @JsonProperty를 빠뜨려 "changed"로 내려보내는 경우에도 앱이 죽지 않아야 한다
        server.enqueue(
            MockResponse().setBody(
                """[{ "companyId": 1, "companyName": "카카오", "crawledAt": "2026-07-30T20:13:51", "changed": true }]""",
            ),
        )

        val notices = repository.getNotices()

        assertEquals(1, notices.size)
        assertEquals(false, notices.single().isChanged)
    }

    @Test
    fun `빈 배열이면 빈 목록을 반환한다`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        assertTrue(repository.getNotices().isEmpty())
    }
}
