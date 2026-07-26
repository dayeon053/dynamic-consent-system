package com.dynamicconsent.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.ui.theme.TextBody
import com.dynamicconsent.ui.theme.TextSecondary
import com.dynamicconsent.ui.theme.accentColor
import com.dynamicconsent.ui.theme.backgroundColor

/**
 * 다른 앱 위에 뜨는 위험도 요약 팝업.
 * 색상은 기존 테마의 RiskGrade.accentColor / backgroundColor 확장을 그대로 재사용한다.
 */
@Composable
fun RiskOverlayContent(
    score: Double,
    grade: RiskGrade,
    onDetail: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = grade.accentColor

    Surface(
        modifier = modifier.width(300.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(2.dp, accent),
    ) {
        Column(Modifier.padding(16.dp)) {

            // 헤더: 등급 + 닫기
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = grade.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "닫기", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 점수 박스
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(grade.backgroundColor, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("위험도 점수", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = "${score}점",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = recommendationFor(grade),
                style = MaterialTheme.typography.bodyMedium,
                color = TextBody,
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onDetail,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text("상세보기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 등급별 권장 조치 문구 (common-model의 recommendedAction 개념을 프론트에서 간략 매핑). */
private fun recommendationFor(grade: RiskGrade): String = when (grade) {
    RiskGrade.VERY_LOW -> "현재 상태를 유지해도 좋아요."
    RiskGrade.LOW -> "기본 보안 조치를 권장해요."
    RiskGrade.MEDIUM -> "보관 기간을 점검해보세요."
    RiskGrade.HIGH -> "노출 범위 축소를 권장해요."
    RiskGrade.VERY_HIGH -> "즉시 동의 해지를 권장해요."
}
