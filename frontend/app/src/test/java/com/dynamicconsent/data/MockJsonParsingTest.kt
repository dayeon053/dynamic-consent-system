package com.dynamicconsent.data

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.domain.RiskCalculator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * assets/mock의 JSON 파일이 데이터 모델과 포맷이 일치하는지 검증한다.
 * 이 JSON은 백엔드 API 응답 포맷 초안이므로, 여기서 깨지면 모델-포맷 불일치라는 뜻.
 */
class MockJsonParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(name: String): String {
        // 단위 테스트는 JVM에서 돌므로 assets을 파일 경로로 직접 읽는다.
        val root = File(checkNotNull(System.getProperty("user.dir")))
        return File(root, "src/main/assets/mock/$name").readText()
    }

    @Test
    fun `organizations json이 Organization 리스트로 파싱된다`() {
        val organizations: List<Organization> = json.decodeFromString(asset("organizations.json"))

        // 백엔드 company 테이블(V6 backfill 기준)의 5개 기업과 목록을 일치시킨다.
        assertEquals(5, organizations.size)
        assertEquals(listOf("kakaotalk", "toss", "naver", "baemin", "daangn"), organizations.map { it.id })

        val kakao = organizations.first()
        assertEquals("카카오톡", kakao.name)
        assertEquals(43.5, kakao.riskScore, 0.0)
        assertEquals(RiskGrade.VERY_HIGH, kakao.riskGrade)
        assertEquals(0xFFFEE500, kakao.logoColor)
    }

    @Test
    fun `organization_details json이 OrganizationDetail 맵으로 파싱된다`() {
        val details: Map<String, OrganizationDetail> =
            json.decodeFromString(asset("organization_details.json"))

        assertEquals(setOf("kakaotalk", "toss", "naver", "baemin", "daangn"), details.keys)

        details.forEach { (id, detail) ->
            assertEquals(id, detail.organization.id)
            assertEquals(5, detail.riskAnalysis.factors.size)
            assertTrue(detail.riskAnalysis.withdrawalEffects.isNotEmpty())
            assertTrue(detail.consentDetail.optionalConsents.isNotEmpty())
            assertTrue(detail.consentDetail.requiredConsents.isNotEmpty())
            assertNotNull(detail.companyInfo.privacyPolicyUrl)
        }
    }

    @Test
    fun `리스트와 상세의 기관 요약 정보가 서로 일치한다`() {
        val organizations: List<Organization> = json.decodeFromString(asset("organizations.json"))
        val details: Map<String, OrganizationDetail> =
            json.decodeFromString(asset("organization_details.json"))

        organizations.forEach { org ->
            val detailOrg = details.getValue(org.id).organization
            assertEquals(org, detailOrg)
        }
    }

    @Test
    fun `JSON의 기관 점수는 동의 항목 변수 기여도로 산출한 점수와 일치한다`() {
        val details: Map<String, OrganizationDetail> =
            json.decodeFromString(asset("organization_details.json"))

        details.forEach { (id, detail) ->
            val enabledImpacts = detail.consentDetail.optionalConsents
                .filter { it.enabled }
                .map { it.variableImpact }
            val score = RiskCalculator.calculateScore(RiskCalculator.combineImpacts(enabledImpacts))

            assertEquals("$id: JSON 점수와 수식 산출 점수 불일치", detail.organization.riskScore, score, 0.0)
            assertEquals("$id: JSON 등급과 수식 산출 등급 불일치", detail.organization.riskGrade, RiskCalculator.classifyGrade(score))
        }
    }

    @Test
    fun `모든 기관에 제3자 제공 정보가 포함돼 있다`() {
        val details: Map<String, OrganizationDetail> =
            json.decodeFromString(asset("organization_details.json"))

        details.forEach { (id, detail) ->
            assertTrue("$id: thirdPartyProviders 비어 있음", detail.thirdPartyProviders.isNotEmpty())
            detail.thirdPartyProviders.forEach { provider ->
                assertTrue("$id: 제공처 이름 누락", provider.name.isNotBlank())
                assertTrue("$id: 제공 목적 누락", provider.purpose.isNotBlank())
            }
        }
    }

    @Test
    fun `모든 기관에 감시용 packageName이 있다`() {
        val organizations: List<Organization> = json.decodeFromString(asset("organizations.json"))

        // packageName이 없는 기업은 WatchedAppRegistry가 감시 대상에서 제외하므로,
        // mock 5개 기업은 전부 앱 실행 감지가 가능해야 한다.
        organizations.forEach { org ->
            assertTrue("${org.id}: packageName 누락", !org.packageName.isNullOrBlank())
        }
        assertEquals(organizations.size, organizations.mapNotNull { it.packageName }.distinct().size)
    }
}
