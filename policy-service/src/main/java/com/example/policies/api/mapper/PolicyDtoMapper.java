package com.example.policies.api.mapper;

import com.example.policies.api.dto.PolicyResponse;
import com.example.policies.domain.Policy;
import org.springframework.stereotype.Component;

@Component
public class PolicyDtoMapper {

    public PolicyResponse toResponse(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .productCode(policy.getProductCode())
                .status(policy.getStatus())
                .validFrom(policy.getValidFrom())
                .validTo(policy.getValidTo())
                .build();
    }
}
