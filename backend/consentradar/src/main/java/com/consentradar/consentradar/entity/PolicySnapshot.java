package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_snapshot")
@Getter
@NoArgsConstructor
public class PolicySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    // 기업과 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String rawText;               // 크롤링된 약관 원문 텍스트

    @Column(nullable = false)
    private boolean isChanged = false;    // 전일 대비 변경 여부 플래그

    @Column(nullable = false, updatable = false)
    private LocalDateTime crawledAt;      // 수집 일시

    @PrePersist
    public void prePersist() {
        this.crawledAt = LocalDateTime.now();
    }
}
