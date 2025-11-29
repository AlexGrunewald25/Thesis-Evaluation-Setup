package com.example.customers.application;

import com.example.customers.domain.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerService {

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByCustomerNumber(String customerNumber);

    /**
     * Validiert Kundendaten anhand der fachlichen Kundennummer.
     */
    boolean isCustomerDataValid(String customerNumber);

    /**
     * Validiert Kundendaten anhand der technischen ID.
     */
    boolean isCustomerDataValidById(UUID customerId);
}
