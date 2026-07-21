package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
