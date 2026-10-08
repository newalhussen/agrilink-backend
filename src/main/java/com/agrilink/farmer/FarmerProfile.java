package com.agrilink.farmer;

import com.agrilink.common.Address;
import com.agrilink.common.BaseEntity;
import com.agrilink.marketplace.Product;
import com.agrilink.wallet.PayoutMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "farmer_profiles")
public class FarmerProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "farmer_type", nullable = false)
    private FarmerType farmerType = FarmerType.INDIVIDUAL;
    @Column(name = "farm_name")
    private String farmName;
    @Column(name = "member_count")
    private Integer memberCount;
    @Embedded
    private Address address = new Address();
    @Column(name = "land_size_hectares")
    private BigDecimal landSizeHectares;
    @Column(nullable = false)
    private boolean irrigated;
    @Column(name = "expected_monthly_supply_kg")
    private BigDecimal expectedMonthlySupplyKg;
    private String bio;
    @Column(name = "fayda_id_number")
    private String faydaIdNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method")
    private PayoutMethod payoutMethod;
    @Column(name = "payout_account_name")
    private String payoutAccountName;
    @Column(name = "payout_account_number")
    private String payoutAccountNumber;
    @Column(name = "completed_trades", nullable = false)
    private int completedTrades;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "farmer_profile_products",
            joinColumns = @JoinColumn(name = "farmer_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> products = new HashSet<>();

    protected FarmerProfile() {
    }

    public FarmerProfile(UUID userId) {
        this.userId = userId;
    }

    public void incrementCompletedTrades() {
        completedTrades++;
    }

    public UUID getUserId() { return userId; }
    public FarmerType getFarmerType() { return farmerType; }
    public void setFarmerType(FarmerType farmerType) { this.farmerType = farmerType; }
    public String getFarmName() { return farmName; }
    public void setFarmName(String farmName) { this.farmName = farmName; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public BigDecimal getLandSizeHectares() { return landSizeHectares; }
    public void setLandSizeHectares(BigDecimal landSizeHectares) { this.landSizeHectares = landSizeHectares; }
    public boolean isIrrigated() { return irrigated; }
    public void setIrrigated(boolean irrigated) { this.irrigated = irrigated; }
    public BigDecimal getExpectedMonthlySupplyKg() { return expectedMonthlySupplyKg; }
    public void setExpectedMonthlySupplyKg(BigDecimal v) { this.expectedMonthlySupplyKg = v; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getFaydaIdNumber() { return faydaIdNumber; }
    public void setFaydaIdNumber(String faydaIdNumber) { this.faydaIdNumber = faydaIdNumber; }
    public PayoutMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(PayoutMethod payoutMethod) { this.payoutMethod = payoutMethod; }
    public String getPayoutAccountName() { return payoutAccountName; }
    public void setPayoutAccountName(String v) { this.payoutAccountName = v; }
    public String getPayoutAccountNumber() { return payoutAccountNumber; }
    public void setPayoutAccountNumber(String v) { this.payoutAccountNumber = v; }
    public int getCompletedTrades() { return completedTrades; }
    public Set<Product> getProducts() { return products; }
}
