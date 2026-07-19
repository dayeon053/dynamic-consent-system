package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.ConsentItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentItemRepository extends JpaRepository<ConsentItem, Long> {
}
