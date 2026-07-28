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
 * 응답 형식은 PR #18의 실제 Controller(ConsentApiController) 기준 (Jackson 기본 camelCase).
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

    /** consentItems는 백엔드 추가 예정 스펙 — 포함된 경우와 없는 경우(토스) 모두 검증 */
    private val companiesJson = """
        [
          {
            "companyId": 1,
            "companyName": "카카오톡",
            "packageName": "com.kakao.talk",
            "privacyUrl": "https://www.kakao.com/policy/privacy",
            "ismsCertified": true,
            "riskScore": 43.5,
            "riskGrade": "VERY_HIGH",
            "consentItems": [
              { "consentItemId": 11, "itemType": "REQUIRED", "itemName": "이용약관 동의",
                "dsScore": 1, "esScore": 1, "tfScore": 1, "pcScore": 1.0, "aiScore": 1.0 },
              { "consentItemId": 12, "itemType": "OPTIONAL", "itemName": "맞춤형 광고 동의",
                "dsScore": 3, "esScore": 3, "tfScore": 3, "pcScore": 1.5, "aiScore": 1.5,
                "checked": true }
            ]
          },
          {
            "companyId": 2,
            "companyName": "토스",
            "ismsCertified": false,
            "riskScore": 32.0,
            "riskGrade": "HIGH"
          }
        ]
    """.trimIndent()

    @Test
    fun `기업 목록을 파싱해 위험도 내림차순으로 반환한다`() = runTest {
        server.enqueue(MockResponse().setBody(companiesJson))

        val organizations = repository.getOrganizations()

        // 카카오톡: consentItems 기반 재산출 3+(3×3×1.5×1.5)×2 = 43.5
        assertEquals(listOf("카카오톡", "토스"), organizations.map { it.name })
        assertEquals(43.5, organizations[0].riskScore, 0.0)
        assertEquals(RiskGrade.VERY_HIGH, organizations[0].riskGrade)

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/companies"))
        assertTrue(request.path!!.contains("userId=1"))
        assertTrue(request.path!!.contains("sort=risk_score_desc"))
    }

    @Test
    fun `상세 매핑 시 필수-선택 분리와 변수 기여도가 유지된다`() = runTest {
        server.enqueue(MockResponse().setBody(companiesJson))

        val detail = repository.getOrganizationDetail("1")!!

        assertEquals(1, detail.consentDetail.requiredConsents.size)
        assertEquals(1, detail.consentDetail.optionalConsents.size)
        val optional = detail.consentDetail.optionalConsents.first()
        assertEquals("맞춤형 광고 동의", optional.title)
        assertEquals(RiskVariables(ds = 3, es = 3, tf = 3, pc = 1.5, ai = 1.5), optional.variableImpact)
        assertEquals("ISMS-P", detail.companyInfo.privacyCertification)
        assertEquals(1, detail.riskAnalysis.withdrawalEffects.size)
        assertEquals("최대 40.5점 감소", detail.riskAnalysis.maxEffect.totalReduction)
    }

    @Test
    fun `동의 토글 PATCH는 본문 없이 명세 경로로 전송된다`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{ "consentItemId": 12, "checked": false, "newRiskScore": 15.0, "newRiskGrade": "MEDIUM" }""",
            ),
        )

        val response = repository.patchConsent(consentItemId = 12)

        assertEquals(12L, response.consentItemId)
        assertEquals(false, response.checked)
        assertEquals(15.0, response.newRiskScore!!, 0.0)
        assertEquals(RiskGrade.MEDIUM, response.newRiskGrade)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/users/1/consents/12", request.path)
        assertEquals(0L, request.bodySize)
    }
}
