package com.example.policies.infrastructure.persistence;

import com.example.policies.domain.Policy;
import org.springframework.stereotype.Component;

@Component
public class PolicyEntityMapper {

    public Policy toDomain(PolicyEntity entity) {
        return Policy.builder()
                .id(entity.getId())
                .policyNumber(entity.getPolicyNumber())
                .productCode(entity.getProductCode())
                .status(entity.getStatus())
                .validFrom(entity.getValidFrom())
                .validTo(entity.getValidTo())
                .build();
    }
}
