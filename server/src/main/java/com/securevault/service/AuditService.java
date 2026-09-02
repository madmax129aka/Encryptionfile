package com.securevault.service;

import com.securevault.entity.AuditLog;
import com.securevault.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(Long userId, Long fileId, String action, String detail) {
        repo.save(new AuditLog(userId, fileId, action, detail));
    }

    public List<AuditLog> forUser(Long userId) {
        return repo.findByUserIdOrderByActionTimeDesc(userId);
    }
}
