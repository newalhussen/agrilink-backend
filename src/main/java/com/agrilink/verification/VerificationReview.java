package com.agrilink.verification;

import com.agrilink.common.BaseEntity;
import com.agrilink.user.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** History of admin verification decisions for a user. */
@Entity
@Table(name = "verification_reviews")
public class VerificationReview extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationDecision decision;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private VerificationStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private VerificationStatus newStatus;
    private String note;

    protected VerificationReview() {
    }

    public VerificationReview(UUID userId, UUID reviewerId, VerificationDecision decision,
                              VerificationStatus previousStatus, VerificationStatus newStatus, String note) {
        this.userId = userId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
    }

    public UUID getUserId() { return userId; }
    public UUID getReviewerId() { return reviewerId; }
    public VerificationDecision getDecision() { return decision; }
    public VerificationStatus getPreviousStatus() { return previousStatus; }
    public VerificationStatus getNewStatus() { return newStatus; }
    public String getNote() { return note; }
}
