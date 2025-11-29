package com.example.customers.api.mapper;

import com.example.customers.api.dto.CustomerResponse;
import com.example.customers.domain.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerResponseMapper {

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerResponse.builder()
                .customerNumber(customer.getCustomerNumber())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .street(customer.getStreet())
                .postalCode(customer.getPostalCode())
                .city(customer.getCity())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .addressComplete(customer.isAddressComplete())
                .contactDataComplete(customer.isContactDataComplete())
                .build();
    }
}
