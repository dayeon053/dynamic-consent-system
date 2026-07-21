package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_snapshot")
@Getter
@Setter
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

    @Column(nullable = false)
    private LocalDateTime crawledAt;      // 수집 일시 (변경 없음 감지 시 최신 레코드의 이 값만 갱신됨)

    @PrePersist
    public void prePersist() {
        this.crawledAt = LocalDateTime.now();
    }
}
