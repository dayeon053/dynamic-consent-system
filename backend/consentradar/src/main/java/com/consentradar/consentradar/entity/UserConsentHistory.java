package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 동의 상태가 바뀔 때마다 남기는 append-only 변경 이력.
 *
 * {@link UserConsentCheck}는 (user, consentItem) 조합당 단일 row로 "현재 상태"만 보관하고
 * changedAt도 매번 덮어써서 이력이 남지 않는다. UserConsentCheck를 현재 상태 조회용으로
 * 그대로 두고(개인 맞춤 위험도 계산 로직이 이 구조에 의존하므로 회귀 위험을 피하기 위함),
 * 상태가 바뀔 때마다 이 테이블에 새 row를 하나 추가하는 방식으로 이력을 남긴다
 * (기록은 {@link com.consentradar.consentradar.consenthistory.UserConsentHistoryRecorder} 담당).
 */
@Entity
@Table(name = "user_consent_history")
@Getter
@Setter
@NoArgsConstructor
public class UserConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_item_id", nullable = false)
    private ConsentItem consentItem;

    @Column(nullable = false)
    private boolean isChecked;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    public void prePersist() {
        this.changedAt = LocalDateTime.now();
    }
}