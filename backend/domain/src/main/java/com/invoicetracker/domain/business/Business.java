package com.invoicetracker.domain.business;

import com.invoicetracker.domain.common.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A business using the system. It is also the tenant boundary: all data is owned by one business. */
@Entity
@Table(name = "businesses")
public class Business {

    public static final String DEFAULT_CURRENCY = "ZAR";
    public static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String registrationNumber;
    private String vatNumber;
    private String email;
    private String phone;

    @Embedded
    private Address address;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int paymentTermsDays;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Business() {}

    public static Business register(String name, String email, Instant now) {
        var business = new Business();
        business.id = UUID.randomUUID();
        business.name = name.trim();
        business.email = email;
        business.address = Address.empty();
        business.currency = DEFAULT_CURRENCY;
        business.paymentTermsDays = DEFAULT_PAYMENT_TERMS_DAYS;
        business.createdAt = now;
        business.updatedAt = now;
        return business;
    }

    public void updateProfile(
            String name,
            String registrationNumber,
            String vatNumber,
            String email,
            String phone,
            Address address,
            String currency,
            int paymentTermsDays,
            Instant now) {
        if (paymentTermsDays < 0) {
            throw new IllegalArgumentException("paymentTermsDays must not be negative");
        }
        this.name = name.trim();
        this.registrationNumber = registrationNumber;
        this.vatNumber = vatNumber;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.currency = currency;
        this.paymentTermsDays = paymentTermsDays;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Address getAddress() {
        return address == null ? Address.empty() : address;
    }

    public String getCurrency() {
        return currency;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
