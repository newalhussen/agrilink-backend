package com.agrilink.auth;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.config.AgriLinkProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time-password lifecycle. Delivery (SMS) is the caller's job so this class stays provider-free.
 * Codes are stored hashed; attempts are limited per code and issuance is rate limited per phone.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository repository;
    private final AgriLinkProperties.Otp props;
    private final Clock clock;

    public OtpService(OtpCodeRepository repository, AgriLinkProperties properties, Clock clock) {
        this.repository = repository;
        this.props = properties.otp();
        this.clock = clock;
    }

    /** Result of issuing a code. {@code plainCode} must only reach the user via SMS (or dev responses). */
    public record Issued(String plainCode, Duration ttl, Duration resendAfter) {}

    @Transactional
    public Issued issue(String phone, OtpPurpose purpose) {
        Instant now = Instant.now(clock);
        Optional<OtpCode> latest = repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose);
        if (latest.isPresent() && latest.get().getCreatedAt().plus(props.resendCooldown()).isAfter(now)) {
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED,
                    "Please wait before requesting another code");
        }
        long lastHour = repository.countByPhoneAndPurposeAndCreatedAtAfter(phone, purpose, now.minus(Duration.ofHours(1)));
        if (lastHour >= props.maxRequestsPerHour()) {
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED, "Too many code requests. Try again later.");
        }
        repository.invalidateOutstanding(phone, purpose, now);
        String code = generateCode();
        repository.save(new OtpCode(phone, purpose, hash(phone, code), now.plus(props.ttl())));
        return new Issued(code, props.ttl(), props.resendCooldown());
    }

    /**
     * Checks a code and consumes it on success. Failed attempts are committed (no rollback) so the
     * attempt counter really limits guessing.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String phone, OtpPurpose purpose, String code) {
        Instant now = Instant.now(clock);
        OtpCode otp = repository.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));
        if (otp.isExpired(now)) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "This code has expired. Request a new one.");
        }
        if (otp.getAttempts() >= props.maxAttempts()) {
            otp.consume(now);
            throw new ApiException(ErrorCode.OTP_INVALID, "Too many wrong attempts. Request a new code.");
        }
        otp.registerAttempt();
        if (!constantTimeEquals(otp.getCodeHash(), hash(phone, code))) {
            throw new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code");
        }
        otp.consume(now);
    }

    public boolean exposeInResponse() {
        return props.exposeInResponse();
    }

    public int ttlMinutes() {
        return (int) Math.max(1, props.ttl().toMinutes());
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, props.length());
        return String.format("%0" + props.length() + "d", RANDOM.nextInt(bound));
    }

    private static String hash(String phone, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
