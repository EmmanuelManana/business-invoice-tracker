package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.dashboard.DashboardService;
import com.invoicetracker.application.dashboard.DashboardSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(CurrentUser user) {
        return service.summary(user);
    }
}
