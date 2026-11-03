package com.invoicetracker.api.web;

import com.invoicetracker.application.auth.AuthDtos.AuthResult;
import com.invoicetracker.application.auth.AuthDtos.LoginCommand;
import com.invoicetracker.application.auth.AuthDtos.RegisterCommand;
import com.invoicetracker.application.auth.AuthDtos.UserProfile;
import com.invoicetracker.application.auth.AuthService;
import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.infrastructure.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication. The access token is returned in the body (kept in memory by the SPA). The
 * refresh token is set as an HttpOnly cookie so JavaScript can never read it.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a business and its first user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterCommand command) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(command));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginCommand command) {
        return withRefreshCookie(HttpStatus.OK, authService.login(command));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user and business")
    public UserProfile me(CurrentUser user) {
        return authService.me(user);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        Duration maxAge = Duration.between(Instant.now(), result.refreshTokenExpiresAt());
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(result.refreshToken(), maxAge).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthResponse(result.accessToken(), "Bearer", result.accessTokenExpiresAt(), result.user()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.auth().cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserProfile user) {}
}
