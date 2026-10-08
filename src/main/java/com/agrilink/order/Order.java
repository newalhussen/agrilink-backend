package com.agrilink.order;

import com.agrilink.common.Address;
import com.agrilink.common.BaseEntity;
import com.agrilink.user.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id")
    private User farmer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;
    @Column(name = "subtotal_amount", nullable = false)
    private BigDecimal subtotalAmount;
    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;
    @Column(name = "platform_fee", nullable = false)
    private BigDecimal platformFee;
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;
    @Column(nullable = false)
    private String currency = "ETB";
    @Column(name = "total_weight_kg", nullable = false)
    private BigDecimal totalWeightKg;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "regionId", column = @Column(name = "delivery_region_id")),
            @AttributeOverride(name = "zone", column = @Column(name = "delivery_zone")),
            @AttributeOverride(name = "woreda", column = @Column(name = "delivery_woreda")),
            @AttributeOverride(name = "town", column = @Column(name = "delivery_town")),
            @AttributeOverride(name = "addressLine", column = @Column(name = "delivery_address_line")),
            @AttributeOverride(name = "latitude", column = @Column(name = "delivery_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "delivery_longitude"))
    })
    private Address deliveryAddress = new Address();
    @Column(name = "delivery_contact_name")
    private String deliveryContactName;
    @Column(name = "delivery_contact_phone")
    private String deliveryContactPhone;
    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;
    @Column(name = "buyer_notes")
    private String buyerNotes;

    @Column(name = "farmer_response_deadline")
    private Instant farmerResponseDeadline;
    @Column(name = "payment_deadline")
    private Instant paymentDeadline;
    @Column(name = "check_window_ends_at")
    private Instant checkWindowEndsAt;
    @Column(name = "status_reason")
    private String statusReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_dispute")
    private OrderStatus statusBeforeDispute;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "ready_at")
    private Instant readyAt;
    @Column(name = "picked_up_at")
    private Instant pickedUpAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String orderNumber, User buyer, User farmer) {
        this.orderNumber = orderNumber;
        this.buyer = buyer;
        this.farmer = farmer;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public boolean isParticipant(java.util.UUID userId) {
        return buyer.getId().equals(userId) || farmer.getId().equals(userId)
                || (driver != null && driver.getId().equals(userId));
    }

    /** Sets status and the matching timestamp. Legality is checked by {@link OrderStateMachine} beforehand. */
    void applyStatus(OrderStatus next, Instant now, String reason) {
        this.status = next;
        this.statusReason = reason;
        switch (next) {
            case ACCEPTED -> acceptedAt = now;
            case PAID -> paidAt = now;
            case READY_FOR_PICKUP -> readyAt = now;
            case PICKED_UP -> pickedUpAt = now;
            case DELIVERED -> deliveredAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED, REJECTED, EXPIRED -> cancelledAt = now;
            default -> { }
        }
    }

    public String getOrderNumber() { return orderNumber; }
    public User getBuyer() { return buyer; }
    public User getFarmer() { return farmer; }
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal v) { this.subtotalAmount = v; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal v) { this.deliveryFee = v; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal v) { this.platformFee = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalWeightKg() { return totalWeightKg; }
    public void setTotalWeightKg(BigDecimal v) { this.totalWeightKg = v; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(Address a) { this.deliveryAddress = a; }
    public String getDeliveryContactName() { return deliveryContactName; }
    public void setDeliveryContactName(String v) { this.deliveryContactName = v; }
    public String getDeliveryContactPhone() { return deliveryContactPhone; }
    public void setDeliveryContactPhone(String v) { this.deliveryContactPhone = v; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(LocalDate v) { this.requestedDeliveryDate = v; }
    public String getBuyerNotes() { return buyerNotes; }
    public void setBuyerNotes(String v) { this.buyerNotes = v; }
    public Instant getFarmerResponseDeadline() { return farmerResponseDeadline; }
    public void setFarmerResponseDeadline(Instant v) { this.farmerResponseDeadline = v; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public void setPaymentDeadline(Instant v) { this.paymentDeadline = v; }
    public Instant getCheckWindowEndsAt() { return checkWindowEndsAt; }
    public void setCheckWindowEndsAt(Instant v) { this.checkWindowEndsAt = v; }
    public String getStatusReason() { return statusReason; }
    public OrderStatus getStatusBeforeDispute() { return statusBeforeDispute; }
    public void setStatusBeforeDispute(OrderStatus v) { this.statusBeforeDispute = v; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<OrderItem> getItems() { return items; }
}
