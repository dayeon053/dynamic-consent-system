package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.UserConsentCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentCheckRepository extends JpaRepository<UserConsentCheck, Long> {

    Optional<UserConsentCheck> findByUser_UserIdAndConsentItem_ConsentItemId(Long userId, Long consentItemId);

    /** 특정 사용자가 특정 기업에 대해 체크해 둔 전체 동의 항목 현황 (개인 맞춤 위험도 산출용). */
    List<UserConsentCheck> findAllByUser_UserIdAndConsentItem_Company_CompanyId(Long userId, Long companyId);
}
