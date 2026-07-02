package com.consentradar.consentradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;              // 암호화 저장 (BCrypt 예정)

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 연관관계
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserConsentCheck> userConsentChecks;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RiskScore> riskScores;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
