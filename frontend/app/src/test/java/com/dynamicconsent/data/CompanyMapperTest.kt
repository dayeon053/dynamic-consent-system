package com.dynamicconsent.data

import com.dynamicconsent.data.remote.CompanyMapper
import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentItemResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 서버가 주는 값(category/legalName)과 프론트가 채우는 값(로고)의 경계를 검증한다.
 * 로고는 서버 스키마에 없어 CompanyMapper의 하드코딩 표에서 나온다.
 */
class CompanyMapperTest {

    private fun company(
        companyId: Long = 1,
        companyName: String = "카카오",
        legalName: String? = "(주)카카오",
        category: String? = "SNS",
        packageName: String? = "com.kakao.talk",
        riskSummary: String? = "위치정보와 연락처를 광고 목적으로 수집해 제3자에게 제공합니다.",
    ) = CompanyResponse(
        companyId = companyId,
        companyName = companyName,
        legalName = legalName,
        category = category,
        packageName = packageName,
        privacyUrl = "https://example.com/privacy",
        ismsCertified = true,
        riskScore = 43.5,
        riskGrade = null,
        riskSummary = riskSummary,
    )

    private val consentItems = listOf(
        ConsentItemResponse(
            consentItemId = 11,
            itemName = "이용약관 동의",
            itemType = ConsentItemResponse.TYPE_REQUIRED,
        ),
    )

    @Test
    fun `category는 서버 값을 그대로 쓴다`() {
        assertEquals("SNS", CompanyMapper.toOrganization(company()).category)
        assertEquals("금융", CompanyMapper.toOrganization(company(category = "금융")).category)
    }

    @Test
    fun `category가 없거나 비면 기타로 떨어진다`() {
        assertEquals("기타", CompanyMapper.toOrganization(company(category = null)).category)
        assertEquals("기타", CompanyMapper.toOrganization(company(category = "  ")).category)
    }

    @Test
    fun `legalName은 서버 값을 쓰고 없으면 서비스명으로 떨어진다`() {
        val withLegalName = CompanyMapper.toOrganizationDetail(company(), consentItems)
        assertEquals("(주)카카오", withLegalName.companyInfo.legalName)
        assertEquals("카카오", withLegalName.companyInfo.serviceName)

        val without = CompanyMapper.toOrganizationDetail(company(legalName = null), consentItems)
        assertEquals("카카오", without.companyInfo.legalName)
    }

    @Test
    fun `로고는 packageName으로 찾는다`() {
        val organization = CompanyMapper.toOrganization(
            // 기업명이 표에 없는 표기여도 packageName이 맞으면 로고가 붙어야 한다
            company(companyName = "카카오톡", packageName = "com.kakao.talk"),
        )

        assertEquals("톡", organization.logoText)
        assertEquals(0xFFFEE500L, organization.logoColor)
    }

    @Test
    fun `packageName이 없으면 기업명으로 찾는다`() {
        val organization = CompanyMapper.toOrganization(
            company(companyName = "토스", packageName = null),
        )

        assertEquals("토스", organization.logoText)
        assertEquals(0xFF0064FFL, organization.logoColor)
    }

    @Test
    fun `표에 없는 기업은 첫 글자와 기본색으로 떨어진다`() {
        val organization = CompanyMapper.toOrganization(
            company(companyName = "새로운기업", packageName = "com.example.newco"),
        )

        assertEquals("새", organization.logoText)
        assertEquals(0xFF00752FL, organization.logoColor)
    }

    @Test
    fun `riskSummary는 서버 문구 그대로 위험도 분석에 실린다`() {
        val detail = CompanyMapper.toOrganizationDetail(company(), consentItems)

        assertEquals(
            "위치정보와 연락처를 광고 목적으로 수집해 제3자에게 제공합니다.",
            detail.riskAnalysis.summary,
        )
    }

    @Test
    fun `riskSummary가 없거나 비면 null이라 설명 칸을 숨긴다`() {
        // 아직 분석 전인 기업, 구버전 서버 모두 여기로 떨어진다.
        val none = CompanyMapper.toOrganizationDetail(company(riskSummary = null), consentItems)
        val blank = CompanyMapper.toOrganizationDetail(company(riskSummary = "  "), consentItems)

        assertNull(none.riskAnalysis.summary)
        assertNull(blank.riskAnalysis.summary)
    }

    @Test
    fun `동의 철회로 점수가 바뀌어도 설명 문구는 그대로다`() {
        // toOrganizationDetail은 마지막에 RiskRecalculator를 한 번 태운다.
        // 재산출은 점수·변수만 갱신하고 서버가 준 문구는 건드리지 않아야 한다.
        val optional = ConsentItemResponse(
            consentItemId = 12,
            itemName = "마케팅 활용 동의",
            itemType = ConsentItemResponse.TYPE_OPTIONAL,
            checked = false,
            dsScore = 5,
        )

        val detail = CompanyMapper.toOrganizationDetail(company(), consentItems + optional)

        assertEquals(
            "위치정보와 연락처를 광고 목적으로 수집해 제3자에게 제공합니다.",
            detail.riskAnalysis.summary,
        )
    }

    @Test
    fun `5개 기업 모두 로고가 매핑되어 있다`() {
        val expected = mapOf(
            "com.kakao.talk" to "톡",
            "com.nhn.android.search" to "N",
            "com.sampleapp" to "배민",
            "viva.republica.toss" to "토스",
            "com.towneers.www" to "당근",
        )

        expected.forEach { (packageName, logoText) ->
            val organization = CompanyMapper.toOrganization(
                company(companyName = "이름과무관", packageName = packageName),
            )
            assertEquals(logoText, organization.logoText)
        }
    }
}
