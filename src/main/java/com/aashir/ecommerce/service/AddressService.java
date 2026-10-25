package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.address.AddressRequest;
import com.aashir.ecommerce.dto.address.AddressResponse;
import com.aashir.ecommerce.entity.Address;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.repository.AddressRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addressRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses() {
        Long userId = currentUserId();

        return addressRepository.findByUserIdOrderByLastUpdateDesc(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(AddressRequest request) {
        Long userId = currentUserId();

        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES_PER_USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can save up to " + MAX_ADDRESSES_PER_USER + " addresses"
            );
        }

        Address address = new Address();
        address.setUser(entityManager.getReference(User.class, userId));
        apply(address, request);

        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long addressId, AddressRequest request) {
        Address address = findOwned(currentUserId(), addressId);
        apply(address, request);

        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Transactional
    public void deleteAddress(Long addressId) {
        addressRepository.delete(findOwned(currentUserId(), addressId));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return entityManager
                .createQuery("select u.id from User u where u.email = :email", Long.class)
                .setParameter("email", authentication.getName())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Address findOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void apply(Address address, AddressRequest request) {
        address.setAddressType(request.addressType());
        address.setArea(request.area().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPincode(request.pincode());
    }
}