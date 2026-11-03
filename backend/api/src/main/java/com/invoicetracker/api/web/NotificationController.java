package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.notification.NotificationService;
import com.invoicetracker.application.notification.NotificationService.NotificationView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<NotificationView> list(
            CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(user, unreadOnly, Paging.of(page, size));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(CurrentUser user) {
        return Map.of("count", service.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    public NotificationView markRead(CurrentUser user, @PathVariable UUID id) {
        return service.markRead(user, id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(CurrentUser user) {
        return Map.of("updated", service.markAllRead(user));
    }
}
