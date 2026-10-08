package com.agrilink.dispute;

import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import com.agrilink.dispute.DisputeEnums.DisputeType;
import com.agrilink.dispute.DisputeEnums.EvidenceKind;
import com.agrilink.dispute.DisputeEnums.ResolutionType;
import com.agrilink.user.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DisputeDtos {

    private DisputeDtos() {
    }

    public record OpenDisputeRequest(
            @NotNull DisputeType type,
            @NotBlank @Size(max = 2000) String description,
            @DecimalMin("0.0") BigDecimal receivedQuantityKg,
            List<UUID> evidenceFileIds) {}

    public record AddEvidenceRequest(
            @NotNull EvidenceKind kind,
            UUID fileId,
            @Size(max = 1000) String note) {}

    public record ResolveDisputeRequest(
            @NotNull ResolutionType resolution,
            @DecimalMin("0.0") BigDecimal farmerAmount,
            @DecimalMin("0.0") BigDecimal driverAmount,
            @DecimalMin("0.0") BigDecimal buyerRefundAmount,
            @NotBlank @Size(max = 2000) String notes) {}

    public record PersonRef(UUID id, String fullName, Role role) {}

    public record EvidenceResponse(UUID id, EvidenceKind kind, String note, String fileUrl, PersonRef submittedBy,
                                   Instant createdAt) {}

    public record Resolution(ResolutionType type, BigDecimal farmerAmount, BigDecimal driverAmount,
                             BigDecimal buyerRefundAmount, BigDecimal platformRetainedAmount, String notes,
                             Instant resolvedAt) {}

    public record DisputeResponse(
            UUID id,
            String disputeNumber,
            UUID orderId,
            String orderNumber,
            DisputeType type,
            DisputeStatus status,
            String description,
            BigDecimal claimedReceivedQuantityKg,
            BigDecimal orderedWeightKg,
            BigDecimal totalHeldAmount,
            PersonRef raisedBy,
            PersonRef against,
            Instant dueAt,
            boolean overdue,
            UUID assignedAdminId,
            Resolution resolution,
            List<EvidenceResponse> evidence,
            Instant createdAt) {}
}
