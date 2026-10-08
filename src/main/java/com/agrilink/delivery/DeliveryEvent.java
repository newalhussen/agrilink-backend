package com.agrilink.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Tracking timeline entry: status steps and GPS pings. */
@Entity
@Table(name = "delivery_events")
public class DeliveryEvent {

    public enum Type { CREATED, ASSIGNED, RELEASED, PICKED_UP, IN_TRANSIT, LOCATION, WEIGHT_VARIANCE, DELIVERED, CANCELLED, NOTE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private Type type;
    @Column(updatable = false)
    private Double latitude;
    @Column(updatable = false)
    private Double longitude;
    @Column(updatable = false)
    private String note;
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryEvent() {
    }

    public DeliveryEvent(UUID deliveryId, Type type, Double latitude, Double longitude, String note, UUID actorId,
                         Instant createdAt) {
        this.deliveryId = deliveryId;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.note = note;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public Type getType() { return type; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getNote() { return note; }
    public UUID getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
