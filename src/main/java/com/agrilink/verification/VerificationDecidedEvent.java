package com.agrilink.verification;

import java.util.UUID;

/** Published when an admin approves, rejects or asks for more information. */
public record VerificationDecidedEvent(UUID userId, VerificationDecision decision, String note) {
}
