package com.agrilink.driver;

import com.agrilink.common.BaseEntity;
import com.agrilink.wallet.PayoutMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "driver_profiles")
public class DriverProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Column(name = "license_number")
    private String licenseNumber;
    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    private VehicleType vehicleType;
    @Column(name = "vehicle_plate")
    private String vehiclePlate;
    @Column(name = "vehicle_make_model")
    private String vehicleMakeModel;
    @Column(name = "vehicle_year")
    private Integer vehicleYear;
    @Column(name = "capacity_kg")
    private BigDecimal capacityKg;
    @Column(nullable = false)
    private boolean refrigerated;
    @Column(name = "region_id")
    private UUID regionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverAvailability availability = DriverAvailability.OFFLINE;
    @Column(name = "current_latitude")
    private Double currentLatitude;
    @Column(name = "current_longitude")
    private Double currentLongitude;
    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method")
    private PayoutMethod payoutMethod;
    @Column(name = "payout_account_name")
    private String payoutAccountName;
    @Column(name = "payout_account_number")
    private String payoutAccountNumber;
    @Column(name = "completed_deliveries", nullable = false)
    private int completedDeliveries;

    protected DriverProfile() {
    }

    public DriverProfile(UUID userId) {
        this.userId = userId;
    }

    public void updateLocation(double lat, double lon, Instant now) {
        this.currentLatitude = lat;
        this.currentLongitude = lon;
        this.locationUpdatedAt = now;
    }

    public void incrementCompletedDeliveries() {
        completedDeliveries++;
    }

    /** A vehicle is complete when type, plate and capacity are known; needed before taking jobs. */
    public boolean hasVehicle() {
        return vehicleType != null && vehiclePlate != null && capacityKg != null;
    }

    public UUID getUserId() { return userId; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String v) { this.licenseNumber = v; }
    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate v) { this.licenseExpiryDate = v; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType v) { this.vehicleType = v; }
    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String v) { this.vehiclePlate = v; }
    public String getVehicleMakeModel() { return vehicleMakeModel; }
    public void setVehicleMakeModel(String v) { this.vehicleMakeModel = v; }
    public Integer getVehicleYear() { return vehicleYear; }
    public void setVehicleYear(Integer v) { this.vehicleYear = v; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal v) { this.capacityKg = v; }
    public boolean isRefrigerated() { return refrigerated; }
    public void setRefrigerated(boolean v) { this.refrigerated = v; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID v) { this.regionId = v; }
    public DriverAvailability getAvailability() { return availability; }
    public void setAvailability(DriverAvailability v) { this.availability = v; }
    public Double getCurrentLatitude() { return currentLatitude; }
    public Double getCurrentLongitude() { return currentLongitude; }
    public Instant getLocationUpdatedAt() { return locationUpdatedAt; }
    public PayoutMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(PayoutMethod v) { this.payoutMethod = v; }
    public String getPayoutAccountName() { return payoutAccountName; }
    public void setPayoutAccountName(String v) { this.payoutAccountName = v; }
    public String getPayoutAccountNumber() { return payoutAccountNumber; }
    public void setPayoutAccountNumber(String v) { this.payoutAccountNumber = v; }
    public int getCompletedDeliveries() { return completedDeliveries; }
}
