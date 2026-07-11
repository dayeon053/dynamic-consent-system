package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "consent_item")
@Getter
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
