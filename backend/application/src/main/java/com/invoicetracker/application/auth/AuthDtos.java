package com.invoicetracker.application.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterCommand(
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank
                    @Size(min = 8, max = 128)
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                            message = "must contain an upper-case letter, a lower-case letter and a digit")
                    String password) {}

    public record LoginCommand(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {}

    public record UserProfile(UUID id, String email, String fullName, UUID businessId, String businessName) {}

    /** Result of a successful sign-in or refresh. The raw refresh token is never stored. */
    public record AuthResult(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserProfile user) {}
}
