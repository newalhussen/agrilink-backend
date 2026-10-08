package com.agrilink.buyer;

import com.agrilink.common.Address;
import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "buyer_profiles")
public class BuyerProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_type", nullable = false)
    private BuyerType buyerType = BuyerType.INDIVIDUAL;
    @Column(name = "business_name")
    private String businessName;
    @Column(name = "contact_person")
    private String contactPerson;
    @Column(name = "tin_number")
    private String tinNumber;
    @Column(name = "trade_license_number")
    private String tradeLicenseNumber;
    @Embedded
    private Address address = new Address();
    @Column(name = "delivery_instructions")
    private String deliveryInstructions;
    @Column(name = "completed_orders", nullable = false)
    private int completedOrders;

    protected BuyerProfile() {
    }

    public BuyerProfile(UUID userId) {
        this.userId = userId;
    }

    public void incrementCompletedOrders() {
        completedOrders++;
    }

    public UUID getUserId() { return userId; }
    public BuyerType getBuyerType() { return buyerType; }
    public void setBuyerType(BuyerType buyerType) { this.buyerType = buyerType; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getTinNumber() { return tinNumber; }
    public void setTinNumber(String tinNumber) { this.tinNumber = tinNumber; }
    public String getTradeLicenseNumber() { return tradeLicenseNumber; }
    public void setTradeLicenseNumber(String v) { this.tradeLicenseNumber = v; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String v) { this.deliveryInstructions = v; }
    public int getCompletedOrders() { return completedOrders; }
}
