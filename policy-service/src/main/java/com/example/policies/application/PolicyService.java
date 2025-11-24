package com.example.policies.application;

import com.example.policies.domain.Policy;

import java.util.Optional;
import java.util.UUID;

public interface PolicyService {

    Optional<Policy> findById(UUID policyId);

    Optional<Policy> findByPolicyNumber(String policyNumber);
}
