package com.invoicetracker.application.common;

import com.invoicetracker.domain.common.Address;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 100) String city,
        @Size(max = 100) String province,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String country) {

    public static AddressDto from(Address a) {
        return new AddressDto(
                a.getLine1(), a.getLine2(), a.getCity(), a.getProvince(), a.getPostalCode(), a.getCountry());
    }

    public static Address toDomain(AddressDto dto) {
        if (dto == null) {
            return Address.empty();
        }
        return new Address(
                Text.clean(dto.line1),
                Text.clean(dto.line2),
                Text.clean(dto.city),
                Text.clean(dto.province),
                Text.clean(dto.postalCode),
                Text.clean(dto.country));
    }
}
