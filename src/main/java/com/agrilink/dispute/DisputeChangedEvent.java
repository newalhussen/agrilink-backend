package com.agrilink.dispute;

import java.util.List;
import java.util.UUID;

/** Published inside the transaction on every dispute step; parties are everyone who should hear about it. */
public record DisputeChangedEvent(
        Kind kind,
        UUID disputeId,
        String disputeNumber,
        UUID orderId,
        String orderNumber,
        UUID actorId,
        List<UUID> parties) {

    public enum Kind { OPENED, EVIDENCE_ADDED, RESOLVED }
}
