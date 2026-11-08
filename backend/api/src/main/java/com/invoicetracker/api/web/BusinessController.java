package com.invoicetracker.api.web;

import com.invoicetracker.application.business.BusinessProfileService;
import com.invoicetracker.application.business.BusinessProfileService.BusinessDate;
import com.invoicetracker.application.business.BusinessProfileService.BusinessProfileDto;
import com.invoicetracker.application.business.BusinessProfileService.UpdateBusinessProfileCommand;
import com.invoicetracker.application.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
@Tag(name = "Business profile")
public class BusinessController {

    private final BusinessProfileService service;

    public BusinessController(BusinessProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public BusinessProfileDto get(CurrentUser user) {
        return service.get(user);
    }

    @PutMapping("/profile")
    public BusinessProfileDto update(CurrentUser user, @Valid @RequestBody UpdateBusinessProfileCommand command) {
        return service.update(user, command);
    }

    @GetMapping("/today")
    @Operation(
            summary = "Your business's current date",
            description = "Use this for date defaults and validation so the browser agrees with the server.")
    public BusinessDate today(CurrentUser user) {
        return service.today(user);
    }
}
