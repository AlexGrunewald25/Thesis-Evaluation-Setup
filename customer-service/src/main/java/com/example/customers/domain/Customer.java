package com.example.customers.domain;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class Customer {

    UUID id;
    String customerNumber;
    String firstName;
    String lastName;
    String street;
    String postalCode;
    String city;
    String email;
    String phoneNumber;

    public boolean isAddressComplete() {
        return notBlank(street) && notBlank(postalCode) && notBlank(city);
    }

    public boolean isContactDataComplete() {
        // Beispiel-Logik: mindestens ein Kommunikationskanal muss vorhanden sein
        return notBlank(email) || notBlank(phoneNumber);
    }

    public boolean isCustomerDataValid() {
        return isAddressComplete() && isContactDataComplete();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
