package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String companyName;           // 기업명 (예: 카카오)

    @Column(unique = true, length = 200)
    private String packageName;           // 앱 패키지명 (예: com.kakao.talk)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privacyUrl;            // 개인정보처리방침 URL

    @Column(nullable = false)
    private boolean ismsCertified = false; // ISMS-P 인증 여부

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
