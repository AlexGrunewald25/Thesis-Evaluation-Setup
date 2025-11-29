package com.example.customers.application;

import com.example.customers.domain.Customer;
import com.example.customers.infrastructure.persistence.CustomerEntityMapper;
import com.example.customers.infrastructure.persistence.CustomerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerEntityMapper customerEntityMapper;

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerJpaRepository.findById(id)
                .map(customerEntityMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByCustomerNumber(String customerNumber) {
        return customerJpaRepository.findByCustomerNumber(customerNumber)
                .map(customerEntityMapper::toDomain);
    }

    @Override
    public boolean isCustomerDataValid(String customerNumber) {
        return customerJpaRepository.findByCustomerNumber(customerNumber)
                .map(customerEntityMapper::toDomain)
                .map(Customer::isCustomerDataValid)
                .orElse(false);
    }

    @Override
    public boolean isCustomerDataValidById(UUID customerId) {
        return customerJpaRepository.findById(customerId)
                .map(customerEntityMapper::toDomain)
                .map(Customer::isCustomerDataValid)
                .orElse(false);
    }
}
