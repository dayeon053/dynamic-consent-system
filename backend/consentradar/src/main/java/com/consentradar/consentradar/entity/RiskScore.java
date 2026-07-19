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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;

    @Column(nullable = false)
    private LocalDate scoredAt;

    @Column(name = "is_representative", nullable = false)
    private boolean isRepresentative = false; // false: 항목별 점수, true: 기업 대표(종합) 점수

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Grade {
        VERY_LOW,   // 매우 안전
        LOW,        // 안전
        MEDIUM,     // 보통
        HIGH,       // 위험
        VERY_HIGH   // 매우 위험
    }
}
