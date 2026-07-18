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
 * 실서버 연동 전에 프론트 쪽 준비가 끝났음을 보장하는 테스트.
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
          {
            "company_id": 1,
            "company_name": "카카오톡",
            "package_name": "com.kakao.talk",
            "privacy_url": "https://www.kakao.com/policy/privacy",
            "isms_certified": true,
            "consent_items": [
              { "consent_item_id": 11, "item_type": "REQUIRED", "item_name": "이용약관 동의",
                "ds_score": 1, "es_score": 1, "tf_score": 1, "pc_score": 1.0, "ai_score": 1.0 },
              { "consent_item_id": 12, "item_type": "OPTIONAL", "item_name": "맞춤형 광고 동의",
                "ds_score": 3, "es_score": 3, "tf_score": 3, "pc_score": 1.5, "ai_score": 1.5,
                "is_checked": true }
            ]
          },
          {
            "company_id": 2,
            "company_name": "토스",
            "isms_certified": false,
            "consent_items": [
              { "consent_item_id": 21, "item_type": "OPTIONAL", "item_name": "신용정보 활용 동의",
                "ds_score": 5, "es_score": 2, "tf_score": 3, "pc_score": 1.5, "ai_score": 1.0,
                "is_checked": true }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `기업 목록을 파싱해 위험도 내림차순으로 반환한다`() = runTest {
        server.enqueue(MockResponse().setBody(companiesJson))

        val organizations = repository.getOrganizations()

        // 카카오톡: 3+(3×3×1.5×1.5)×2 = 43.5 / 토스: 5+(2×3×1.5×1.0)×2 = 23.0
        assertEquals(listOf("카카오톡", "토스"), organizations.map { it.name })
        assertEquals(43.5, organizations[0].riskScore, 0.0)
        assertEquals(RiskGrade.VERY_HIGH, organizations[0].riskGrade)
        assertEquals(23.0, organizations[1].riskScore, 0.0)
        assertEquals(RiskGrade.MEDIUM, organizations[1].riskGrade)

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/companies"))
        assertTrue(request.path!!.contains("user_id=1"))
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
        // 철회 효과·최대 효과도 재산출돼 있어야 한다
        assertEquals(1, detail.riskAnalysis.withdrawalEffects.size)
        assertEquals("최대 40.5점 감소", detail.riskAnalysis.maxEffect.totalReduction)
    }

    @Test
    fun `동의 토글 PATCH는 명세 경로와 본문으로 전송된다`() = runTest {
        server.enqueue(MockResponse().setBody("""{ "new_risk_score": 15.0, "grade": "MEDIUM" }"""))

        val response = repository.patchConsent(consentItemId = 12, enabled = false)

        assertEquals(15.0, response.newRiskScore, 0.0)
        assertEquals(RiskGrade.MEDIUM, response.grade)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/users/1/consents/12", request.path)
        assertEquals("""{"is_checked":false}""", request.body.readUtf8())
    }
}
