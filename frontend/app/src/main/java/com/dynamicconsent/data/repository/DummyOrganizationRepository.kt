package com.dynamicconsent.data.repository

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
import com.dynamicconsent.data.model.WithdrawalEffect

/**
 * 실제 API가 붙기 전까지 사용하는 임시 데이터 소스.
 * TODO: 실제 API 연동 구현체로 교체
 */
class DummyOrganizationRepository : OrganizationRepository {

    private val organizations = listOf(
        Organization(
            id = "kakaotalk",
            name = "카카오톡",
            category = "SNS",
            riskScore = 31.5,
            riskGrade = RiskGrade.HIGH,
            logoText = "톡",
            logoColor = 0xFFFEE500,
        ),
        Organization(
            id = "toss",
            name = "토스",
            category = "금융",
            riskScore = 32.0,
            riskGrade = RiskGrade.HIGH,
            logoText = "토스",
            logoColor = 0xFF0064FF,
        ),
        Organization(
            id = "netflix",
            name = "넷플릭스",
            category = "기타",
            riskScore = 21.0,
            riskGrade = RiskGrade.MEDIUM,
            logoText = "N",
            logoColor = 0xFFE50914,
        ),
    )

    private val details = mapOf(
        "kakaotalk" to OrganizationDetail(
            organization = organizations[0],
            consentDetail = ConsentDetail(
                optionalConsents = listOf(
                    ConsentToggleItem(1, "프로필정보 추가 수집 동의", enabled = false),
                    ConsentToggleItem(2, "이벤트 및 마케팅 활용 동의", enabled = false),
                    ConsentToggleItem(3, "위치정보 수집 및 이용 동의", enabled = false),
                    ConsentToggleItem(4, "맞춤형 광고를 위한 행태정보 수집 및 이용", enabled = false),
                ),
                requiredConsents = listOf(
                    ConsentRequiredItem(1, "카카오계정 이용약관"),
                    ConsentRequiredItem(2, "개인정보 수집·이용 동의"),
                    ConsentRequiredItem(3, "위치정보 이용약관 동의"),
                ),
            ),
            riskAnalysis = RiskAnalysis(
                riskScore = 31.5,
                riskGrade = RiskGrade.HIGH,
                formula = "위험도 = 데이터민감도(4) + (노출범위(3) × 경과시간(3) × 목적명확성(1.5) × AI위험(1.5)) × 2 = 31.5",
                factors = listOf(
                    RiskFactor("데이터민감도", "4", "메시지, 프로필, 연락처, 위치정보 등 풍부한 개인 데이터를 수집합니다."),
                    RiskFactor("노출범위", "3", "카카오 계열사, 브랜드 파트너, 광고주 등에 광범위하게 공유됩니다."),
                    RiskFactor("경과시간", "3", "위치정보, 행태정보 등이 장기간 보관됩니다."),
                    RiskFactor("목적명확성", "1.5", "마케팅, 광고 등 목적이 포괄적으로 표현됩니다."),
                    RiskFactor("AI위험", "1.5", "맞춤형 광고를 위한 자동 분석 및 타겟팅이 이루어집니다."),
                ),
                withdrawalEffects = listOf(
                    WithdrawalEffect("프로필정보 추가 수집 동의", "2점 감소"),
                    WithdrawalEffect("위치정보 수집 및 이용 동의", "4점 감소"),
                    WithdrawalEffect("맞춤형 광고를 위한 행태정보 수집 및 이용", "5점 감소"),
                ),
                maxEffect = MaxEffect(
                    currentScore = "31.5점",
                    currentGrade = RiskGrade.HIGH,
                    afterScore = "3점",
                    afterGrade = RiskGrade.VERY_LOW,
                    totalReduction = "28.5점 감소",
                ),
            ),
            companyInfo = CompanyInfo(
                serviceName = "카카오톡 Kakao Talk",
                legalName = "(주)카카오",
                privacyCertification = "ISMS-P, ISO 27001, ISO 27701",
                privacyPolicyUrl = "https://www.kakao.com/policy/privacy",
            ),
        ),
        "toss" to OrganizationDetail(
            organization = organizations[1],
            consentDetail = ConsentDetail(
                optionalConsents = listOf(
                    ConsentToggleItem(1, "마케팅 정보 수신 동의", enabled = false),
                    ConsentToggleItem(2, "제휴사/그룹사 서비스 안내", enabled = false),
                    ConsentToggleItem(3, "신용정보 조회 및 활용 동의", enabled = false),
                ),
                requiredConsents = listOf(
                    ConsentRequiredItem(1, "서비스 이용약관"),
                    ConsentRequiredItem(2, "개인정보 수집·이용 동의"),
                    ConsentRequiredItem(3, "고유식별정보 처리 동의"),
                ),
            ),
            riskAnalysis = RiskAnalysis(
                riskScore = 32.0,
                riskGrade = RiskGrade.HIGH,
                formula = "위험도 = 5 + (2 × 3 × 1.5 × 1.5) × 2 = 32.0",
                factors = listOf(
                    RiskFactor("데이터민감도", "5", "금융 거래 정보, 신용 정보 등 고도로 민감한 고유식별정보입니다."),
                    RiskFactor("노출범위", "2", "토스 그룹사·제휴사에 제한적으로 공유됩니다."),
                    RiskFactor("경과시간", "3", "장기간 지속 수집됩니다."),
                    RiskFactor("목적명확성", "1.5", "\"금융서비스 안내\" 등 일부 포괄적 표현입니다."),
                    RiskFactor("AI위험", "1.5", "AI 신용평가, 맞춤형 금융상품 추천에 활용됩니다."),
                ),
                withdrawalEffects = listOf(
                    WithdrawalEffect("신용정보 조회 및 활용", "14점 감소"),
                    WithdrawalEffect("제휴사/그룹사 서비스 안내", "3점 감소"),
                ),
                maxEffect = MaxEffect(
                    currentScore = "32.0점",
                    currentGrade = RiskGrade.HIGH,
                    afterScore = "11점",
                    afterGrade = RiskGrade.LOW,
                    totalReduction = "21점 감소",
                ),
            ),
            companyInfo = CompanyInfo(
                serviceName = "토스 Toss",
                legalName = "(주)비바리퍼블리카",
                privacyCertification = "ISMS-P, ISO 27001, ISO 27701, PCI DSS",
                privacyPolicyUrl = "https://toss.im/privacy-policy",
            ),
        ),
        "netflix" to OrganizationDetail(
            organization = organizations[2],
            consentDetail = ConsentDetail(
                optionalConsents = listOf(
                    ConsentToggleItem(1, "마케팅 정보 수신 동의", enabled = false),
                    ConsentToggleItem(2, "콘텐츠 취향 맞춤 정보 제공", enabled = true),
                ),
                requiredConsents = listOf(
                    ConsentRequiredItem(1, "이용 약관 동의"),
                    ConsentRequiredItem(2, "개인정보 수집 및 이용 동의"),
                    ConsentRequiredItem(3, "국외 이전 동의"),
                ),
            ),
            riskAnalysis = RiskAnalysis(
                riskScore = 21.0,
                riskGrade = RiskGrade.MEDIUM,
                formula = "위험도 = 3 + (2 × 3 × 1.0 × 1.5) × 2 = 21.0",
                factors = listOf(
                    RiskFactor("데이터민감도", "3", "시청 기록은 보호받아야 할 개인정보입니다."),
                    RiskFactor("노출범위", "2", "국외 이전되며, 제한적으로 공유됩니다."),
                    RiskFactor("경과시간", "3", "장기간 시청 기록이 보관됩니다."),
                    RiskFactor("목적명확성", "1.0", "\"콘텐츠 추천\", \"서비스 제공\" 목적이 명확합니다."),
                    RiskFactor("AI위험", "1.5", "콘텐츠 추천 알고리즘에 활용됩니다."),
                ),
                withdrawalEffects = listOf(
                    WithdrawalEffect("콘텐츠 취향 맞춤 정보 제공", "9점 감소"),
                ),
                maxEffect = MaxEffect(
                    currentScore = "21.0점",
                    currentGrade = RiskGrade.MEDIUM,
                    afterScore = "3점",
                    afterGrade = RiskGrade.VERY_LOW,
                    totalReduction = "18점 감소",
                ),
            ),
            companyInfo = CompanyInfo(
                serviceName = "넷플릭스 Netflix",
                legalName = "넷플릭스서비시스코리아 유한회사",
                privacyCertification = "ISO 27001",
                privacyPolicyUrl = "https://help.netflix.com/legal/privacy",
            ),
        ),
    )

    override suspend fun getOrganizations(): List<Organization> = organizations

    override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = details[id]
}
