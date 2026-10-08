package com.agrilink.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of consequential admin actions. */
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;
    @Column(name = "entity_id", updatable = false)
    private UUID entityId;
    @Column(updatable = false)
    private String details;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(UUID adminId, String action, String entityType, UUID entityId, String details,
                         Instant createdAt) {
        this.adminId = adminId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details == null || details.length() <= 2000 ? details : details.substring(0, 2000);
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
