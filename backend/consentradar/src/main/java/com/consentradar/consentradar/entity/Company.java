package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String companyName;           // 기업명 (예: 카카오)

    @Column(nullable = false, length = 100)
    private String legalName;             // 법인 정식 명칭 (예: (주)카카오)

    @Column(nullable = false, length = 50)
    private String category;              // 기업 카테고리 (예: SNS, 금융)

    @Column(unique = true, length = 200)
    private String packageName;           // 앱 패키지명 (예: com.kakao.talk)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privacyUrl;            // 개인정보처리방침 URL

    @Column(nullable = false)
    private boolean ismsCertified = false; // ISMS-P 인증 여부

    // 사용자용 위험 요약 한 문장 ("어떤 정보를 어떤 목적으로 가져가서 위험한지"). LLM 분석이
    // 아직 한 번도 성공하지 않은 기업은 null — 프론트가 이 경우 설명 칸을 숨긴다. 변수별 근거
    // (ConsentItem.dsReason 등)와 달리 이건 동의 항목 단위가 아니라 기업 단위 요약이다.
    @Column(columnDefinition = "TEXT")
    private String riskSummary;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 연관관계
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<ConsentItem> consentItems;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<PolicySnapshot> policySnapshots;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<RiskScore> riskScores;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
