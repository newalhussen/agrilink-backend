package com.agrilink.notification;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** In-app notification. {@code referenceType}/{@code referenceId} let clients deep-link (ORDER, DISPUTE...). */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String body;
    @Column(name = "reference_type")
    private String referenceType;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(UUID userId, NotificationType type, String title, String body, String referenceType,
                        UUID referenceId) {
        this.userId = userId;
        this.type = type;
        this.title = title.length() > 200 ? title.substring(0, 200) : title;
        this.body = body.length() > 1000 ? body.substring(0, 1000) : body;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getReadAt() { return readAt; }
}
