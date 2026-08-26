package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.ConsentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentItemRepository extends JpaRepository<ConsentItem, Long> {
    long countByCompany_CompanyId(Long companyId);
    List<ConsentItem> findByCompany_CompanyId(Long companyId);
    /** 위험도 계산·동의 항목 목록 조회는 반드시 이 메서드로 소프트 삭제된(active=false) 항목을 제외해야 한다. */
    List<ConsentItem> findByCompany_CompanyIdAndActiveTrue(Long companyId);
    Optional<ConsentItem> findByCompany_CompanyIdAndItemName(Long companyId, String itemName);
}
