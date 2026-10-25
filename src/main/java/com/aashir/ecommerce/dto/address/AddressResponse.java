package com.aashir.ecommerce.dto.address;

import com.aashir.ecommerce.entity.ADDRESS_TYPE;
import com.aashir.ecommerce.entity.Address;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        ADDRESS_TYPE addressType,
        String area,
        String city,
        String state,
        String pincode,
        LocalDateTime lastUpdate
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getAddressType(),
                address.getArea(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLastUpdate()
        );
    }
}