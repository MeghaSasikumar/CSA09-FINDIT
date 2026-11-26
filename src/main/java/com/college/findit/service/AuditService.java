package com.college.findit.service;

import com.college.findit.model.AuditLog;
import com.college.findit.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(String actor, String action, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setDetails(details);
        repo.save(entry);
    }
}
