package com.agrilink.auth;

import com.agrilink.auth.AuthDtos.AuthResponse;
import com.agrilink.auth.AuthDtos.ChangePasswordRequest;
import com.agrilink.auth.AuthDtos.LoginRequest;
import com.agrilink.auth.AuthDtos.LogoutRequest;
import com.agrilink.auth.AuthDtos.OtpRequest;
import com.agrilink.auth.AuthDtos.OtpRequestResponse;
import com.agrilink.auth.AuthDtos.OtpVerifyRequest;
import com.agrilink.auth.AuthDtos.RefreshRequest;
import com.agrilink.auth.AuthDtos.RegisterRequest;
import com.agrilink.auth.AuthDtos.RegisterResponse;
import com.agrilink.auth.AuthDtos.ResetPasswordRequest;
import com.agrilink.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Creates an account (FARMER, BUYER or DRIVER) and texts a verification code. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    /** Requests a code for REGISTER (resend), LOGIN or PASSWORD_RESET. */
    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
        return auth.requestOtp(request.phone(), request.purpose());
    }

    /** Verifies a REGISTER or LOGIN code and signs the user in. */
    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        return auth.verifyOtp(request.phone(), request.code(), request.purpose(), userAgent(http), ip(http));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request.phone(), request.password(), userAgent(http), ip(http));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return auth.refresh(request.refreshToken(), userAgent(http), ip(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        auth.logout(request.refreshToken());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(AuthContext.userId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.phone(), request.code(), request.newPassword());
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
