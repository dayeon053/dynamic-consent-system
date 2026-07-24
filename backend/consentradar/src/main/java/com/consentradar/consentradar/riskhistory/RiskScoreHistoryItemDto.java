package com.consentradar.consentradar.riskhistory;

import com.consentradar.consentradar.entity.RiskScore;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RiskScoreHistoryItemDto(
        LocalDate scoredAt,
        BigDecimal totalScore,
        RiskScore.Grade grade
) {
    public static RiskScoreHistoryItemDto from(RiskScore riskScore) {
        return new RiskScoreHistoryItemDto(
                riskScore.getScoredAt(), riskScore.getTotalScore(), riskScore.getGrade());
    }
}
