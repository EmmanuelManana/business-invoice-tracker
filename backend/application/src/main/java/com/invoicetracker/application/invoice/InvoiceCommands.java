package com.invoicetracker.application.invoice;

import com.invoicetracker.domain.invoice.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceCommands {

    private InvoiceCommands() {}

    /**
     * Create or update a draft. Only inputs are accepted: subtotal, VAT and total are always
     * computed on the server. {@code issueDate} defaults to today and {@code dueDate} to the issue
     * date plus the business's payment terms.
     */
    public record SaveInvoiceCommand(
            @NotNull UUID customerId,
            LocalDate issueDate,
            LocalDate dueDate,
            @Size(max = 2000) String notes,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull LineItemCommand> lineItems) {}

    public record LineItemCommand(
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @DecimalMin(value = "0.00") @DecimalMax(value = "1.00") @Digits(integer = 1, fraction = 4)
                    BigDecimal vatRate) {}

    public record RecordPaymentCommand(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotNull PaymentMethod method,
            @Size(max = 100) String reference) {}

    public record CancelInvoiceCommand(@Size(max = 500) String reason) {}
}
