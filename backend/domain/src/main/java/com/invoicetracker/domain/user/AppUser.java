package com.invoicetracker.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A person who signs in on behalf of a business. */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    @Version
    private Long version;

    protected AppUser() {}

    public static AppUser create(UUID businessId, String email, String passwordHash, String fullName, Instant now) {
        var user = new AppUser();
        user.id = UUID.randomUUID();
        user.businessId = businessId;
        user.email = normaliseEmail(email);
        user.passwordHash = passwordHash;
        user.fullName = fullName.trim();
        user.createdAt = now;
        return user;
    }

    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
