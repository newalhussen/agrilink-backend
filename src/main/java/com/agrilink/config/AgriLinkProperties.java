package com.agrilink.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view over the {@code agrilink.*} configuration tree. */
@ConfigurationProperties(prefix = "agrilink")
public record AgriLinkProperties(
        Security security,
        Otp otp,
        Pricing pricing,
        Orders orders,
        Disputes disputes,
        Marketplace marketplace,
        Payment payment,
        Storage storage,
        Notifications notifications,
        Scheduling scheduling,
        Bootstrap bootstrap) {

    public record Security(Jwt jwt, List<String> corsAllowedOrigins, int maxFailedLogins, Duration lockDuration) {
        public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {}
    }

    public record Otp(int length, Duration ttl, int maxAttempts, Duration resendCooldown,
                      int maxRequestsPerHour, boolean exposeInResponse) {}

    public record Pricing(BigDecimal platformFeePercent, BigDecimal deliveryBaseFee, BigDecimal deliveryPerKm,
                          BigDecimal deliveryPerKg, BigDecimal deliveryMinFee, BigDecimal roadDistanceFactor,
                          BigDecimal defaultDistanceKm, int roundingStep) {}

    public record Orders(Duration farmerResponseWindow, Duration paymentWindow, Duration checkWindow,
                         int maxActiveDeliveriesPerDriver, int maxCodeAttempts) {}

    public record Disputes(Duration resolutionTarget) {}

    public record Marketplace(boolean requireVerifiedFarmers) {}

    public record Payment(String defaultProvider, Mock mock) {
        public record Mock(boolean autoConfirm, String webhookSecret) {}
    }

    public record Storage(String localPath, String publicBasePath) {}

    public record Notifications(List<String> smsTypes) {}

    public record Scheduling(boolean enabled, Duration orderMaintenanceInterval) {}

    public record Bootstrap(String adminPhone, String adminPassword, String adminName, boolean seedDemoData) {}
}
