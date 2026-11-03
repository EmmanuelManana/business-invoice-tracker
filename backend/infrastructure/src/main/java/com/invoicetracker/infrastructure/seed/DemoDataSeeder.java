package com.invoicetracker.infrastructure.seed;

import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.CustomerRepository;
import com.invoicetracker.application.port.InvoiceNumberGenerator;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.application.port.PasswordHasher;
import com.invoicetracker.application.port.UserRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.common.Address;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.LineItemDraft;
import com.invoicetracker.domain.invoice.PaymentMethod;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.infrastructure.config.AppProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Seeds one demo business with customers and invoices in every status, including overdue invoices
 * spread across all ageing buckets. Dates are relative to today so the demo always looks current.
 * Runs once: it does nothing if the demo user already exists.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final BigDecimal VAT = new BigDecimal("0.15");

    private final AppProperties properties;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final InvoiceNumberGenerator numbers;
    private final PasswordHasher passwordHasher;
    private final TransactionOperations tx;
    private final Clock clock;

    DemoDataSeeder(
            AppProperties properties,
            BusinessRepository businesses,
            UserRepository users,
            CustomerRepository customers,
            InvoiceRepository invoices,
            InvoiceNumberGenerator numbers,
            PasswordHasher passwordHasher,
            TransactionOperations tx,
            Clock clock) {
        this.properties = properties;
        this.businesses = businesses;
        this.users = users;
        this.customers = customers;
        this.invoices = invoices;
        this.numbers = numbers;
        this.passwordHasher = passwordHasher;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = AppUser.normaliseEmail(properties.seed().demoEmail());
        String password = properties.seed().demoPassword();
        if (password == null || password.isBlank()) {
            log.warn("Demo seeding is enabled but app.seed.demo-password is not set; skipping");
            return;
        }
        if (users.existsByEmail(email)) {
            log.info("Demo data already present; skipping seed");
            return;
        }
        tx.executeWithoutResult(status -> seed(email, password));
    }

    private void seed(String email, String password) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        Business business = Business.register("Acme Creative Studio (Pty) Ltd", "accounts@acme.co.za", now);
        business.updateProfile(
                business.getName(),
                "2019/123456/07",
                "4123456789",
                "accounts@acme.co.za",
                "+27 21 555 0100",
                new Address("12 Bree Street", "3rd Floor", "Cape Town", "Western Cape", "8001", "South Africa"),
                "ZAR",
                30,
                now);
        businesses.save(business);
        users.save(AppUser.create(business.getId(), email, passwordHasher.hash(password), "Thandi Nkosi", now));

        List<Customer> c = new ArrayList<>();
        c.add(customer(
                business,
                "Karoo Coffee Roasters",
                "accounts@karoocoffee.co.za",
                "+27 82 555 0101",
                "Graaff-Reinet",
                "Eastern Cape",
                now));
        c.add(customer(
                business,
                "Table Mountain Tours",
                "finance@tmtours.co.za",
                "+27 83 555 0102",
                "Cape Town",
                "Western Cape",
                now));
        c.add(customer(business, "Ubuntu Logistics", "ap@ubuntulogistics.co.za", null, "Johannesburg", "Gauteng", now));
        c.add(customer(
                business,
                "Fynbos Wellness Spa",
                "hello@fynboswellness.co.za",
                "+27 84 555 0104",
                "Stellenbosch",
                "Western Cape",
                now));
        c.add(customer(
                business,
                "Highveld Engineering",
                "payables@highveld-eng.co.za",
                "+27 11 555 0105",
                "Pretoria",
                "Gauteng",
                now));
        c.add(customer(
                business, "Indian Ocean Traders", "accounts@iotraders.co.za", null, "Durban", "KwaZulu-Natal", now));
        c.add(customer(
                business,
                "Kalahari Solar",
                "billing@kalaharisolar.co.za",
                "+27 72 555 0107",
                "Upington",
                "Northern Cape",
                now));
        c.add(customer(
                business,
                "Drakensberg Outdoor Co.",
                "finance@drakensberg-outdoor.co.za",
                "+27 76 555 0108",
                "Bergville",
                "KwaZulu-Natal",
                now));

        UUID b = business.getId();
        // issued N days ago, payment terms, scenario
        invoice(b, c.get(0), today, 3, 30, Scenario.DRAFT, lines("Brand refresh - discovery workshop", "1", "8500.00"));
        invoice(b, c.get(1), today, 1, 14, Scenario.DRAFT, lines("Social media content pack (May)", "12", "650.00"));
        invoice(
                b,
                c.get(2),
                today,
                5,
                30,
                Scenario.SENT,
                lines("Fleet livery design", "1", "18500.00", "Vehicle mock-ups", "6", "950.00"));
        invoice(b, c.get(3), today, 12, 14, Scenario.SENT, lines("Website copywriting", "8", "1200.00"));
        invoice(
                b,
                c.get(4),
                today,
                28,
                30,
                Scenario.SENT,
                lines("Technical brochure layout", "24", "450.00", "Print management", "1", "2500.00"));
        invoice(
                b,
                c.get(5),
                today,
                20,
                30,
                Scenario.PARTIALLY_PAID,
                lines("E-commerce product photography", "40", "350.00"));
        invoice(b, c.get(6), today, 15, 30, Scenario.PAID, lines("Annual report design", "1", "32000.00"));
        invoice(b, c.get(7), today, 45, 30, Scenario.PAID, lines("Trail map illustration", "3", "4200.00"));
        invoice(b, c.get(0), today, 60, 30, Scenario.PAID, lines("Packaging design - 3 SKUs", "3", "6500.00"));
        invoice(b, c.get(1), today, 40, 30, Scenario.OVERDUE, lines("Tour brochure reprint", "1", "7800.00"));
        invoice(
                b,
                c.get(2),
                today,
                50,
                30,
                Scenario.PARTIAL_OVERDUE,
                lines("Warehouse signage", "14", "1150.00", "Installation", "1", "4500.00"));
        invoice(b, c.get(4), today, 75, 30, Scenario.OVERDUE, lines("Trade show booth graphics", "1", "24500.00"));
        invoice(b, c.get(5), today, 100, 30, Scenario.OVERDUE, lines("Catalogue photography", "60", "300.00"));
        invoice(
                b,
                c.get(3),
                today,
                140,
                30,
                Scenario.OVERDUE,
                lines("Spa menu design and print", "1", "5400.00", "Gift voucher design", "1", "1800.00"));
        invoice(
                b,
                c.get(6),
                today,
                30,
                30,
                Scenario.CANCELLED,
                lines("Investor deck (cancelled scope)", "1", "12000.00"));
        invoice(b, c.get(7), today, 27, 30, Scenario.SENT, lines("Product catalogue - autumn range", "1", "9600.00"));

        log.info("Seeded demo business '{}' with {} customers; sign in as {}", business.getName(), c.size(), email);
    }

    private Customer customer(
            Business business, String name, String email, String phone, String city, String province, Instant now) {
        return customers.save(Customer.create(
                business.getId(),
                name,
                email,
                phone,
                new Address(null, null, city, province, null, "South Africa"),
                now));
    }

    private void invoice(
            UUID businessId,
            Customer customer,
            LocalDate today,
            int issuedDaysAgo,
            int terms,
            Scenario scenario,
            List<LineItemDraft> lines) {
        LocalDate issued = today.minusDays(issuedDaysAgo);
        LocalDate due = issued.plusDays(terms);
        Instant issuedAt = at(issued);

        Invoice invoice = Invoice.createDraft(
                businessId,
                customer,
                numbers.next(businessId, issued.getYear()),
                issued,
                due,
                "ZAR",
                "Thank you for your business. Banking details: FNB, Acc 62000000000, Branch 250655.",
                lines,
                issuedAt);
        if (scenario != Scenario.DRAFT) {
            invoice.send(issuedAt.plusSeconds(3600));
        }
        BigDecimal total = invoice.getTotal();
        switch (scenario) {
            case PAID -> pay(invoice, total, issued.plusDays(Math.min(terms - 2, issuedDaysAgo)), "full");
            case PARTIALLY_PAID -> pay(invoice, half(total), issued.plusDays(7), "deposit");
            case PARTIAL_OVERDUE -> {
                pay(invoice, half(total), issued.plusDays(10), "deposit");
                invoice.markOverdueIfDue(today, at(due.plusDays(1)));
            }
            case OVERDUE -> invoice.markOverdueIfDue(today, at(due.plusDays(1)));
            case CANCELLED -> invoice.cancel("Project scope cancelled by client", at(issued.plusDays(5)));
            default -> {}
        }
        invoices.save(invoice);
    }

    private void pay(Invoice invoice, BigDecimal amount, LocalDate on, String key) {
        invoice.recordPayment(amount, on, PaymentMethod.EFT, "EFT-" + invoice.getNumber(), "seed-" + key, at(on));
    }

    private static BigDecimal half(BigDecimal total) {
        return total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
    }

    private Instant at(LocalDate date) {
        return date.atTime(LocalTime.of(9, 0)).atZone(clock.getZone()).toInstant();
    }

    private static List<LineItemDraft> lines(String... values) {
        List<LineItemDraft> result = new ArrayList<>();
        for (int i = 0; i < values.length; i += 3) {
            result.add(new LineItemDraft(values[i], new BigDecimal(values[i + 1]), new BigDecimal(values[i + 2]), VAT));
        }
        return result;
    }

    private enum Scenario {
        DRAFT,
        SENT,
        PARTIALLY_PAID,
        PARTIAL_OVERDUE,
        PAID,
        OVERDUE,
        CANCELLED
    }
}
