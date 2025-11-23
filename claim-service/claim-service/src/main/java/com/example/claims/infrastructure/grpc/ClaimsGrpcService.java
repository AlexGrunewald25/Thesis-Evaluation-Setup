package com.example.claims.infrastructure.grpc;

import com.example.claims.application.ClaimService;
import com.example.claims.domain.Claim;
import com.example.claims.grpc.*;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@GrpcService
public class ClaimsGrpcService extends ClaimsServiceGrpc.ClaimsServiceImplBase {

    private final ClaimService claimService;
    private final MeterRegistry meterRegistry;

    public ClaimsGrpcService(ClaimService claimService, MeterRegistry meterRegistry) {
        this.claimService = claimService;
        this.meterRegistry = meterRegistry;
    }

    // -------------------------------------------------------------------------
    // Helper für Metriken
    // -------------------------------------------------------------------------

    private Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    private void stopSample(Timer.Sample sample, String name, String method, String outcome) {
        sample.stop(
                Timer.builder(name)
                        .description("gRPC latency per claims RPC")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .publishPercentileHistogram(true)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        );
    }

    private void incrementCounter(String name, String method, String outcome) {
        Counter.builder(name)
                .description("gRPC request count per claims RPC")
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // RPC-Methoden
    // -------------------------------------------------------------------------

    @Override
    public void submitClaim(SubmitClaimRequest request,
                            StreamObserver<SubmitClaimResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.submitClaim(
                    UUID.fromString(request.getPolicyId()),
                    UUID.fromString(request.getCustomerId()),
                    request.getDescription(),
                    BigDecimal.valueOf(request.getReportedAmount())
            );

            SubmitClaimResponse response = SubmitClaimResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "SubmitClaim", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "SubmitClaim", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "SubmitClaim", outcome);
        }
    }

    @Override
    public void getClaim(GetClaimRequest request,
                         StreamObserver<GetClaimResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.getClaimById(UUID.fromString(request.getClaimId()));

            GetClaimResponse response = GetClaimResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "GetClaim", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "GetClaim", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "GetClaim", outcome);
        }
    }

    @Override
    public void listClaimsForCustomer(ListClaimsForCustomerRequest request,
                                      StreamObserver<ListClaimsForCustomerResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            var claims = claimService.getClaimsForCustomer(
                    UUID.fromString(request.getCustomerId())
            );

            ListClaimsForCustomerResponse.Builder builder =
                    ListClaimsForCustomerResponse.newBuilder();

            claims.forEach(c -> builder.addClaims(toProtoClaim(c)));

            incrementCounter("claims.grpc.requests", "ListClaimsForCustomer", "success");
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "ListClaimsForCustomer", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "ListClaimsForCustomer", outcome);
        }
    }

    @Override
    public void approveClaim(ApproveClaimRequest request,
                             StreamObserver<ApproveClaimResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.approveClaim(
                    UUID.fromString(request.getClaimId()),
                    BigDecimal.valueOf(request.getApprovedAmount()),
                    request.getReason()
            );

            ApproveClaimResponse response = ApproveClaimResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "ApproveClaim", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "ApproveClaim", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "ApproveClaim", outcome);
        }
    }

    @Override
    public void rejectClaim(RejectClaimRequest request,
                            StreamObserver<RejectClaimResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.rejectClaim(
                    UUID.fromString(request.getClaimId()),
                    request.getReason()
            );

            RejectClaimResponse response = RejectClaimResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "RejectClaim", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "RejectClaim", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "RejectClaim", outcome);
        }
    }

    @Override
    public void markClaimPaidOut(MarkClaimPaidOutRequest request,
                                 StreamObserver<MarkClaimPaidOutResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.payoutClaim(
                    UUID.fromString(request.getClaimId())
            );

            MarkClaimPaidOutResponse response = MarkClaimPaidOutResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "MarkClaimPaidOut", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "MarkClaimPaidOut", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "MarkClaimPaidOut", outcome);
        }
    }

    @Override
    public void startReview(StartReviewRequest request,
                            StreamObserver<StartReviewResponse> responseObserver) {

        Timer.Sample sample = startSample();
        String outcome = "success";

        try {
            Claim claim = claimService.startReview(
                    UUID.fromString(request.getClaimId())
            );

            StartReviewResponse response = StartReviewResponse.newBuilder()
                    .setClaim(toProtoClaim(claim))
                    .build();

            incrementCounter("claims.grpc.requests", "StartReview", "success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            outcome = "error";
            incrementCounter("claims.grpc.requests", "StartReview", "error");
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            stopSample(sample, "claims.grpc.latency", "StartReview", outcome);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping Domain -> Proto
    // -------------------------------------------------------------------------

    private com.example.claims.grpc.Claim toProtoClaim(Claim claim) {
        return com.example.claims.grpc.Claim.newBuilder()
                .setId(claim.getId().toString())
                .setPolicyId(claim.getPolicyId().toString())
                .setCustomerId(claim.getCustomerId().toString())
                .setDescription(claim.getDescription() != null ? claim.getDescription() : "")
                .setReportedAmount(
                        claim.getReportedAmount() != null
                                ? claim.getReportedAmount().doubleValue()
                                : 0.0
                )
                .setStatus(toProtoStatus(claim.getStatus()))
                .setApproved(claim.isApproved())
                .setApprovedAmount(
                        claim.getApprovedAmount() != null
                                ? claim.getApprovedAmount().doubleValue()
                                : 0.0
                )
                .setDecisionReason(
                        claim.getDecisionReason() != null ? claim.getDecisionReason() : ""
                )
                .setCreatedAt(toTimestamp(claim.getCreatedAt()))
                .setLastUpdatedAt(toTimestamp(claim.getLastUpdatedAt()))
                .build();
    }

    private ClaimStatus toProtoStatus(com.example.claims.domain.ClaimStatus status) {
        return switch (status) {
            case SUBMITTED -> ClaimStatus.CLAIM_STATUS_SUBMITTED;
            case IN_REVIEW -> ClaimStatus.CLAIM_STATUS_IN_REVIEW;
            case APPROVED -> ClaimStatus.CLAIM_STATUS_APPROVED;
            case REJECTED -> ClaimStatus.CLAIM_STATUS_REJECTED;
            case PAID_OUT -> ClaimStatus.CLAIM_STATUS_PAID_OUT;
        };
    }

    private Timestamp toTimestamp(OffsetDateTime odt) {
        if (odt == null) {
            return Timestamp.getDefaultInstance();
        }
        var instant = odt.toInstant();
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
