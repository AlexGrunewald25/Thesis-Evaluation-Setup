package com.example.claims.infrastructure.customer;

import com.example.customers.grpc.CustomerServiceGrpc;
import com.example.customers.grpc.CustomerValidationRequest;
import com.example.customers.grpc.CustomerValidationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("grpc")
public class GrpcCustomerClient implements CustomerClient, DisposableBean {

    private final ManagedChannel channel;
    private final CustomerServiceGrpc.CustomerServiceBlockingStub stub;

    public GrpcCustomerClient(
            @Value("${customer.grpc.host:localhost}") String host,
            @Value("${customer.grpc.port:9192}") int port) {

        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        this.stub = CustomerServiceGrpc.newBlockingStub(channel);
        log.info("Initialized GrpcCustomerClient for {}:{}", host, port);
    }

    @Override
    public boolean isCustomerDataValid(UUID customerId) {
        // Achtung: Wir verwenden hier aktuell die UUID als "customer_number".
        // Wenn du später eine echte Kundennummer im Claim hast, kannst du hier
        // direkt customerNumber übergeben.
        CustomerValidationRequest request = CustomerValidationRequest.newBuilder()
                .setCustomerNumber(customerId.toString())
                .build();

        try {
            log.info("GrpcCustomerClient.isCustomerDataValid({}) called", customerId);
            CustomerValidationResponse response = stub.isCustomerDataValid(request);
            boolean valid = response.getValid();
            log.info("Customer validation result via gRPC: {}", valid);
            return valid;
        } catch (StatusRuntimeException ex) {
            log.error("gRPC error in GrpcCustomerClient for customerId {}: {}", customerId, ex.getStatus(), ex);
            return false;
        } catch (Exception ex) {
            log.error("Unexpected error in GrpcCustomerClient for customerId {}: {}", customerId, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public void destroy() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
