package com.agrilink.dispute;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.dispute.DisputeDtos.DisputeResponse;
import com.agrilink.dispute.DisputeDtos.EvidenceResponse;
import com.agrilink.dispute.DisputeDtos.PersonRef;
import com.agrilink.dispute.DisputeDtos.Resolution;
import com.agrilink.file.FileController;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DisputeMapper {

    private final DisputeEvidenceRepository evidenceRepository;
    private final UserRepository users;
    private final String filesBasePath;
    private final Clock clock;

    public DisputeMapper(DisputeEvidenceRepository evidenceRepository, UserRepository users,
                         AgriLinkProperties properties, Clock clock) {
        this.evidenceRepository = evidenceRepository;
        this.users = users;
        this.filesBasePath = properties.storage().publicBasePath();
        this.clock = clock;
    }

    public DisputeResponse toResponse(Dispute d, boolean withEvidence) {
        List<DisputeEvidence> evidence = withEvidence
                ? evidenceRepository.findByDisputeIdOrderByCreatedAtAsc(d.getId()) : List.of();
        Map<UUID, User> people = users.findAllById(evidence.stream().map(DisputeEvidence::getSubmittedById)
                .distinct().toList()).stream().collect(Collectors.toMap(User::getId, u -> u));
        List<EvidenceResponse> evidenceResponses = evidence.stream().map(e -> new EvidenceResponse(e.getId(),
                e.getKind(), e.getNote(), e.getFileId() == null ? null : FileController.urlFor(filesBasePath, e.getFileId()),
                person(people.get(e.getSubmittedById())), e.getCreatedAt())).toList();
        Resolution resolution = d.getResolutionType() == null ? null : new Resolution(d.getResolutionType(),
                d.getFarmerAmount(), d.getDriverAmount(), d.getBuyerRefundAmount(), d.getPlatformRetainedAmount(),
                d.getResolutionNotes(), d.getResolvedAt());
        boolean overdue = d.getStatus().isOpen() && d.getDueAt().isBefore(Instant.now(clock));
        return new DisputeResponse(d.getId(), d.getDisputeNumber(), d.getOrder().getId(),
                d.getOrder().getOrderNumber(), d.getType(), d.getStatus(), d.getDescription(),
                d.getClaimedReceivedQuantity(), d.getOrder().getTotalWeightKg(), d.getOrder().getTotalAmount(),
                person(d.getRaisedBy()), person(d.getAgainstUser()), d.getDueAt(), overdue, d.getAssignedAdminId(),
                resolution, evidenceResponses, d.getCreatedAt());
    }

    private PersonRef person(User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getRole());
    }
}
