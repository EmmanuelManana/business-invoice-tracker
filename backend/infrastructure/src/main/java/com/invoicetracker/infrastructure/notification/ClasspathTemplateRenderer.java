package com.invoicetracker.infrastructure.notification;

import com.invoicetracker.application.port.NotificationTemplateRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renders plain-text templates from {@code classpath:notification-templates/<name>.txt}.
 *
 * <p>Format: the first line is {@code Subject: ...}, then a blank line, then the body.
 * Placeholders look like <code>{{customerName}}</code>. Output is plain text, so customer-supplied
 * values cannot inject markup.
 */
@Component
class ClasspathTemplateRenderer implements NotificationTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String SUBJECT_PREFIX = "Subject:";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public RenderedMessage render(String templateName, Map<String, String> model) {
        String template = cache.computeIfAbsent(templateName, ClasspathTemplateRenderer::load);
        String rendered = substitute(template, model);
        int split = rendered.indexOf('\n');
        String firstLine = split < 0 ? rendered : rendered.substring(0, split);
        if (!firstLine.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalStateException("Template " + templateName + " must start with 'Subject:'");
        }
        String subject = firstLine.substring(SUBJECT_PREFIX.length()).trim();
        String body = split < 0 ? "" : rendered.substring(split + 1).strip();
        return new RenderedMessage(subject, body);
    }

    static String substitute(String template, Map<String, String> model) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!model.containsKey(key)) {
                throw new IllegalArgumentException("No value for template placeholder '" + key + "'");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(model.get(key)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String load(String name) {
        if (!name.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid template name: " + name);
        }
        var resource = new ClassPathResource("notification-templates/" + name + ".txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("Notification template not found: " + name, e);
        }
    }
}
