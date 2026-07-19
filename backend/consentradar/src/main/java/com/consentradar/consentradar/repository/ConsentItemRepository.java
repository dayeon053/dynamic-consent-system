package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.ConsentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentItemRepository extends JpaRepository<ConsentItem, Long> {
    List<ConsentItem> findByCompany_CompanyId(Long companyId);
}
