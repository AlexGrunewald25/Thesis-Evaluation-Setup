package com.example.customers.infrastructure.grpc;

import com.example.customers.application.CustomerService;
import com.example.customers.domain.Customer;
import com.example.customers.grpc.CustomerServiceGrpc;
import com.example.customers.grpc.CustomerValidationRequest;
import com.example.customers.grpc.CustomerValidationResponse;
import com.example.customers.grpc.GetCustomerRequest;
import com.example.customers.grpc.GetCustomerResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

@GrpcService                                  // <- WICHTIG: registriert den gRPC-Service
@RequiredArgsConstructor
@Slf4j
@Profile("grpc")                              // <- nur aktiv, wenn Profil "grpc" gesetzt ist
public class CustomerGrpcService extends CustomerServiceGrpc.CustomerServiceImplBase {

    private final CustomerService customerService;

    // -------------------------------------------------------------------------
    // GetCustomer
    // -------------------------------------------------------------------------

    @Override
    public void getCustomer(
            GetCustomerRequest request,
            StreamObserver<GetCustomerResponse> responseObserver) {

        String customerNumber = request.getCustomerNumber();

        log.info("gRPC GetCustomer called for customerNumber={}", customerNumber);

        try {
            var customerOpt = customerService.findByCustomerNumber(customerNumber);

            if (customerOpt.isEmpty()) {
                log.warn("gRPC GetCustomer: no customer found for customerNumber={}", customerNumber);
                responseObserver.onError(
                        Status.NOT_FOUND
                                .withDescription("Customer not found for customerNumber=" + customerNumber)
                                .asRuntimeException()
                );
                return;
            }

            Customer customer = customerOpt.get();

            com.example.customers.grpc.Customer grpcCustomer =
                    com.example.customers.grpc.Customer.newBuilder()
                            .setId(customer.getId().toString())
                            .setCustomerNumber(customer.getCustomerNumber())
                            .setFirstName(customer.getFirstName() != null ? customer.getFirstName() : "")
                            .setLastName(customer.getLastName() != null ? customer.getLastName() : "")
                            .setStreet(customer.getStreet() != null ? customer.getStreet() : "")
                            .setPostalCode(customer.getPostalCode() != null ? customer.getPostalCode() : "")
                            .setCity(customer.getCity() != null ? customer.getCity() : "")
                            .setEmail(customer.getEmail() != null ? customer.getEmail() : "")
                            .setPhoneNumber(customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "")
                            .setAddressComplete(customer.isAddressComplete())
                            .setContactDataComplete(customer.isContactDataComplete())
                            .build();

            GetCustomerResponse response = GetCustomerResponse.newBuilder()
                    .setCustomer(grpcCustomer)
                    .build();

            log.info("gRPC GetCustomer successful for customerNumber={}", customerNumber);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            log.error(
                    "Error in gRPC GetCustomer for customerNumber {}: {}",
                    customerNumber,
                    ex.getMessage(),
                    ex
            );
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Error while processing GetCustomer")
                            .withCause(ex)
                            .asRuntimeException()
            );
        }
    }

    // -------------------------------------------------------------------------
    // IsCustomerDataValid – ID-basiert (customer_number-Feld enthält UUID)
    // -------------------------------------------------------------------------

    @Override
    public void isCustomerDataValid(
            CustomerValidationRequest request,
            StreamObserver<CustomerValidationResponse> responseObserver) {

        String customerNumberField = request.getCustomerNumber();

        log.info(
                "gRPC IsCustomerDataValid called with customer_number field='{}' (interpreted as customerId)",
                customerNumberField
        );

        try {
            UUID customerId = UUID.fromString(customerNumberField);

            boolean valid = customerService.isCustomerDataValidById(customerId);

            log.info(
                    "gRPC IsCustomerDataValid result for customerId={}: valid={}",
                    customerId,
                    valid
            );

            CustomerValidationResponse response = CustomerValidationResponse.newBuilder()
                    .setValid(valid)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            log.warn(
                    "gRPC IsCustomerDataValid received invalid UUID in customer_number field: '{}'",
                    customerNumberField,
                    ex
            );
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid customerId: " + customerNumberField)
                            .asRuntimeException()
            );
        } catch (Exception ex) {
            log.error(
                    "Error in gRPC IsCustomerDataValid for customer_number field='{}': {}",
                    customerNumberField,
                    ex.getMessage(),
                    ex
            );
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Error while processing IsCustomerDataValid")
                            .withCause(ex)
                            .asRuntimeException()
            );
        }
    }
}
