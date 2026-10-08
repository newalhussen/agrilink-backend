package com.agrilink.delivery;

import com.agrilink.common.Address;
import com.agrilink.common.ApiException;
import com.agrilink.common.BaseEntity;
import com.agrilink.common.ErrorCode;
import com.agrilink.order.Order;
import com.agrilink.user.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;

/** The transport job for one order, with the two handover codes (pickup at the farm, delivery at the buyer). */
@Entity
@Table(name = "deliveries")
public class Delivery extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.OPEN;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "regionId", column = @Column(name = "pickup_region_id")),
            @AttributeOverride(name = "zone", column = @Column(name = "pickup_zone")),
            @AttributeOverride(name = "woreda", column = @Column(name = "pickup_woreda")),
            @AttributeOverride(name = "town", column = @Column(name = "pickup_town")),
            @AttributeOverride(name = "addressLine", column = @Column(name = "pickup_address_line")),
            @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude"))
    })
    private Address pickupAddress = new Address();
    @Column(name = "pickup_contact_name")
    private String pickupContactName;
    @Column(name = "pickup_contact_phone")
    private String pickupContactPhone;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "regionId", column = @Column(name = "dropoff_region_id")),
            @AttributeOverride(name = "zone", column = @Column(name = "dropoff_zone")),
            @AttributeOverride(name = "woreda", column = @Column(name = "dropoff_woreda")),
            @AttributeOverride(name = "town", column = @Column(name = "dropoff_town")),
            @AttributeOverride(name = "addressLine", column = @Column(name = "dropoff_address_line")),
            @AttributeOverride(name = "latitude", column = @Column(name = "dropoff_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "dropoff_longitude"))
    })
    private Address dropoffAddress = new Address();
    @Column(name = "dropoff_contact_name")
    private String dropoffContactName;
    @Column(name = "dropoff_contact_phone")
    private String dropoffContactPhone;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    @Column(name = "total_weight_kg", nullable = false)
    private BigDecimal totalWeightKg;
    @Column(name = "driver_fee", nullable = false)
    private BigDecimal driverFee;
    @Column(name = "scheduled_pickup_date")
    private LocalDate scheduledPickupDate;

    @Column(name = "pickup_code", nullable = false)
    private String pickupCode;
    @Column(name = "pickup_qr_token", nullable = false, unique = true)
    private String pickupQrToken;
    @Column(name = "delivery_code", nullable = false)
    private String deliveryCode;
    @Column(name = "delivery_qr_token", nullable = false, unique = true)
    private String deliveryQrToken;
    @Column(name = "pickup_failed_attempts", nullable = false)
    private int pickupFailedAttempts;
    @Column(name = "delivery_failed_attempts", nullable = false)
    private int deliveryFailedAttempts;

    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "picked_up_at")
    private Instant pickedUpAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "picked_up_weight_kg")
    private BigDecimal pickedUpWeightKg;
    @Column(name = "picked_up_crate_count")
    private Integer pickedUpCrateCount;
    @Column(name = "pickup_note")
    private String pickupNote;
    @Column(name = "delivery_note")
    private String deliveryNote;
    @Column(name = "cancelled_reason")
    private String cancelledReason;

    protected Delivery() {
    }

    public Delivery(Order order, BigDecimal totalWeightKg, BigDecimal driverFee, String pickupCode,
                    String pickupQrToken, String deliveryCode, String deliveryQrToken) {
        this.order = order;
        this.totalWeightKg = totalWeightKg;
        this.driverFee = driverFee;
        this.pickupCode = pickupCode;
        this.pickupQrToken = pickupQrToken;
        this.deliveryCode = deliveryCode;
        this.deliveryQrToken = deliveryQrToken;
    }

    /** Pickup handover check against the code (or QR token) the farmer shows. Counts failures. */
    public void verifyPickupCode(String presented, int maxAttempts) {
        if (pickupFailedAttempts >= maxAttempts) {
            throw new ApiException(ErrorCode.HANDOVER_LOCKED,
                    "Too many wrong codes. Contact AgriLink support to unlock this pickup.");
        }
        if (!matches(presented, pickupCode, pickupQrToken)) {
            pickupFailedAttempts++;
            throw new ApiException(ErrorCode.INVALID_HANDOVER_CODE, "That pickup code is not correct");
        }
    }

    public void verifyDeliveryCode(String presented, int maxAttempts) {
        if (deliveryFailedAttempts >= maxAttempts) {
            throw new ApiException(ErrorCode.HANDOVER_LOCKED,
                    "Too many wrong codes. Contact AgriLink support to unlock this delivery.");
        }
        if (!matches(presented, deliveryCode, deliveryQrToken)) {
            deliveryFailedAttempts++;
            throw new ApiException(ErrorCode.INVALID_HANDOVER_CODE, "That delivery code is not correct");
        }
    }

    public void resetCodeAttempts() {
        pickupFailedAttempts = 0;
        deliveryFailedAttempts = 0;
    }

    private static boolean matches(String presented, String code, String qrToken) {
        if (presented == null) {
            return false;
        }
        String candidate = presented.trim().replace(" ", "");
        byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(bytes, code.getBytes(StandardCharsets.UTF_8))
                || MessageDigest.isEqual(bytes, qrToken.getBytes(StandardCharsets.UTF_8));
    }

    public Order getOrder() { return order; }
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public Address getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(Address a) { this.pickupAddress = a; }
    public String getPickupContactName() { return pickupContactName; }
    public void setPickupContactName(String v) { this.pickupContactName = v; }
    public String getPickupContactPhone() { return pickupContactPhone; }
    public void setPickupContactPhone(String v) { this.pickupContactPhone = v; }
    public Address getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(Address a) { this.dropoffAddress = a; }
    public String getDropoffContactName() { return dropoffContactName; }
    public void setDropoffContactName(String v) { this.dropoffContactName = v; }
    public String getDropoffContactPhone() { return dropoffContactPhone; }
    public void setDropoffContactPhone(String v) { this.dropoffContactPhone = v; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public BigDecimal getTotalWeightKg() { return totalWeightKg; }
    public BigDecimal getDriverFee() { return driverFee; }
    public LocalDate getScheduledPickupDate() { return scheduledPickupDate; }
    public void setScheduledPickupDate(LocalDate v) { this.scheduledPickupDate = v; }
    public String getPickupCode() { return pickupCode; }
    public String getPickupQrToken() { return pickupQrToken; }
    public String getDeliveryCode() { return deliveryCode; }
    public String getDeliveryQrToken() { return deliveryQrToken; }
    public int getPickupFailedAttempts() { return pickupFailedAttempts; }
    public int getDeliveryFailedAttempts() { return deliveryFailedAttempts; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant v) { this.assignedAt = v; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(Instant v) { this.pickedUpAt = v; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant v) { this.deliveredAt = v; }
    public BigDecimal getPickedUpWeightKg() { return pickedUpWeightKg; }
    public void setPickedUpWeightKg(BigDecimal v) { this.pickedUpWeightKg = v; }
    public Integer getPickedUpCrateCount() { return pickedUpCrateCount; }
    public void setPickedUpCrateCount(Integer v) { this.pickedUpCrateCount = v; }
    public String getPickupNote() { return pickupNote; }
    public void setPickupNote(String v) { this.pickupNote = v; }
    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String v) { this.deliveryNote = v; }
    public String getCancelledReason() { return cancelledReason; }
    public void setCancelledReason(String v) { this.cancelledReason = v; }
}
