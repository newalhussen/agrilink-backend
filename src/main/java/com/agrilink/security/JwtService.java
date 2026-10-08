package com.agrilink.security;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived signed access tokens. Refresh tokens are opaque and handled by the auth module. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AgriLinkProperties.Security.Jwt props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AgriLinkProperties properties, Clock clock) {
        this.encoder = encoder;
        this.props = properties.security().jwt();
        this.clock = clock;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .claim("phone", user.getPhone())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration accessTokenTtl() {
        return props.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return props.refreshTokenTtl();
    }
}
