package com.example.claims.infrastructure.rest;

import com.example.claims.api.dto.ApproveClaimRequest;
import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.CreateClaimRequest;
import com.example.claims.api.dto.RejectClaimRequest;
import com.example.claims.api.mapper.ClaimDtoMapper;
import com.example.claims.application.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimRestController {

    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<ClaimResponse> submitClaim(@Valid @RequestBody CreateClaimRequest request) {

        var claim = claimService.submitClaim(
                request.getPolicyId(),
                request.getCustomerId(),
                request.getDescription(),
                request.getReportedAmount()
        );

        var response = ClaimDtoMapper.toResponse(claim);

        return ResponseEntity
                .created(URI.create("/claims/" + response.getId()))
                .body(response);
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable UUID claimId) {

        var claim = claimService.getClaim(claimId);
        var response = ClaimDtoMapper.toResponse(claim);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ClaimResponse>> getClaimsForCustomer(
            @RequestParam("customerId") UUID customerId) {

        var claims = claimService.getClaimsForCustomer(customerId);
        var responses = claims.stream()
                .map(ClaimDtoMapper::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{claimId}/approve")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody ApproveClaimRequest request) {

        var claim = claimService.approveClaim(
                claimId,
                request.getApprovedAmount(),
                request.getReason()
        );
        var response = ClaimDtoMapper.toResponse(claim);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{claimId}/reject")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody RejectClaimRequest request) {

        var claim = claimService.rejectClaim(
                claimId,
                request.getReason()
        );
        var response = ClaimDtoMapper.toResponse(claim);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{claimId}/paid-out")
    public ResponseEntity<ClaimResponse> markAsPaidOut(
            @PathVariable UUID claimId) {

        var claim = claimService.markAsPaidOut(claimId);
        var response = ClaimDtoMapper.toResponse(claim);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}
