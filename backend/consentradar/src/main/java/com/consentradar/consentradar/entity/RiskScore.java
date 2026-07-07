package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_score")
@Getter
@NoArgsConstructor
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long riskScoreId;

    // 사용자와 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 기업과 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 위험도 점수: Risk Score = DS + (ES × TF × PC × AI) × 2
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;                  // 5단계 등급

    @Column(nullable = false)
    private LocalDate scoredAt;           // 산출 날짜 (날짜별 누적 저장, append only)

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Grade {
        VERY_SAFE,      // 매우 안전
        SAFE,           // 안전
        NORMAL,         // 보통
        DANGEROUS,      // 위험
        VERY_DANGEROUS  // 매우 위험
    }
}
