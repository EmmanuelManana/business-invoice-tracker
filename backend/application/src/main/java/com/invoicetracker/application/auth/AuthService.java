package com.invoicetracker.application.auth;

import com.invoicetracker.application.auth.AuthDtos.AuthResult;
import com.invoicetracker.application.auth.AuthDtos.LoginCommand;
import com.invoicetracker.application.auth.AuthDtos.RegisterCommand;
import com.invoicetracker.application.auth.AuthDtos.UserProfile;
import com.invoicetracker.application.common.AuthenticationFailedException;
import com.invoicetracker.application.common.ConflictException;
import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.AccessTokenIssuer;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.PasswordHasher;
import com.invoicetracker.application.port.RefreshTokenRepository;
import com.invoicetracker.application.port.UserRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.domain.user.RefreshToken;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Bean name of the real-time clock (as opposed to the adjustable business clock). */
    public static final String SYSTEM_CLOCK = "systemClock";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_REFRESH = "Session expired. Please sign in again.";

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokens;
    private final ApplicationSettings settings;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            BusinessRepository businesses,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokens,
            ApplicationSettings settings,
            // Sessions use real time, not the business clock the dev tools can shift.
            @Qualifier(SYSTEM_CLOCK) Clock clock) {
        this.users = users;
        this.businesses = businesses;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.settings = settings;
        this.clock = clock;
        // Used to spend the same hashing time for unknown emails, so response timing
        // does not reveal which emails are registered.
        this.dummyHash = passwordHasher.hash("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public AuthResult register(RegisterCommand command) {
        String email = AppUser.normaliseEmail(command.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("email_taken", "An account with this email already exists");
        }
        Instant now = clock.instant();
        Business business = businesses.save(Business.register(command.businessName(), email, now));
        AppUser user = users.save(AppUser.create(
                business.getId(), email, passwordHasher.hash(command.password()), command.fullName(), now));
        log.info("Registered business {} with user {}", business.getId(), user.getId());
        return issueTokens(user, business, now);
    }

    @Transactional
    public AuthResult login(LoginCommand command) {
        String email = AppUser.normaliseEmail(command.email());
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordHasher.matches(command.password(), dummyHash);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            log.warn("Failed login for user {}", user.getId());
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        Instant now = clock.instant();
        user.recordLogin(now);
        Business business = loadBusiness(user);
        return issueTokens(user, business, now);
    }

    /**
     * Rotates a refresh token. If a token that was already rotated is presented again, it has
     * probably been stolen, so every session of that user is revoked.
     *
     * <p>Exception: a token rotated only moments ago (within the configured grace period) is most
     * likely a second browser tab that refreshed at the same time. That tab gets a fresh token pair
     * of its own instead of signing the user out everywhere. Tokens revoked by sign-out never get
     * this grace.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens
                .findByTokenHash(RefreshTokens.hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));

        if (token.isRevoked() && token.wasRotatedWithin(settings.refreshReuseGrace(), now)) {
            AppUser user = users.findById(token.getUserId())
                    .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));
            log.info("Concurrent refresh for user {} within the grace period; issuing a new session", user.getId());
            return issueTokens(user, loadBusiness(user), now);
        }
        if (token.isRevoked()) {
            int revoked = refreshTokens.revokeAllForUser(token.getUserId(), now);
            log.warn(
                    "Refresh token reuse detected for user {}; revoked {} active session(s)",
                    token.getUserId(),
                    revoked);
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }
        if (token.isExpired(now)) {
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }

        AppUser user =
                users.findById(token.getUserId()).orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));
        Issued issued = issue(user, loadBusiness(user), now);
        token.revoke(now, issued.refreshToken().getId());
        refreshTokens.save(token);
        return issued.result();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(RefreshTokens.hash(rawRefreshToken)).ifPresent(token -> {
            token.revoke(clock.instant(), null);
            refreshTokens.save(token);
        });
    }

    @Transactional(readOnly = true)
    public UserProfile me(CurrentUser currentUser) {
        AppUser user = users.findById(currentUser.userId())
                .orElseThrow(() -> new NotFoundException("User", currentUser.userId()));
        return toProfile(user, loadBusiness(user));
    }

    private AuthResult issueTokens(AppUser user, Business business, Instant now) {
        return issue(user, business, now).result();
    }

    private Issued issue(AppUser user, Business business, Instant now) {
        AccessTokenIssuer.IssuedToken access = accessTokens.issue(user);
        String rawRefresh = RefreshTokens.generate();
        Instant refreshExpiresAt = now.plus(settings.refreshTokenTtl());
        RefreshToken stored = refreshTokens.save(
                RefreshToken.issue(user.getId(), RefreshTokens.hash(rawRefresh), refreshExpiresAt, now));
        var result = new AuthResult(
                access.value(), access.expiresAt(), rawRefresh, refreshExpiresAt, toProfile(user, business));
        return new Issued(result, stored);
    }

    private record Issued(AuthResult result, RefreshToken refreshToken) {}

    private Business loadBusiness(AppUser user) {
        return businesses
                .findById(user.getBusinessId())
                .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " has no business"));
    }

    private static UserProfile toProfile(AppUser user, Business business) {
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), business.getId(), business.getName());
    }
}
