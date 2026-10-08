package com.agrilink.auth;

import com.agrilink.auth.AuthDtos.AuthResponse;
import com.agrilink.auth.AuthDtos.OtpRequestResponse;
import com.agrilink.auth.AuthDtos.RegisterRequest;
import com.agrilink.auth.AuthDtos.RegisterResponse;
import com.agrilink.buyer.BuyerProfile;
import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.common.PhoneNumbers;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.driver.DriverProfile;
import com.agrilink.driver.DriverProfileRepository;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.notification.NotificationMessages;
import com.agrilink.notification.SmsGateway;
import com.agrilink.security.JwtService;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserMapper;
import com.agrilink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, OTP and password sign-in, token refresh and password management.
 * Phone numbers are the identity; OTP is the primary proof of ownership, passwords are optional.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final FarmerProfileRepository farmerProfiles;
    private final BuyerProfileRepository buyerProfiles;
    private final DriverProfileRepository driverProfiles;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SmsGateway sms;
    private final NotificationMessages messages;
    private final UserMapper userMapper;
    private final AgriLinkProperties.Security security;
    private final Clock clock;

    public AuthService(UserRepository users, FarmerProfileRepository farmerProfiles,
                       BuyerProfileRepository buyerProfiles, DriverProfileRepository driverProfiles,
                       OtpService otpService, RefreshTokenService refreshTokens, JwtService jwtService,
                       PasswordEncoder passwordEncoder, SmsGateway sms, NotificationMessages messages,
                       UserMapper userMapper, AgriLinkProperties properties, Clock clock) {
        this.users = users;
        this.farmerProfiles = farmerProfiles;
        this.buyerProfiles = buyerProfiles;
        this.driverProfiles = driverProfiles;
        this.otpService = otpService;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.sms = sms;
        this.messages = messages;
        this.userMapper = userMapper;
        this.security = properties.security();
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.role().isSelfRegistrable()) {
            throw ApiException.forbidden("This role cannot be created through registration");
        }
        String phone = PhoneNumbers.normalize(request.phone());
        User user = users.findByPhone(phone).orElse(null);
        if (user != null && user.isPhoneVerified()) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED, "This phone number is already registered");
        }
        if (user == null) {
            user = new User(phone, request.fullName().trim(), request.role());
            user = users.save(user);
            createProfile(user);
        } else if (user.getRole() == request.role()) {
            user.setFullName(request.fullName().trim());
        } else {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED,
                    "A registration for this phone number is already in progress with a different role");
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        OtpService.Issued otp = sendOtp(user, OtpPurpose.REGISTER);
        return new RegisterResponse(user.getId(), phone, (int) otp.ttl().toSeconds(),
                (int) otp.resendAfter().toSeconds(), devCode(otp));
    }

    /** Sends a fresh code. Unknown phones get the same response for LOGIN so numbers cannot be enumerated. */
    @Transactional
    public OtpRequestResponse requestOtp(String rawPhone, OtpPurpose purpose) {
        String phone = PhoneNumbers.normalize(rawPhone);
        User user = users.findByPhone(phone).orElse(null);
        if (user == null || (purpose != OtpPurpose.REGISTER && !user.isActive())) {
            return new OtpRequestResponse(300, 60, null);
        }
        if (purpose == OtpPurpose.REGISTER && user.isPhoneVerified()) {
            return new OtpRequestResponse(300, 60, null);
        }
        OtpService.Issued otp = sendOtp(user, purpose);
        return new OtpRequestResponse((int) otp.ttl().toSeconds(), (int) otp.resendAfter().toSeconds(), devCode(otp));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyOtp(String rawPhone, String code, OtpPurpose purpose, String userAgent, String ip) {
        if (purpose == OtpPurpose.PASSWORD_RESET) {
            throw ApiException.badRequest("Use the password reset endpoint for this purpose");
        }
        String phone = PhoneNumbers.normalize(rawPhone);
        otpService.verify(phone, purpose, code);
        User user = users.findByPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));
        assertCanSignIn(user);
        user.markPhoneVerified(Instant.now(clock));
        user.registerSuccessfulLogin(Instant.now(clock));
        return tokensFor(user, userAgent, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(String rawPhone, String password, String userAgent, String ip) {
        String phone = PhoneNumbers.normalize(rawPhone);
        User user = users.findByPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Incorrect phone number or password"));
        Instant now = Instant.now(clock);
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again later or sign in with a code.");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(security.maxFailedLogins(), now.plus(security.lockDuration()));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Incorrect phone number or password");
        }
        assertCanSignIn(user);
        if (!user.isPhoneVerified()) {
            throw new ApiException(ErrorCode.PHONE_NOT_VERIFIED, "Verify your phone number with the SMS code first");
        }
        user.registerSuccessfulLogin(now);
        return tokensFor(user, userAgent, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ip) {
        RefreshTokenService.Issued rotated = refreshTokens.rotate(rawRefreshToken, userAgent, ip);
        User user = users.findById(rotated.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));
        if (!user.isActive()) {
            refreshTokens.revokeAllForUser(user.getId());
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "This account is not active");
        }
        return new AuthResponse(jwtService.issueAccessToken(user), rotated.rawToken(), "Bearer",
                jwtService.accessTokenTtl().toSeconds(), userMapper.toResponse(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getPasswordHash() != null) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawPhone, String code, String newPassword) {
        String phone = PhoneNumbers.normalize(rawPhone);
        otpService.verify(phone, OtpPurpose.PASSWORD_RESET, code);
        User user = users.findByPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.markPhoneVerified(Instant.now(clock));
        user.registerSuccessfulLogin(Instant.now(clock));
        refreshTokens.revokeAllForUser(user.getId());
    }

    private void assertCanSignIn(User user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "This account is not active. Contact AgriLink support.");
        }
    }

    private AuthResponse tokensFor(User user, String userAgent, String ip) {
        RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.getId(), userAgent, ip);
        return new AuthResponse(jwtService.issueAccessToken(user), refresh.rawToken(), "Bearer",
                jwtService.accessTokenTtl().toSeconds(), userMapper.toResponse(user));
    }

    private OtpService.Issued sendOtp(User user, OtpPurpose purpose) {
        OtpService.Issued otp = otpService.issue(user.getPhone(), purpose);
        String text = messages.render(user.language(), "otp.sms", otp.plainCode(), otpService.ttlMinutes());
        try {
            sms.send(user.getPhone(), text);
        } catch (RuntimeException ex) {
            log.error("Failed to send OTP SMS to {}", PhoneNumbers.mask(user.getPhone()), ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not send the SMS code. Please try again.");
        }
        return otp;
    }

    private String devCode(OtpService.Issued otp) {
        return otpService.exposeInResponse() ? otp.plainCode() : null;
    }

    private void createProfile(User user) {
        switch (user.getRole()) {
            case FARMER -> farmerProfiles.save(new FarmerProfile(user.getId()));
            case BUYER -> buyerProfiles.save(new BuyerProfile(user.getId()));
            case DRIVER -> driverProfiles.save(new DriverProfile(user.getId()));
            case ADMIN -> { }
        }
    }

    /** Used by the bootstrap and admin tooling: creates an already-verified account without OTP. */
    @Transactional
    public User createVerifiedUser(String phone, String fullName, Role role, String password) {
        User user = new User(PhoneNumbers.normalize(phone), fullName, role);
        user.markPhoneVerified(Instant.now(clock));
        user.setPasswordHash(passwordEncoder.encode(password));
        user = users.save(user);
        createProfile(user);
        return user;
    }
}
