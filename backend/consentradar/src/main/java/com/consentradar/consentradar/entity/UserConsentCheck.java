package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_consent_check")
@Getter
@NoArgsConstructor
public class UserConsentCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checkId;

    // 사용자와 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 동의 항목과 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_item_id", nullable = false)
    private ConsentItem consentItem;

    @Column(nullable = false)
    private boolean isChecked = false;    // 동의 여부 (true = 동의, false = 미동의/철회)

    private LocalDateTime changedAt;      // 동의 상태 변경 일시

    @PrePersist
    public void prePersist() {
        this.changedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.changedAt = LocalDateTime.now();
    }
}
