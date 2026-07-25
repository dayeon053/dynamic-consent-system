package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_score", uniqueConstraints = @UniqueConstraint(
        name = "uq_risk_score_user_company_scoredat_rep",
        columnNames = {"user_id", "company_id", "scored_at", "is_representative"}
))
@Getter
@Setter
@NoArgsConstructor
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long riskScoreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
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
    private boolean isRepresentative = false; // false = 항목별 점수, true = 기업 대표 점수

    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 낙관적 락 버전 컬럼. 동일 사용자가 짧은 시간 안에 여러 선택동의를 토글해 같은
     * (user, company) 대표 row를 동시에 갱신하는 경합 상황에서, 나중에 커밋하는 쪽이
     * 먼저 커밋된 값을 조용히 덮어쓰지 않고 {@link org.springframework.orm.ObjectOptimisticLockingFailureException}으로
     * 감지되게 한다. 호출부(RiskScoreUpsertService)는 이 예외를 받아 재계산 후 재시도한다.
     */
    @Version
    private Long version;

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
