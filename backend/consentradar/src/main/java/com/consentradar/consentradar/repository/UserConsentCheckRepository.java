package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.UserConsentCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserConsentCheckRepository extends JpaRepository<UserConsentCheck, Long> {
    Optional<UserConsentCheck> findByUser_UserIdAndConsentItem_ConsentItemId(Long userId, Long consentItemId);
}
