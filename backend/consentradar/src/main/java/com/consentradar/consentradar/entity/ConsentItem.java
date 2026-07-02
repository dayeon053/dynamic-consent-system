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

    // 기업과 N:1 관계 (여러 동의 항목이 하나의 기업에 속함)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;            // REQUIRED(필수) / OPTIONAL(선택)

    @Column(nullable = false, length = 200)
    private String itemName;              // 항목명 (예: 마케팅 수신 동의)

    // 5대 변수 점수 (0~10)
    @Column(nullable = false)
    private int dsScore;                  // 데이터 민감도 (Data Sensitivity)

    @Column(nullable = false)
    private int esScore;                  // 외부 공유 여부 (External Sharing)

    @Column(nullable = false)
    private int tfScore;                  // 제3자 제공 빈도 (Third-party Frequency)

    @Column(nullable = false)
    private int pcScore;                  // 처리 목적 명확성 (Purpose Clarity)

    @Column(nullable = false)
    private int aiScore;                  // 자동화된 의사결정 (Automated decision-making Impact)

    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 연관관계
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
