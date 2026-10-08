package com.agrilink.user;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String phone;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;
    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "en";
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;
    @Column(name = "profile_photo_file_id")
    private UUID profilePhotoFileId;
    @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAverage = BigDecimal.ZERO;
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "status_reason")
    private String statusReason;

    protected User() {
    }

    public User(String phone, String fullName, Role role) {
        this.phone = phone;
        this.fullName = fullName;
        this.role = role;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(int maxFailures, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxFailures) {
            lockedUntil = lockUntil;
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void markPhoneVerified(Instant now) {
        if (!phoneVerified) {
            phoneVerified = true;
            phoneVerifiedAt = now;
        }
    }

    /** Folds a new 1-5 score into the running average. */
    public void addRating(int score) {
        BigDecimal total = ratingAverage.multiply(BigDecimal.valueOf(ratingCount)).add(BigDecimal.valueOf(score));
        ratingCount++;
        ratingAverage = total.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
    }

    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public Language language() { return Language.fromCode(preferredLanguage); }
    public boolean isPhoneVerified() { return phoneVerified; }
    public UUID getProfilePhotoFileId() { return profilePhotoFileId; }
    public void setProfilePhotoFileId(UUID profilePhotoFileId) { this.profilePhotoFileId = profilePhotoFileId; }
    public BigDecimal getRatingAverage() { return ratingAverage; }
    public int getRatingCount() { return ratingCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
}
