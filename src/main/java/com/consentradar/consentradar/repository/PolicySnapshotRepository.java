package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.PolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot, Long> {
}