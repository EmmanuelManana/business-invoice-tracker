package com.invoicetracker.domain.customer;

import com.invoicetracker.domain.common.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A client of a business that receives invoices. */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Embedded
    private Address billingAddress;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Customer() {}

    public static Customer create(
            UUID businessId, String name, String email, String phone, Address billingAddress, Instant now) {
        var customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.businessId = businessId;
        customer.createdAt = now;
        customer.apply(name, email, phone, billingAddress, now);
        return customer;
    }

    public void update(String name, String email, String phone, Address billingAddress, Instant now) {
        apply(name, email, phone, billingAddress, now);
    }

    private void apply(String name, String email, String phone, Address billingAddress, Instant now) {
        this.name = name.trim();
        this.email = email.trim();
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.billingAddress = billingAddress;
        this.updatedAt = now;
    }

    public boolean hasPhone() {
        return phone != null && !phone.isBlank();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Address getBillingAddress() {
        return billingAddress == null ? Address.empty() : billingAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
