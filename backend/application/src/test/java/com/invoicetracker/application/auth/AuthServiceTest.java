package com.invoicetracker.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicetracker.application.auth.AuthDtos.AuthResult;
import com.invoicetracker.application.auth.AuthDtos.LoginCommand;
import com.invoicetracker.application.auth.AuthDtos.RegisterCommand;
import com.invoicetracker.application.common.AuthenticationFailedException;
import com.invoicetracker.application.common.ConflictException;
import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.AccessTokenIssuer;
import com.invoicetracker.application.port.PasswordHasher;
import com.invoicetracker.application.support.InMemoryRepositories;
import com.invoicetracker.application.support.MutableClock;
import com.invoicetracker.domain.user.RefreshToken;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-01T08:00:00Z"), ZoneOffset.UTC);
    private final InMemoryRepositories.Users users = new InMemoryRepositories.Users();
    private final InMemoryRepositories.RefreshTokens tokens = new InMemoryRepositories.RefreshTokens();

    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return hash.equals("hashed:" + raw);
        }
    };

    private final AccessTokenIssuer issuer = user -> new AccessTokenIssuer.IssuedToken(
            "jwt-" + user.getId() + "-" + UUID.randomUUID(), clock.instant().plusSeconds(900));

    private final AuthService service = new AuthService(
            users,
            new InMemoryRepositories.Businesses(),
            tokens,
            hasher,
            issuer,
            ApplicationSettings.defaults(),
            clock);

    private AuthResult register() {
        return service.register(new RegisterCommand("Acme", "Thandi Nkosi", "Thandi@Acme.test", "Secret123"));
    }

    @Test
    void registerCreatesBusinessAndUserWithNormalisedEmail() {
        AuthResult result = register();

        assertThat(result.user().email()).isEqualTo("thandi@acme.test");
        assertThat(result.user().businessName()).isEqualTo("Acme");
        assertThat(result.accessToken()).startsWith("jwt-");
        assertThat(result.refreshToken()).isNotBlank();
        // Only the hash is stored, never the raw token.
        assertThat(tokens.all())
                .singleElement()
                .extracting(RefreshToken::getTokenHash)
                .isNotEqualTo(result.refreshToken());
    }

    @Test
    void duplicateEmailIsRejected() {
        register();
        assertThatThrownBy(() -> service.register(new RegisterCommand("Other", "X", "thandi@acme.test", "Secret123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailFailsWithSameMessage() {
        register();

        assertThatThrownBy(() -> service.login(new LoginCommand("thandi@acme.test", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.login(new LoginCommand("nobody@acme.test", "Secret123")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
        assertThat(service.login(new LoginCommand("THANDI@acme.test", "Secret123"))
                        .accessToken())
                .isNotBlank();
    }

    @Test
    void refreshRotatesTheToken() {
        AuthResult first = register();

        AuthResult second = service.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(service.refresh(second.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void reusingARotatedTokenRevokesAllSessions() {
        AuthResult first = register();
        AuthResult second = service.refresh(first.refreshToken());

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        // The legitimate (newer) token is now revoked too.
        assertThatThrownBy(() -> service.refresh(second.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        AuthResult first = register();
        clock.advanceDays(8);

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logoutRevokesTheToken() {
        AuthResult first = register();
        service.logout(first.refreshToken());

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
