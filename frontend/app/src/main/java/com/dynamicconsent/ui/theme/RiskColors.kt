package com.dynamicconsent.ui.theme

import androidx.compose.ui.graphics.Color
import com.dynamicconsent.data.model.RiskGrade

/** 등급 강조색(테두리·숫자 텍스트 등에 사용). */
val RiskGrade.accentColor: Color
    get() = when (this) {
        RiskGrade.VERY_HIGH -> RiskVeryHigh
        RiskGrade.HIGH -> RiskHigh
        RiskGrade.MEDIUM -> RiskMedium
        RiskGrade.LOW -> RiskLow
        RiskGrade.VERY_LOW -> RiskVeryLow
    }

/** 등급 배경색(카드·박스 배경에 사용). */
val RiskGrade.backgroundColor: Color
    get() = when (this) {
        RiskGrade.VERY_HIGH -> RiskVeryHighBg
        RiskGrade.HIGH -> RiskHighBg
        RiskGrade.MEDIUM -> RiskMediumBg
        RiskGrade.LOW -> RiskLowBg
        RiskGrade.VERY_LOW -> RiskVeryLowBg
    }
