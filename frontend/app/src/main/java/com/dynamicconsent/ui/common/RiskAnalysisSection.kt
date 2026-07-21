package com.dynamicconsent.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamicconsent.data.model.RiskAnalysis
import com.dynamicconsent.data.model.RiskFactor
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.RiskLow
import com.dynamicconsent.ui.theme.TextBody
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import com.dynamicconsent.ui.theme.accentColor
import com.dynamicconsent.ui.theme.backgroundColor

/** 5대 변수의 만점 기준. 게이지 채움 비율 계산에 사용한다. */
private fun maxValueFor(label: String): Float = when (label) {
    "데이터민감도" -> 5f
    "노출범위", "경과시간" -> 3f
    "목적명확성", "AI위험" -> 1.5f
    else -> 5f
}

/**
 * 위험도 점수 · 산출식 · 변수 분석 · 철회 효과 · 최대 효과를 보여주는 공용 섹션.
 * 위험기관리스트 상세 카드와 기업상세 '위험도' 탭에서 공통으로 사용한다.
 */
@Composable
fun RiskAnalysisSection(
    riskAnalysis: RiskAnalysis,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 위험도 점수
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(riskAnalysis.riskGrade.backgroundColor, RoundedCornerShape(12.dp))
                .border(2.dp, riskAnalysis.riskGrade.accentColor, RoundedCornerShape(12.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("위험도 점수", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                text = "${riskAnalysis.riskScore}점",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = riskAnalysis.riskGrade.accentColor,
            )
            Text(
                text = "(${riskAnalysis.riskGrade.displayName})",
                style = MaterialTheme.typography.titleMedium,
                color = riskAnalysis.riskGrade.accentColor,
            )
        }

        // 산출식
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("산출식", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                text = riskAnalysis.formula,
                style = MaterialTheme.typography.bodyMedium,
                color = TextBody,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // 5개 변수 분석 (변수별 게이지 시각화)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "5개 변수 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            riskAnalysis.factors.forEach { factor ->
                RiskFactorGauge(factor = factor, accentColor = riskAnalysis.riskGrade.accentColor)
            }
        }

        // 동의한 선택항목 철회 시 효과
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "동의한 선택항목 철회시 효과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerColor, RoundedCornerShape(8.dp)),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Text("선택항목", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("감소량", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                riskAnalysis.withdrawalEffects.forEach { effect ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = DividerColor)
                            .padding(12.dp),
                    ) {
                        Text(effect.consentTitle, style = MaterialTheme.typography.bodyMedium, color = TextBody, modifier = Modifier.weight(1f))
                        Text(effect.pointsReduced, style = MaterialTheme.typography.bodyMedium, color = TextBody, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 최대 효과
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RiskLow.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(2.dp, RiskLow, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text("최대 효과", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                text = "현재: ${riskAnalysis.maxEffect.currentScore} (${riskAnalysis.maxEffect.currentGrade.displayName})",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBody,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "모두 철회 시: ${riskAnalysis.maxEffect.afterScore} (${riskAnalysis.maxEffect.afterGrade.displayName})",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBody,
            )
            Text(
                text = riskAnalysis.maxEffect.totalReduction,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = RiskLow,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * 변수 하나의 위험 수준을 만점 대비 게이지 막대로 보여준다.
 * 동의 철회로 변수 값이 낮아지면 채움 폭이 애니메이션으로 줄어든다.
 */
@Composable
private fun RiskFactorGauge(
    factor: RiskFactor,
    accentColor: Color,
) {
    val value = factor.value.toFloatOrNull() ?: 0f
    val fraction = (value / maxValueFor(factor.label)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "gaugeFill")

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = factor.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${factor.value} / ${formatMax(maxValueFor(factor.label))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(8.dp)
                .background(DividerColor, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .background(accentColor, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = factor.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextBody,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 5.0 → "5", 1.5 → "1.5" */
private fun formatMax(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
