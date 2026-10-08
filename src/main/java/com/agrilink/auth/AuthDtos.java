package com.agrilink.auth;

import com.agrilink.user.Role;
import com.agrilink.user.UserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String phone,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotNull Role role,
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @Pattern(regexp = "en|am|om", message = "Language must be en, am or om") String preferredLanguage) {}

    public record RegisterResponse(UUID userId, String phone, int otpExpiresInSeconds, int resendAfterSeconds,
                                   String devOtp) {}

    public record OtpRequest(@NotBlank String phone, @NotNull OtpPurpose purpose) {}

    public record OtpRequestResponse(int expiresInSeconds, int resendAfterSeconds, String devOtp) {}

    public record OtpVerifyRequest(@NotBlank String phone, @NotBlank @Size(min = 4, max = 10) String code,
                                   @NotNull OtpPurpose purpose) {}

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record ChangePasswordRequest(String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record ResetPasswordRequest(@NotBlank String phone, @NotBlank String code,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                               UserResponse user) {}
}
