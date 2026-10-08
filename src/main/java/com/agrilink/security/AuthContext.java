package com.agrilink.security;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.user.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Read access to the authenticated caller, derived from the validated access-token claims. */
public final class AuthContext {

    private AuthContext() {
    }

    public static Optional<UUID> optionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
            } catch (IllegalArgumentException | NullPointerException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static UUID userId() {
        return optionalUserId().orElseThrow(() ->
                new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
    }

    public static Role role() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String role = jwt.getToken().getClaimAsString("role");
            if (role != null) {
                return Role.valueOf(role);
            }
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
    }

    public static boolean isAdmin() {
        try {
            return role() == Role.ADMIN;
        } catch (ApiException ex) {
            return false;
        }
    }
}
