package com.agrilink.user;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.file.FileController;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final String filesBasePath;

    public UserMapper(AgriLinkProperties properties) {
        this.filesBasePath = properties.storage().publicBasePath();
    }

    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getPhone(), u.getEmail(), u.getFullName(), u.getRole(),
                u.getAccountStatus(), u.getVerificationStatus(), u.isPhoneVerified(), u.getPreferredLanguage(),
                u.getProfilePhotoFileId() == null ? null
                        : FileController.urlFor(filesBasePath, u.getProfilePhotoFileId()),
                u.getRatingAverage(), u.getRatingCount(), u.getCreatedAt());
    }

    /** Minimal public identity shown to other parties of a trade. */
    public record PartySummary(java.util.UUID id, String fullName, Role role, String phone,
                               java.math.BigDecimal ratingAverage, int ratingCount, boolean verified,
                               String profilePhotoUrl) {}

    public PartySummary toParty(User u, boolean includePhone) {
        return new PartySummary(u.getId(), u.getFullName(), u.getRole(), includePhone ? u.getPhone() : null,
                u.getRatingAverage(), u.getRatingCount(), u.isVerified(),
                u.getProfilePhotoFileId() == null ? null
                        : FileController.urlFor(filesBasePath, u.getProfilePhotoFileId()));
    }
}
