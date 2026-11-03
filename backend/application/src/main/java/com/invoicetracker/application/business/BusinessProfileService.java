package com.invoicetracker.application.business;

import com.invoicetracker.application.common.AddressDto;
import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.common.Text;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.domain.business.Business;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final BusinessRepository businesses;
    private final Clock clock;

    public BusinessProfileService(BusinessRepository businesses, Clock clock) {
        this.businesses = businesses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessProfileDto get(CurrentUser user) {
        return BusinessProfileDto.from(load(user));
    }

    @Transactional
    public BusinessProfileDto update(CurrentUser user, UpdateBusinessProfileCommand command) {
        Business business = load(user);
        business.updateProfile(
                command.name(),
                Text.clean(command.registrationNumber()),
                Text.clean(command.vatNumber()),
                Text.clean(command.email()),
                Text.clean(command.phone()),
                AddressDto.toDomain(command.address()),
                command.currency(),
                command.paymentTermsDays(),
                clock.instant());
        return BusinessProfileDto.from(businesses.save(business));
    }

    private Business load(CurrentUser user) {
        return businesses
                .findById(user.businessId())
                .orElseThrow(() -> new NotFoundException("Business", user.businessId()));
    }

    public record UpdateBusinessProfileCommand(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 30) @Pattern(regexp = "^[0-9A-Za-z/\\- ]*$", message = "contains invalid characters")
                    String registrationNumber,
            @Pattern(regexp = "^(4\\d{9})?$", message = "must be a 10-digit South African VAT number starting with 4")
                    String vatNumber,
            @Email @Size(max = 254) String email,
            @Size(max = 30) @Pattern(regexp = "^[+0-9 ()\\-]*$", message = "must be a valid phone number") String phone,
            @Valid AddressDto address,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
            @Min(0) @Max(365) int paymentTermsDays) {}

    public record BusinessProfileDto(
            UUID id,
            String name,
            String registrationNumber,
            String vatNumber,
            String email,
            String phone,
            AddressDto address,
            String currency,
            int paymentTermsDays) {

        static BusinessProfileDto from(Business b) {
            return new BusinessProfileDto(
                    b.getId(),
                    b.getName(),
                    b.getRegistrationNumber(),
                    b.getVatNumber(),
                    b.getEmail(),
                    b.getPhone(),
                    AddressDto.from(b.getAddress()),
                    b.getCurrency(),
                    b.getPaymentTermsDays());
        }
    }
}
