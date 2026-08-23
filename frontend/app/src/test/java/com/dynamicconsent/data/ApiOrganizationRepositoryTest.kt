package com.dynamicconsent.data

import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.model.RiskVariables
import com.dynamicconsent.data.remote.ConsentRadarApi
import com.dynamicconsent.data.repository.ApiOrganizationRepository
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
 * MockWebServer로 백엔드 응답을 흉내 내어 DTO 파싱·매핑·요청 형식을 검증한다.
 * 응답 형식은 실제 Controller(ConsentApiController) 기준 (Jackson 기본 camelCase).
 * 기업 요약(GET /companies)과 동의 항목(GET /companies/{id}/consent-items)이 별도 응답.
 */
class ApiOrganizationRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ApiOrganizationRepository

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
        repository = ApiOrganizationRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val companiesJson = """
        [
          { "companyId": 1, "companyName": "카카오톡", "legalName": "(주)카카오", "category": "SNS",
            "packageName": "com.kakao.talk",
            "privacyUrl": "https://www.kakao.com/policy/privacy", "ismsCertified": true,
            "riskScore": 43.5, "riskGrade": "VERY_HIGH" },
          { "companyId": 2, "companyName": "토스", "ismsCertified": false,
            "riskScore": 32.0, "riskGrade": "HIGH" }
        ]
    """.trimIndent()

    private val kakaoItemsJson = """
        [
          { "consentItemId": 11, "itemName": "이용약관 동의", "itemType": "REQUIRED", "checked": true,
            "dsScore": 1, "esScore": 1, "tfScore": 1, "pcScore": 1.0, "aiScore": 1.0 },
          { "consentItemId": 12, "itemName": "맞춤형 광고 동의", "itemType": "OPTIONAL", "checked": true,
            "dsScore": 3, "esScore": 3, "tfScore": 3, "pcScore": 1.5, "aiScore": 1.5 }
        ]
    """.trimIndent()

    private val tossItemsJson = """
        [
          { "consentItemId": 21, "itemName": "신용정보 활용 동의", "itemType": "OPTIONAL", "checked": true,
            "dsScore": 5, "esScore": 2, "tfScore": 3, "pcScore": 1.5, "aiScore": 1.0 }
        ]
    """.trimIndent()

    /** getCompanies 1건 + 각 기업의 consent-items를 순서대로 응답하도록 큐잉 */
    private fun enqueueAll() {
        server.enqueue(MockResponse().setBody(companiesJson))
        server.enqueue(MockResponse().setBody(kakaoItemsJson))
        server.enqueue(MockResponse().setBody(tossItemsJson))
    }

    @Test
    fun `기업 목록을 파싱해 위험도 내림차순으로 반환한다`() = runTest {
        enqueueAll()

        val organizations = repository.getOrganizations()

        assertEquals(listOf("카카오톡", "토스"), organizations.map { it.name })
        assertEquals(43.5, organizations[0].riskScore, 0.0)
        assertEquals(RiskGrade.VERY_HIGH, organizations[0].riskGrade)
        // category는 서버 값, 로고는 프론트 매핑표에서 온다
        assertEquals("SNS", organizations[0].category)
        assertEquals("톡", organizations[0].logoText)
        // category를 안 준 기업은 기타로 떨어진다
        assertEquals("기타", organizations[1].category)

        val companiesReq = server.takeRequest()
        assertTrue(companiesReq.path!!.startsWith("/companies?"))
        assertTrue(companiesReq.path!!.contains("userId=1"))
        assertTrue(companiesReq.path!!.contains("sort=risk_score_desc"))
    }

    @Test
    fun `상세는 consent-items를 별도 호출해 변수 기여도와 함께 매핑한다`() = runTest {
        enqueueAll()

        val detail = repository.getOrganizationDetail("1")!!

        assertEquals(1, detail.consentDetail.requiredConsents.size)
        assertEquals(1, detail.consentDetail.optionalConsents.size)
        val optional = detail.consentDetail.optionalConsents.first()
        assertEquals("맞춤형 광고 동의", optional.title)
        assertEquals(RiskVariables(ds = 3, es = 3, tf = 3, pc = 1.5, ai = 1.5), optional.variableImpact)
        assertEquals("ISMS-P", detail.companyInfo.privacyCertification)
        assertEquals("(주)카카오", detail.companyInfo.legalName)
        assertEquals("카카오톡", detail.companyInfo.serviceName)
        assertEquals("최대 40.5점 감소", detail.riskAnalysis.maxEffect.totalReduction)

        // 요청 순서: /companies → /companies/1/consent-items → /companies/2/consent-items
        server.takeRequest()
        val itemsReq = server.takeRequest()
        assertTrue(itemsReq.path!!.startsWith("/companies/1/consent-items"))
        assertTrue(itemsReq.path!!.contains("userId=1"))
    }

    @Test
    fun `동의 변경 PATCH는 원하는 상태를 본문에 담아 전송된다`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{ "consentItemId": 12, "checked": false, "newRiskScore": 15.0, "newRiskGrade": "MEDIUM" }""",
            ),
        )

        val response = repository.patchConsent(consentItemId = 12, checked = false)

        assertEquals(12L, response.consentItemId)
        assertEquals(false, response.checked)
        assertEquals(15.0, response.newRiskScore!!, 0.0)
        assertEquals(RiskGrade.MEDIUM, response.newRiskGrade)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/users/1/consents/12", request.path)
        // 서버가 맹목적으로 반전하지 않도록 원하는 상태를 명시한다
        assertEquals("""{"checked":false}""", request.body.readUtf8())
    }

    @Test
    fun `같은 상태로 반복 호출해도 동일한 본문이 전송된다 (멱등)`() = runTest {
        val body = """{ "consentItemId": 12, "checked": true, "newRiskScore": 43.5, "newRiskGrade": "VERY_HIGH" }"""
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody(body))

        repository.patchConsent(consentItemId = 12, checked = true)
        repository.patchConsent(consentItemId = 12, checked = true)

        // 토글 방식이었다면 두 번째 요청이 상태를 되돌렸겠지만,
        // 원하는 상태를 보내므로 몇 번을 보내도 결과가 같다.
        assertEquals("""{"checked":true}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"checked":true}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `동의 변경 이력은 명세 경로로 조회해 해당 기업 것만 최신순으로 반환한다`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "consentItemId": 12, "itemName": "맞춤형 광고 동의", "companyId": 1,
                    "companyName": "카카오톡", "isChecked": true, "changedAt": "2026-07-29T10:15:00" },
                  { "consentItemId": 21, "itemName": "신용정보 활용 동의", "companyId": 2,
                    "companyName": "토스", "isChecked": false, "changedAt": "2026-07-29T11:00:00" },
                  { "consentItemId": 12, "itemName": "맞춤형 광고 동의", "companyId": 1,
                    "companyName": "카카오톡", "isChecked": false, "changedAt": "2026-07-29T12:30:00" }
                ]
                """.trimIndent(),
            ),
        )

        val records = repository.getConsentHistory(orgId = "1")

        assertEquals(2, records.size)
        assertEquals(false, records[0].enabled)
        assertEquals(true, records[1].enabled)
        assertTrue(records[0].timestampMillis > records[1].timestampMillis)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/users/1/consents/history", request.path)
    }

    @Test
    fun `이력이 비어 있으면 빈 목록을 반환한다`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        assertTrue(repository.getConsentHistory(orgId = "1").isEmpty())
    }
}
