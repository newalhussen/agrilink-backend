package com.agrilink.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Photo evidence captured by the driver at pickup (scale, loaded crates) or delivery. */
@Entity
@Table(name = "delivery_attachments")
public class DeliveryAttachment {

    public enum Stage { PICKUP, DELIVERY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryAttachment() {
    }

    public DeliveryAttachment(Delivery delivery, Stage stage, UUID fileId, UUID uploadedBy, Instant createdAt) {
        this.delivery = delivery;
        this.stage = stage;
        this.fileId = fileId;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    public Stage getStage() { return stage; }
    public UUID getFileId() { return fileId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
