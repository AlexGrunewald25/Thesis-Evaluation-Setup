package com.example.policies.application;

import com.example.policies.domain.Policy;
import com.example.policies.infrastructure.persistence.PolicyEntity;
import com.example.policies.infrastructure.persistence.PolicyEntityMapper;
import com.example.policies.infrastructure.persistence.PolicyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyJpaRepository policyJpaRepository;
    private final PolicyEntityMapper policyEntityMapper;

    @Override
    public Optional<Policy> findById(UUID policyId) {
        return policyJpaRepository.findById(policyId)
                .map(policyEntityMapper::toDomain);
    }

    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return policyJpaRepository.findByPolicyNumber(policyNumber)
                .map(policyEntityMapper::toDomain);
    }
}
