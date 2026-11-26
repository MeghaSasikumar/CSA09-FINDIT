package com.college.findit.repo;

import com.college.findit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop200ByOrderByCreatedAtDesc();
    List<AuditLog> findTop8ByOrderByCreatedAtDesc();
}
