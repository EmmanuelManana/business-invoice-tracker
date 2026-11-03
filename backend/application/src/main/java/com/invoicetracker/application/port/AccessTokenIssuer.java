package com.invoicetracker.application.port;

import com.invoicetracker.domain.user.AppUser;
import java.time.Instant;

/** Issues short-lived signed access tokens (JWT) carrying the user and tenant identity. */
public interface AccessTokenIssuer {

    IssuedToken issue(AppUser user);

    record IssuedToken(String value, Instant expiresAt) {}
}
