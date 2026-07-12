package com.dynamicconsent.domain

import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskGrade
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 동의 상태 변경 → 위험도 재산출 → 리스트 재정렬 시나리오를 mock JSON 실데이터로 검증한다.
 */
class RiskRecalculatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val details: Map<String, OrganizationDetail> by lazy {
        val root = File(checkNotNull(System.getProperty("user.dir")))
        val text = File(root, "src/main/assets/mock/organization_details.json").readText()
        json.decodeFromString(text)
    }

    private fun enabledIdsOf(detail: OrganizationDetail): Set<Int> =
        detail.consentDetail.optionalConsents.filter { it.enabled }.map { it.id }.toSet()

    @Test
    fun `모든 선택동의를 유지하면 기본 점수와 등급이 그대로 산출된다`() {
        val kakao = details.getValue("kakaotalk")

        val result = RiskRecalculator.recalculate(kakao, enabledIdsOf(kakao))

        assertEquals(43.5, result.organization.riskScore, 0.0)
        assertEquals(RiskGrade.VERY_HIGH, result.organization.riskGrade)
        assertEquals(43.5, result.riskAnalysis.riskScore, 0.0)
    }

    @Test
    fun `모든 선택동의를 철회하면 최솟값 3점 매우 안전이 된다`() {
        details.values.forEach { detail ->
            val result = RiskRecalculator.recalculate(detail, emptySet())

            assertEquals(3.0, result.organization.riskScore, 0.0)
            assertEquals(RiskGrade.VERY_LOW, result.organization.riskGrade)
            assertTrue(result.consentDetail.optionalConsents.none { it.enabled })
        }
    }

    @Test
    fun `일부 철회 시 점수가 낮아지고 등급이 함께 내려간다`() {
        val kakao = details.getValue("kakaotalk")

        // 마케팅(2)·위치정보(3)·광고 파트너 제3자 제공(6) 철회
        // → 변수: DS=3, ES=2, TF=2, PC=1.0, AI=1.5 → 3 + (2×2×1×1.5)×2 = 15.0
        val result = RiskRecalculator.recalculate(kakao, enabledIdsOf(kakao) - setOf(2, 3, 6))

        assertEquals(15.0, result.organization.riskScore, 0.0)
        assertEquals(RiskGrade.MEDIUM, result.organization.riskGrade)
    }

    @Test
    fun `철회 효과 표는 항목별 점수 감소량을 계산해 담는다`() {
        val kakao = details.getValue("kakaotalk")

        val result = RiskRecalculator.recalculate(kakao, enabledIdsOf(kakao))
        val effects = result.riskAnalysis.withdrawalEffects.associate { it.consentTitle to it.pointsReduced }

        assertEquals("13.5점 감소", effects.getValue("이벤트 및 마케팅 활용 동의"))
        assertEquals("13.5점 감소", effects.getValue("광고 파트너 제3자 제공 동의"))
        // 다른 동의가 같은 변수 위험을 유지하는 항목은 단독 철회 효과가 없다
        assertEquals("감소 없음", effects.getValue("카카오 계열사 정보 공유 동의"))
    }

    @Test
    fun `최대 효과에는 전부 철회 시 점수와 총 감소량이 담긴다`() {
        val kakao = details.getValue("kakaotalk")

        val result = RiskRecalculator.recalculate(kakao, enabledIdsOf(kakao))
        val maxEffect = result.riskAnalysis.maxEffect

        assertEquals("43.5점", maxEffect.currentScore)
        assertEquals(RiskGrade.VERY_HIGH, maxEffect.currentGrade)
        assertEquals("3.0점", maxEffect.afterScore)
        assertEquals(RiskGrade.VERY_LOW, maxEffect.afterGrade)
        assertEquals("최대 40.5점 감소", maxEffect.totalReduction)
    }

    @Test
    fun `카카오 동의 철회로 점수가 떨어지면 위험도 순위가 재정렬된다`() {
        val base = details.values.map { RiskRecalculator.recalculate(it, enabledIdsOf(it)) }
        val baseOrder = base.sortedByDescending { it.organization.riskScore }.map { it.organization.id }
        assertEquals(listOf("kakaotalk", "toss", "netflix"), baseOrder)

        // 카카오만 대부분 철회 → 15.0점으로 하락
        val kakao = details.getValue("kakaotalk")
        val adjusted = details.values.map { detail ->
            if (detail.organization.id == "kakaotalk") {
                RiskRecalculator.recalculate(detail, enabledIdsOf(kakao) - setOf(2, 3, 6))
            } else {
                RiskRecalculator.recalculate(detail, enabledIdsOf(detail))
            }
        }
        val adjustedOrder = adjusted.sortedByDescending { it.organization.riskScore }.map { it.organization.id }

        assertEquals(listOf("toss", "netflix", "kakaotalk"), adjustedOrder)
    }
}
