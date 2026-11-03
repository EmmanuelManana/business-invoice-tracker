package com.invoicetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Lives in the top-level package so component, entity and repository scanning
 * covers the domain, application, infrastructure and api modules.
 */
@SpringBootApplication
public class InvoiceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceTrackerApplication.class, args);
    }
}
