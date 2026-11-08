package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.dev.DevToolsService;
import com.invoicetracker.application.dev.DevToolsService.ClockStatus;
import com.invoicetracker.application.notification.OverdueRunResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only endpoints to simulate time passing and run the overdue job on demand. Only registered
 * when {@code app.dev-tools.enabled=true}. The clock is per business: moving it changes only the
 * caller's own business.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "app.dev-tools", name = "enabled", havingValue = "true")
@Tag(name = "Dev tools (demo only)")
public class DevToolsController {

    private final DevToolsService devTools;

    public DevToolsController(DevToolsService devTools) {
        this.devTools = devTools;
    }

    @GetMapping("/clock")
    @Operation(summary = "Your business's current date and simulated offset")
    public ClockStatus clock(CurrentUser user) {
        return devTools.status(user);
    }

    @PostMapping("/clock/advance")
    @Operation(summary = "Move your business's clock forward by N days")
    public ClockStatus advance(CurrentUser user, @Valid @RequestBody AdvanceRequest request) {
        return devTools.advanceDays(user, request.days());
    }

    @PostMapping("/clock/reset")
    @Operation(
            summary = "Reset your business's clock to real time",
            description = "Invoices that were only overdue in simulated time go back to Sent / Partially paid.")
    public ClockStatus reset(CurrentUser user) {
        return devTools.reset(user);
    }

    @PostMapping("/overdue-check")
    @Operation(summary = "Run the overdue detection and reminder job now")
    public OverdueRunResult runOverdueCheck(CurrentUser user) {
        return devTools.runOverdueCheck();
    }

    public record AdvanceRequest(@Min(1) @Max(DevToolsService.MAX_ADVANCE_DAYS) int days) {}
}
