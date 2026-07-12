package com.dynamicconsent.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamicconsent.data.model.RiskAnalysis
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.RiskLow
import com.dynamicconsent.ui.theme.TextBody
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import com.dynamicconsent.ui.theme.accentColor
import com.dynamicconsent.ui.theme.backgroundColor

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

        // 5개 변수 분석
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "5개 변수 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            riskAnalysis.factors.forEach { factor ->
                Row {
                    Text(
                        text = "• ${factor.label}(${factor.value}):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = factor.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextBody,
                    )
                }
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
