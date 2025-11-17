package com.example.claims.application;

import com.example.claims.domain.Claim;
import com.example.claims.infrastructure.persistence.ClaimEntity;
import com.example.claims.infrastructure.persistence.ClaimEntityMapper;
import com.example.claims.infrastructure.persistence.ClaimJpaRepository;
import com.example.claims.support.error.ClaimNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

    private final ClaimJpaRepository claimJpaRepository;

    @Override
    public Claim submitClaim(UUID policyId,
                             UUID customerId,
                             String description,
                             double reportedAmount) {

        OffsetDateTime now = OffsetDateTime.now();

        Claim claim = Claim.newSubmittedClaim(
                policyId,
                customerId,
                description,
                reportedAmount,
                now
        );

        ClaimEntity saved = claimJpaRepository.save(ClaimEntityMapper.toEntity(claim));
        return ClaimEntityMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Claim getClaim(UUID claimId) {
        return claimJpaRepository.findById(claimId)
                .map(ClaimEntityMapper::toDomain)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Claim> getClaimsForCustomer(UUID customerId) {
        return claimJpaRepository.findByCustomerId(customerId)
                .stream()
                .map(ClaimEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Claim approveClaim(UUID claimId,
                              double approvedAmount,
                              String reason) {

        OffsetDateTime now = OffsetDateTime.now();

        ClaimEntity entity = claimJpaRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        Claim claim = ClaimEntityMapper.toDomain(entity);
        claim.approve(approvedAmount, reason, now);

        ClaimEntity updated = claimJpaRepository.save(ClaimEntityMapper.toEntity(claim));
        return ClaimEntityMapper.toDomain(updated);
    }

    @Override
    public Claim rejectClaim(UUID claimId,
                             String reason) {

        OffsetDateTime now = OffsetDateTime.now();

        ClaimEntity entity = claimJpaRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        Claim claim = ClaimEntityMapper.toDomain(entity);
        claim.reject(reason, now);

        ClaimEntity updated = claimJpaRepository.save(ClaimEntityMapper.toEntity(claim));
        return ClaimEntityMapper.toDomain(updated);
    }

    @Override
    public Claim markAsPaidOut(UUID claimId) {

        OffsetDateTime now = OffsetDateTime.now();

        ClaimEntity entity = claimJpaRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        Claim claim = ClaimEntityMapper.toDomain(entity);
        claim.markPaidOut(now);

        ClaimEntity updated = claimJpaRepository.save(ClaimEntityMapper.toEntity(claim));
        return ClaimEntityMapper.toDomain(updated);
    }
}
