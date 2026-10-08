package com.agrilink.dispute;

import com.agrilink.common.BaseEntity;
import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import com.agrilink.dispute.DisputeEnums.DisputeType;
import com.agrilink.dispute.DisputeEnums.ResolutionType;
import com.agrilink.order.Order;
import com.agrilink.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
public class Dispute extends BaseEntity {

    @Column(name = "dispute_number", nullable = false, unique = true)
    private String disputeNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;
    @Column(name = "delivery_id")
    private UUID deliveryId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_by_id")
    private User raisedBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "against_user_id")
    private User againstUser;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeType type;
    private String description;
    @Column(name = "claimed_received_quantity")
    private BigDecimal claimedReceivedQuantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status = DisputeStatus.OPEN;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(name = "assigned_admin_id")
    private UUID assignedAdminId;
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type")
    private ResolutionType resolutionType;
    @Column(name = "farmer_amount")
    private BigDecimal farmerAmount;
    @Column(name = "driver_amount")
    private BigDecimal driverAmount;
    @Column(name = "buyer_refund_amount")
    private BigDecimal buyerRefundAmount;
    @Column(name = "platform_retained_amount")
    private BigDecimal platformRetainedAmount;
    @Column(name = "resolution_notes")
    private String resolutionNotes;
    @Column(name = "resolved_by_id")
    private UUID resolvedById;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Dispute() {
    }

    public Dispute(String disputeNumber, Order order, UUID deliveryId, User raisedBy, User againstUser,
                   DisputeType type, String description, BigDecimal claimedReceivedQuantity, Instant dueAt) {
        this.disputeNumber = disputeNumber;
        this.order = order;
        this.deliveryId = deliveryId;
        this.raisedBy = raisedBy;
        this.againstUser = againstUser;
        this.type = type;
        this.description = description;
        this.claimedReceivedQuantity = claimedReceivedQuantity;
        this.dueAt = dueAt;
    }

    public void resolve(ResolutionType type, BigDecimal farmer, BigDecimal driver, BigDecimal refund,
                        BigDecimal platform, String notes, UUID adminId, Instant now) {
        this.status = DisputeStatus.RESOLVED;
        this.resolutionType = type;
        this.farmerAmount = farmer;
        this.driverAmount = driver;
        this.buyerRefundAmount = refund;
        this.platformRetainedAmount = platform;
        this.resolutionNotes = notes;
        this.resolvedById = adminId;
        this.resolvedAt = now;
    }

    public void assignTo(UUID adminId) {
        this.assignedAdminId = adminId;
        if (status == DisputeStatus.OPEN) {
            status = DisputeStatus.UNDER_REVIEW;
        }
    }

    public String getDisputeNumber() { return disputeNumber; }
    public Order getOrder() { return order; }
    public UUID getDeliveryId() { return deliveryId; }
    public User getRaisedBy() { return raisedBy; }
    public User getAgainstUser() { return againstUser; }
    public DisputeType getType() { return type; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedReceivedQuantity() { return claimedReceivedQuantity; }
    public DisputeStatus getStatus() { return status; }
    public Instant getDueAt() { return dueAt; }
    public UUID getAssignedAdminId() { return assignedAdminId; }
    public ResolutionType getResolutionType() { return resolutionType; }
    public BigDecimal getFarmerAmount() { return farmerAmount; }
    public BigDecimal getDriverAmount() { return driverAmount; }
    public BigDecimal getBuyerRefundAmount() { return buyerRefundAmount; }
    public BigDecimal getPlatformRetainedAmount() { return platformRetainedAmount; }
    public String getResolutionNotes() { return resolutionNotes; }
    public UUID getResolvedById() { return resolvedById; }
    public Instant getResolvedAt() { return resolvedAt; }
}
