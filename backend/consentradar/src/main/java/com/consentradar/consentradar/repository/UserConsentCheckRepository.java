package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.entity.UserConsentCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserConsentCheckRepository extends JpaRepository<UserConsentCheck, Long> {

    Optional<UserConsentCheck> findByUser_UserIdAndConsentItem_ConsentItemId(Long userId, Long consentItemId);

    /** 특정 사용자가 특정 기업에 대해 체크해 둔 전체 동의 항목 현황 (개인 맞춤 위험도 산출용). */
    List<UserConsentCheck> findAllByUser_UserIdAndConsentItem_Company_CompanyId(Long userId, Long companyId);

    /**
     * 특정 기업에 대해 동의 체크 이력(체크/해제 무관)이 하나라도 있는 사용자 목록(중복 제거).
     * 배치 파이프라인이 매일 밤 개인 맞춤 위험도 히스토리를 저장할 때, "이 기업을 실제로 접한
     * 사용자"만 대상으로 삼기 위해 쓴다.
     */
    @Query("SELECT DISTINCT ucc.user FROM UserConsentCheck ucc WHERE ucc.consentItem.company.companyId = :companyId")
    List<User> findDistinctUsersByConsentItem_Company_CompanyId(@Param("companyId") Long companyId);
}
