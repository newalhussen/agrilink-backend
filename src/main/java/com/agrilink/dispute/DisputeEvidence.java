package com.agrilink.dispute;

import com.agrilink.common.BaseEntity;
import com.agrilink.dispute.DisputeEnums.EvidenceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One piece of evidence: a photo, a scale reading, a crate count or a written note. */
@Entity
@Table(name = "dispute_evidence")
public class DisputeEvidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id")
    private Dispute dispute;
    @Column(name = "submitted_by_id", nullable = false)
    private UUID submittedById;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceKind kind;
    @Column(name = "file_id")
    private UUID fileId;
    private String note;

    protected DisputeEvidence() {
    }

    public DisputeEvidence(Dispute dispute, UUID submittedById, EvidenceKind kind, UUID fileId, String note) {
        this.dispute = dispute;
        this.submittedById = submittedById;
        this.kind = kind;
        this.fileId = fileId;
        this.note = note;
    }

    public Dispute getDispute() { return dispute; }
    public UUID getSubmittedById() { return submittedById; }
    public EvidenceKind getKind() { return kind; }
    public UUID getFileId() { return fileId; }
    public String getNote() { return note; }
}
