package com.agrilink.verification;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_documents")
public class VerificationDocument extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocumentType docType;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;
    private String note;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected VerificationDocument() {
    }

    public VerificationDocument(UUID userId, DocumentType docType, UUID fileId, String note) {
        this.userId = userId;
        this.docType = docType;
        this.fileId = fileId;
        this.note = note;
    }

    public void review(DocumentStatus status, UUID reviewerId, Instant now) {
        this.status = status;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
    }

    public UUID getUserId() { return userId; }
    public DocumentType getDocType() { return docType; }
    public UUID getFileId() { return fileId; }
    public DocumentStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getReviewedAt() { return reviewedAt; }
}
