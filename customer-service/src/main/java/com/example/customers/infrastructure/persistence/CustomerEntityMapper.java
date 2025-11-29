package com.example.customers.infrastructure.persistence;

import com.example.customers.domain.Customer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerEntityMapper {

    public Customer toDomain(CustomerEntity entity) {
        if (entity == null) {
            return null;
        }
        return Customer.builder()
                .id(entity.getId())
                .customerNumber(entity.getCustomerNumber())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .street(entity.getStreet())
                .postalCode(entity.getPostalCode())
                .city(entity.getCity())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .build();
    }

    public CustomerEntity toEntity(Customer customer) {
        if (customer == null) {
            return null;
        }

        UUID id = customer.getId();
        if (id == null) {
            id = UUID.randomUUID();
        }

        return new CustomerEntity(
                id,
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getStreet(),
                customer.getPostalCode(),
                customer.getCity(),
                customer.getEmail(),
                customer.getPhoneNumber()
        );
    }
}
