package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.UserConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserConsentHistoryRepository extends JpaRepository<UserConsentHistory, Long> {

    /**
     * 특정 사용자의 동의 변경 이력 전체, 변경 시각 오름차순(오래된 순).
     * consentItem/company를 함께 fetch해 목록 응답 매핑 시 항목별 지연 로딩(N+1)이
     * 발생하지 않도록 한다.
     */
    @Query("SELECT h FROM UserConsentHistory h "
            + "JOIN FETCH h.consentItem ci "
            + "JOIN FETCH ci.company "
            + "WHERE h.user.userId = :userId "
            + "ORDER BY h.changedAt ASC")
    List<UserConsentHistory> findByUserIdOrderByChangedAtAsc(@Param("userId") Long userId);
}