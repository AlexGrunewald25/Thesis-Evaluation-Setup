package com.example.claims.infrastructure.rest;

import com.example.claims.api.dto.ClaimApproveRequest;
import com.example.claims.api.dto.ClaimCreateRequest;
import com.example.claims.api.dto.ClaimRejectRequest;
import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.mapper.ClaimDtoMapper;
import com.example.claims.application.ClaimService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimRestController {

    private final ClaimService claimService;
    private final ClaimDtoMapper claimDtoMapper;
    private final MeterRegistry meterRegistry;

    // --- Helper für Metriken -------------------------------------------------

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String name, String method, String outcome) {
        sample.stop(
                Timer.builder(name)
                        .description("REST latency per claims endpoint")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String name, String method, String outcome) {
        Counter.builder(name)
                .description("REST request count per claims endpoint")
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // --- Endpunkte -----------------------------------------------------------

    /**
     * POST /claims – neuen Schadenfall anlegen.
     */
    @PostMapping
    public ResponseEntity<ClaimResponse> submitClaim(
            @RequestBody @Valid ClaimCreateRequest request) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.submitClaim(
                    request.getPolicyId(),
                    request.getCustomerId(),
                    request.getDescription(),
                    request.getReportedAmount()
            );

            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "submitClaim", "success");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "submitClaim", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "submitClaim", outcome);
        }
    }

    /**
     * GET /claims/{id} – Einzelnen Claim abrufen.
     */
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable UUID claimId) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.getClaimById(claimId);
            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "getClaim", "success");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "getClaim", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "getClaim", outcome);
        }
    }

    /**
     * GET /claims?customerId=... – alle Claims eines Kunden.
     */
    @GetMapping
    public ResponseEntity<List<ClaimResponse>> listClaimsForCustomer(
            @RequestParam("customerId") UUID customerId) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claims = claimService.getClaimsForCustomer(customerId);

            List<ClaimResponse> responses = claims.stream()
                    .map(claimDtoMapper::toResponse)
                    .toList();

            incrementCounter("claims.rest.requests", "listClaimsForCustomer", "success");
            return ResponseEntity.ok(responses);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "listClaimsForCustomer", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "listClaimsForCustomer", outcome);
        }
    }

    /**
     * POST /claims/{id}/review – Claim in Prüfungsstatus überführen.
     */
    @PostMapping("/{claimId}/review")
    public ResponseEntity<ClaimResponse> startReview(@PathVariable UUID claimId) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.startReview(claimId);
            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "startReview", "success");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "startReview", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "startReview", outcome);
        }
    }

    /**
     * POST /claims/{id}/approve – Claim genehmigen.
     */
    @PostMapping("/{claimId}/approve")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable UUID claimId,
            @RequestBody @Valid ClaimApproveRequest request) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.approveClaim(
                    claimId,
                    request.getApprovedAmount(),  // BigDecimal
                    request.getReason()
            );

            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "approveClaim", "success");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "approveClaim", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "approveClaim", outcome);
        }
    }

    /**
     * POST /claims/{id}/reject – Claim ablehnen.
     */
    @PostMapping("/{claimId}/reject")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable UUID claimId,
            @RequestBody @Valid ClaimRejectRequest request) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.rejectClaim(
                    claimId,
                    request.getReason()
            );

            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "rejectClaim", "success");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "rejectClaim", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "rejectClaim", outcome);
        }
    }

    /**
     * POST /claims/{id}/payout – Claim auszahlen.
     */
    @PostMapping("/{claimId}/payout")
    public ResponseEntity<ClaimResponse> payoutClaim(@PathVariable UUID claimId) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claim = claimService.payoutClaim(claimId);
            ClaimResponse response = claimDtoMapper.toResponse(claim);

            incrementCounter("claims.rest.requests", "payoutClaim", "success");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.rest.requests", "payoutClaim", "error");
            throw ex;
        } finally {
            stopSample(sample, "claims.rest.latency", "payoutClaim", outcome);
        }
    }
}
