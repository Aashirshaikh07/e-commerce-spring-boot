package com.aashir.ecommerce.dto.address;

import com.aashir.ecommerce.entity.ADDRESS_TYPE;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotNull(message = "Address type is required")
        ADDRESS_TYPE addressType,

        @NotBlank(message = "Street address is required")
        @Size(min = 5, max = 255, message = "Street address must be between 5 and 255 characters")
        String area,

        @NotBlank(message = "City is required")
        @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(min = 2, max = 100, message = "State must be between 2 and 100 characters")
        String state,

        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code")
        String pincode
) {
}
