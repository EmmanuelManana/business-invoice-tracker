package com.invoicetracker.infrastructure.security;

import com.invoicetracker.application.auth.AuthService;
import com.invoicetracker.application.port.AccessTokenIssuer;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    /** Custom claim carrying the tenant (business) id. */
    public static final String BUSINESS_CLAIM = "bid";

    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;

    JwtAccessTokenIssuer(
            JwtEncoder encoder, AppProperties properties, @Qualifier(AuthService.SYSTEM_CLOCK) Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.auth().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.auth().issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(BUSINESS_CLAIM, user.getBusinessId().toString())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
