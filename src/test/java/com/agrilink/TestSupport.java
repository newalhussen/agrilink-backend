package com.agrilink;

import com.agrilink.common.BaseEntity;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Shared helpers for unit tests: entities with ids, fixed clock and default configuration. */
public final class TestSupport {

    public static final Instant NOW = Instant.parse("2026-10-07T09:00:00Z");

    private TestSupport() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static <T extends BaseEntity> T withId(T entity, UUID id) {
        try {
            Field f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T extends BaseEntity> T withId(T entity) {
        return withId(entity, UUID.randomUUID());
    }

    public static User user(Role role, String phone, String name) {
        return withId(new User(phone, name, role));
    }

    public static AgriLinkProperties properties() {
        return new AgriLinkProperties(
                new AgriLinkProperties.Security(new AgriLinkProperties.Security.Jwt(
                        "test-secret-test-secret-test-secret-123", "agrilink", Duration.ofMinutes(15),
                        Duration.ofDays(30)), List.of("http://localhost"), 5, Duration.ofMinutes(15)),
                new AgriLinkProperties.Otp(6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60), 5, false),
                new AgriLinkProperties.Pricing(new BigDecimal("2.0"), new BigDecimal("600"), new BigDecimal("15"),
                        new BigDecimal("0.20"), new BigDecimal("500"), new BigDecimal("1.3"), new BigDecimal("100"),
                        10),
                new AgriLinkProperties.Orders(Duration.ofHours(2), Duration.ofMinutes(30), Duration.ofHours(6), 3, 3),
                new AgriLinkProperties.Disputes(Duration.ofHours(24)),
                new AgriLinkProperties.Marketplace(true),
                new AgriLinkProperties.Payment("mock", new AgriLinkProperties.Payment.Mock(true, "secret")),
                new AgriLinkProperties.Storage("./target/test-uploads", "/api/v1/files"),
                new AgriLinkProperties.Notifications(List.of("ORDER_CREATED")),
                new AgriLinkProperties.Scheduling(false, Duration.ofSeconds(60)),
                new AgriLinkProperties.Bootstrap("", "", "Admin", false));
    }
}
