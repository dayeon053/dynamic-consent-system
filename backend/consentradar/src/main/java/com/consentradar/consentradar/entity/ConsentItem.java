package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "consent_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consent_item_company_item_name",
                columnNames = {"company_id", "item_name"}))
@Getter
@Setter
@NoArgsConstructor
public class ConsentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long consentItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false)
    private int dsScore;                  // 데이터 민감도 (Data Sensitivity)

    @Column(nullable = false)
    private int esScore;                  // 노출 범위 (Exposure Scope)

    @Column(nullable = false)
    private int tfScore;                  // 경과 시간 - 보관 기간 (Time Factor)

    @Column(nullable = false)
    private double pcScore;               // 처리 목적 명확성 (Purpose Clarity) - 1.0 / 1.5

    @Column(nullable = false)
    private double aiScore;               // AI 위험계수 (AI Risk Factor) - 1.0 / 1.5

    /**
     * 소프트 삭제 플래그. 재크롤링/재분석 시 이번 LLM 응답에 더 이상 나타나지 않는 기존
     * 항목은 물리적으로 지우지 않고 이 값만 false로 내린다({@link
     * com.consentradar.consentradar.consentitem.ConsentItemUpsertService#deactivateMissing}) —
     * UserConsentCheck/UserConsentHistory가 이 row를 FK로 참조하고 있어 하드 삭제하면 이력이
     * 끊기기 때문이다. 위험도 계산(PersonalRiskCalculator)과 동의 항목 목록 API는 반드시
     * active=true인 항목만 조회해야 한다.
     */
    @Column(name = "is_active", nullable = false, columnDefinition = "BIT(1) DEFAULT 1")
    private boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "consentItem", cascade = CascadeType.ALL)
    private List<UserConsentCheck> userConsentChecks;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ItemType {
        REQUIRED, OPTIONAL
    }
}
