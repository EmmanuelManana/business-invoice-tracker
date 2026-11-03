package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.dev.DevToolsService;
import com.invoicetracker.application.dev.DevToolsService.ClockStatus;
import com.invoicetracker.application.notification.OverdueProcessor;
import com.invoicetracker.application.notification.OverdueRunResult;
import com.invoicetracker.infrastructure.time.AdjustableClock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only endpoints to simulate time passing and run the overdue job on demand. Only registered
 * when {@code app.dev-tools.enabled=true}. Shifting the clock affects the whole instance (all
 * tenants), which is acceptable for a demo but is why this must stay off in production.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "app.dev-tools", name = "enabled", havingValue = "true")
@Tag(name = "Dev tools (demo only)")
public class DevToolsController {

    private static final Logger log = LoggerFactory.getLogger(DevToolsController.class);

    private final DevToolsService devTools;

    public DevToolsController(AdjustableClock clock, OverdueProcessor overdueProcessor) {
        this.devTools = new DevToolsService(clock, clock, overdueProcessor);
    }

    @GetMapping("/clock")
    @Operation(summary = "Current business date and simulated offset")
    public ClockStatus clock(CurrentUser user) {
        return devTools.status();
    }

    @PostMapping("/clock/advance")
    @Operation(summary = "Move the business clock forward by N days")
    public ClockStatus advance(CurrentUser user, @jakarta.validation.Valid @RequestBody AdvanceRequest request) {
        log.warn("Dev tools: user {} advanced the clock by {} day(s)", user.userId(), request.days());
        return devTools.advanceDays(request.days());
    }

    @PostMapping("/clock/reset")
    @Operation(summary = "Reset the business clock to real time")
    public ClockStatus reset(CurrentUser user) {
        log.warn("Dev tools: user {} reset the clock", user.userId());
        return devTools.reset();
    }

    @PostMapping("/overdue-check")
    @Operation(summary = "Run the overdue detection and reminder job now")
    public OverdueRunResult runOverdueCheck(CurrentUser user) {
        return devTools.runOverdueCheck();
    }

    public record AdvanceRequest(@Min(1) @Max(DevToolsService.MAX_ADVANCE_DAYS) int days) {}
}
