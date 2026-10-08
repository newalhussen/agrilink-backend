package com.agrilink.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AdminAuditLogRepository repository;
    private final Clock clock;

    public AdminAuditService(AdminAuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Joins the caller's transaction so the audit row commits or rolls back with the action itself. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID adminId, String action, String entityType, UUID entityId, String details) {
        repository.save(new AdminAuditLog(adminId, action, entityType, entityId, details, Instant.now(clock)));
    }
}
