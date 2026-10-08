package com.agrilink.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Account view returned to clients. Role-specific details live under /farmers/me, /buyers/me, /drivers/me. */
public record UserResponse(
        UUID id,
        String phone,
        String email,
        String fullName,
        Role role,
        AccountStatus accountStatus,
        VerificationStatus verificationStatus,
        boolean phoneVerified,
        String preferredLanguage,
        String profilePhotoUrl,
        BigDecimal ratingAverage,
        int ratingCount,
        Instant createdAt) {
}
