package com.dynamicconsent.data.remote

import com.dynamicconsent.data.model.CompanyInfo
import com.dynamicconsent.data.model.ConsentDetail
import com.dynamicconsent.data.model.ConsentRequiredItem
import com.dynamicconsent.data.model.ConsentToggleItem
import com.dynamicconsent.data.model.MaxEffect
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskAnalysis
import com.dynamicconsent.data.model.RiskFactor
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.model.RiskVariables
import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentItemResponse
import com.dynamicconsent.domain.RiskCalculator
import com.dynamicconsent.domain.RiskRecalculator

/**
 * 서버 응답을 화면 모델로 변환한다.
 * 기업 요약(GET /companies)과 동의 항목(GET /companies/{id}/consent-items)이 별도 응답이라
 * 각각 매핑 함수를 둔다. 위험도 분석 정보는 동의 항목의 5대 변수로 클라이언트에서 재산출한다.
 */
object CompanyMapper {

    private const val DEFAULT_LOGO_COLOR = 0xFF00752FL
    private const val DEFAULT_CATEGORY = "기타"

    /**
     * 기업 로고(약칭·배경색). 서버 스키마에 없는 값이라 프론트에서 관리한다.
     * 팀 합의: 이 3~5개 기업 데모 범위에서는 백엔드 컬럼을 추가하지 않고 여기에 둔다.
     *
     * packageName을 1순위 키로 쓴다 — companyId는 DB 입력 순서에 따라 환경마다 달라지고,
     * 기업명은 "카카오"/"카카오톡"처럼 표기가 흔들리기 때문. packageName이 없는 기업은
     * 기업명으로 한 번 더 찾고, 그래도 없으면 기존처럼 첫 글자 + 기본색으로 떨어진다.
     */
    private data class Branding(
        val packageName: String,
        val companyName: String,
        val logoText: String,
        val logoColor: Long,
    )

    private val BRANDINGS = listOf(
        Branding("com.kakao.talk", "카카오", "톡", 0xFFFEE500L),
        Branding("com.nhn.android.search", "네이버", "N", 0xFF03C75AL),
        Branding("com.sampleapp", "배달의민족", "배민", 0xFF2AC1BCL),
        Branding("viva.republica.toss", "토스", "토스", 0xFF0064FFL),
        Branding("com.towneers.www", "당근마켓", "당근", 0xFFFF6F0FL),
    )

    private val brandingByPackage = BRANDINGS.associateBy { it.packageName }
    private val brandingByName = BRANDINGS.associateBy { it.companyName }

    private fun brandingOf(response: CompanyResponse): Branding? =
        response.packageName?.let { brandingByPackage[it] } ?: brandingByName[response.companyName]

    /** GET /companies 항목 → 리스트 카드용 요약. 위험도는 서버 산출값을 그대로 쓴다. */
    fun toOrganization(response: CompanyResponse): Organization {
        val branding = brandingOf(response)
        return Organization(
            id = response.companyId.toString(),
            packageName = response.packageName,
            name = response.companyName,
            category = response.category?.takeIf { it.isNotBlank() } ?: DEFAULT_CATEGORY,
            riskScore = response.riskScore ?: RiskCalculator.MIN_SCORE,
            riskGrade = response.riskGrade ?: RiskGrade.VERY_LOW,
            logoText = branding?.logoText ?: response.companyName.take(1),
            logoColor = branding?.logoColor ?: DEFAULT_LOGO_COLOR,
        )
    }

    /**
     * 기업 요약 + 동의 항목 → 기업상세.
     * 사용자 체크 상태 기준으로 점수·등급·산출식·철회 효과를 클라이언트에서 재산출해 채운다.
     */
    fun toOrganizationDetail(
        company: CompanyResponse,
        consentItems: List<ConsentItemResponse>,
    ): OrganizationDetail {
        val optionalConsents = consentItems
            .filter { it.itemType == ConsentItemResponse.TYPE_OPTIONAL }
            .map { item ->
                ConsentToggleItem(
                    id = item.consentItemId.toInt(),
                    title = item.itemName,
                    enabled = item.checked,
                    variableImpact = RiskVariables(
                        ds = item.dsScore,
                        es = item.esScore,
                        tf = item.tfScore,
                        pc = item.pcScore,
                        ai = item.aiScore,
                    ),
                )
            }
        val requiredConsents = consentItems
            .filter { it.itemType == ConsentItemResponse.TYPE_REQUIRED }
            .map { item ->
                ConsentRequiredItem(
                    id = item.consentItemId.toInt(),
                    title = item.itemName,
                    variableImpact = RiskVariables(
                        ds = item.dsScore,
                        es = item.esScore,
                        tf = item.tfScore,
                        pc = item.pcScore,
                        ai = item.aiScore,
                    ),
                )
            }

        val base = OrganizationDetail(
            organization = toOrganization(company),
            consentDetail = ConsentDetail(
                optionalConsents = optionalConsents,
                requiredConsents = requiredConsents,
            ),
            riskAnalysis = placeholderAnalysis(),
            companyInfo = CompanyInfo(
                serviceName = company.companyName,
                // 구버전 서버(V6 이전)는 legalName을 안 주므로 서비스명으로 떨어진다.
                legalName = company.legalName?.takeIf { it.isNotBlank() } ?: company.companyName,
                privacyCertification = if (company.ismsCertified) "ISMS-P" else "-",
                privacyPolicyUrl = company.privacyUrl.orEmpty(),
            ),
        )

        val enabledIds = optionalConsents.filter { it.enabled }.map { it.id }.toSet()
        return RiskRecalculator.recalculate(base, enabledIds)
    }

    /** recalculate가 값·설명을 채우기 전의 기본 틀. 변수 설명은 서버 스키마에 없어 공통 문구를 쓴다. */
    private fun placeholderAnalysis(): RiskAnalysis = RiskAnalysis(
        riskScore = RiskCalculator.MIN_SCORE,
        riskGrade = RiskGrade.VERY_LOW,
        formula = "",
        factors = listOf(
            RiskFactor("데이터민감도", "1", "수집하는 개인정보의 민감도 수준입니다."),
            RiskFactor("노출범위", "1", "제3자 제공·공유 범위입니다."),
            RiskFactor("경과시간", "1", "개인정보 보관 기간입니다."),
            RiskFactor("목적명확성", "1", "처리 목적이 얼마나 명확한지입니다."),
            RiskFactor("AI위험", "1", "AI 분석·활용에 따른 위험입니다."),
        ),
        withdrawalEffects = emptyList(),
        maxEffect = MaxEffect("", RiskGrade.VERY_LOW, "", RiskGrade.VERY_LOW, ""),
    )
}
