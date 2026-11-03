package com.invoicetracker.application.port;

import java.util.Map;

/** Renders a named message template with a model of placeholder values. */
public interface NotificationTemplateRenderer {

    RenderedMessage render(String templateName, Map<String, String> model);

    record RenderedMessage(String subject, String body) {}
}
