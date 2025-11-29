package com.example.customers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    private String customerNumber;
    private String firstName;
    private String lastName;

    private String street;
    private String postalCode;
    private String city;

    private String email;
    private String phoneNumber;

    private boolean addressComplete;
    private boolean contactDataComplete;
}
