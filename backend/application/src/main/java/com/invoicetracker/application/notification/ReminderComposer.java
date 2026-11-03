package com.invoicetracker.application.notification;

import com.invoicetracker.application.port.NotificationTemplateRenderer;
import com.invoicetracker.application.port.NotificationTemplateRenderer.RenderedMessage;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationKind;
import com.invoicetracker.domain.notification.ReminderStage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds outbox notifications from templates. Template choice depends on stage and channel. */
@Component
public class ReminderComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final NotificationTemplateRenderer renderer;

    public ReminderComposer(NotificationTemplateRenderer renderer) {
        this.renderer = renderer;
    }

    public Notification paymentReminder(
            Invoice invoice,
            Business business,
            ReminderStage stage,
            NotificationChannel channel,
            LocalDate today,
            Instant now) {
        String recipient = channel == NotificationChannel.SMS
                ? invoice.getCustomer().getPhone()
                : invoice.getCustomer().getEmail();
        String template = templateFor(stage) + "." + channel.name().toLowerCase(Locale.ROOT);
        RenderedMessage message = renderer.render(template, model(invoice, business, today));
        return Notification.queue(
                invoice.getBusinessId(),
                invoice.getId(),
                invoice.getNumber(),
                NotificationKind.PAYMENT_REMINDER,
                stage.code(),
                channel,
                recipient,
                message.subject(),
                message.body(),
                now);
    }

    public Notification overdueAlert(Invoice invoice, Business business, LocalDate today, Instant now) {
        RenderedMessage message = renderer.render("overdue-alert.in_app", model(invoice, business, today));
        return Notification.queue(
                invoice.getBusinessId(),
                invoice.getId(),
                invoice.getNumber(),
                NotificationKind.OVERDUE_ALERT,
                Notification.OVERDUE_ALERT_STAGE,
                NotificationChannel.IN_APP,
                business.getEmail() != null ? business.getEmail() : business.getName(),
                message.subject(),
                message.body(),
                now);
    }

    private static String templateFor(ReminderStage stage) {
        if (stage.isBeforeDue()) {
            return "reminder-upcoming";
        }
        return stage.isDueDay() ? "reminder-due-today" : "reminder-overdue";
    }

    private static Map<String, String> model(Invoice invoice, Business business, LocalDate today) {
        long daysToDue = ChronoUnit.DAYS.between(today, invoice.getDueDate());
        Map<String, String> model = new HashMap<>();
        model.put("customerName", invoice.getCustomer().getName());
        model.put("businessName", business.getName());
        model.put("businessEmail", nullToEmpty(business.getEmail()));
        model.put("businessPhone", nullToEmpty(business.getPhone()));
        model.put("invoiceNumber", invoice.getNumber());
        model.put("currency", invoice.getCurrency());
        model.put("total", invoice.getTotal().toPlainString());
        model.put("balanceDue", invoice.balanceDue().toPlainString());
        model.put("dueDate", DATE.format(invoice.getDueDate()));
        model.put("daysUntilDue", Long.toString(Math.max(0, daysToDue)));
        model.put("daysOverdue", Long.toString(Math.max(0, -daysToDue)));
        return model;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
