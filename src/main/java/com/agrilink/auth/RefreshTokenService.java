package com.agrilink.auth;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection. Only a SHA-256 hash is stored. Presenting a
 * token that was already rotated revokes its whole family, so a stolen token cannot outlive the thief's
 * first use.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService, Clock clock) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** Raw token (returned to the client once) plus the user it belongs to. */
    public record Issued(String rawToken, UUID userId) {}

    @Transactional
    public Issued issueNewFamily(UUID userId, String userAgent, String ip) {
        return issue(userId, UUID.randomUUID(), userAgent, ip);
    }

    /** Validates and rotates; the returned token replaces the presented one. */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken, String userAgent, String ip) {
        Instant now = Instant.now(clock);
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));
        if (existing.isRevoked()) {
            repository.revokeFamily(existing.getFamilyId(), now);
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Refresh token was already used. Please sign in again.");
        }
        if (existing.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Refresh token expired. Please sign in again.");
        }
        existing.revoke(now);
        return issue(existing.getUserId(), existing.getFamilyId(), userAgent, ip);
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> repository.revokeFamily(t.getFamilyId(),
                Instant.now(clock)));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    private Issued issue(UUID userId, UUID familyId, String userAgent, String ip) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now(clock).plus(jwtService.refreshTokenTtl());
        repository.save(new RefreshToken(userId, familyId, hash(raw), expires, userAgent, ip));
        return new Issued(raw, userId);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
