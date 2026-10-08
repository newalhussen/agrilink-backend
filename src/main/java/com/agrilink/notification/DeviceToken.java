package com.agrilink.notification;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_tokens")
public class DeviceToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, unique = true)
    private String token;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DevicePlatform platform;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected DeviceToken() {
    }

    public DeviceToken(UUID userId, String token, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public void rebind(UUID userId, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public DevicePlatform getPlatform() { return platform; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
