package com.example.claims.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimJpaRepository extends JpaRepository<ClaimEntity, UUID> {

    List<ClaimEntity> findByCustomerId(UUID customerId);
}
