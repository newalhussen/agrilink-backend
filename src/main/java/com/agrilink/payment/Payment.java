package com.agrilink.payment;

import com.agrilink.common.BaseEntity;
import com.agrilink.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One charge attempt for an order. At most one payment per order can be PENDING or HELD at a time
 * (enforced by a partial unique index).
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    @Column(nullable = false)
    private String provider;
    @Column(name = "transaction_reference", nullable = false, unique = true)
    private String transactionReference;
    @Column(name = "provider_reference")
    private String providerReference;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "goods_amount", nullable = false)
    private BigDecimal goodsAmount;
    @Column(name = "delivery_amount", nullable = false)
    private BigDecimal deliveryAmount;
    @Column(name = "platform_fee_amount", nullable = false)
    private BigDecimal platformFeeAmount;
    @Column(nullable = false)
    private String currency = Money.CURRENCY;
    @Column(name = "payer_account")
    private String payerAccount;
    @Column(name = "checkout_url")
    private String checkoutUrl;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount = Money.ZERO;
    @Column(name = "released_farmer_amount", nullable = false)
    private BigDecimal releasedFarmerAmount = Money.ZERO;
    @Column(name = "released_driver_amount", nullable = false)
    private BigDecimal releasedDriverAmount = Money.ZERO;
    @Column(name = "platform_fee_retained", nullable = false)
    private BigDecimal platformFeeRetained = Money.ZERO;

    protected Payment() {
    }

    public Payment(UUID orderId, UUID payerId, PaymentMethod method, String provider, String transactionReference,
                   BigDecimal goodsAmount, BigDecimal deliveryAmount, BigDecimal platformFeeAmount,
                   String payerAccount, Instant initiatedAt, Instant expiresAt) {
        this.orderId = orderId;
        this.payerId = payerId;
        this.method = method;
        this.provider = provider;
        this.transactionReference = transactionReference;
        this.goodsAmount = goodsAmount;
        this.deliveryAmount = deliveryAmount;
        this.platformFeeAmount = platformFeeAmount;
        this.amount = goodsAmount.add(deliveryAmount).add(platformFeeAmount);
        this.payerAccount = payerAccount;
        this.initiatedAt = initiatedAt;
        this.expiresAt = expiresAt;
    }

    public void markHeld(Instant now) {
        this.status = PaymentStatus.HELD;
        this.paidAt = now;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markCancelled(String reason) {
        this.status = PaymentStatus.CANCELLED;
        this.failureReason = reason;
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void markReleased(BigDecimal farmerAmount, BigDecimal driverAmount, BigDecimal platformRetained,
                             BigDecimal refunded, Instant now) {
        this.releasedFarmerAmount = farmerAmount;
        this.releasedDriverAmount = driverAmount;
        this.platformFeeRetained = platformRetained;
        this.refundedAmount = refunded;
        this.releasedAt = now;
        if (refunded.compareTo(amount) >= 0) {
            this.status = PaymentStatus.REFUNDED;
        } else if (refunded.signum() > 0) {
            this.status = PaymentStatus.PARTIALLY_REFUNDED;
        } else {
            this.status = PaymentStatus.RELEASED;
        }
    }

    public void setProviderResult(String providerReference, String checkoutUrl) {
        this.providerReference = providerReference;
        this.checkoutUrl = checkoutUrl;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getPayerId() { return payerId; }
    public PaymentStatus getStatus() { return status; }
    public PaymentMethod getMethod() { return method; }
    public String getProvider() { return provider; }
    public String getTransactionReference() { return transactionReference; }
    public String getProviderReference() { return providerReference; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public BigDecimal getDeliveryAmount() { return deliveryAmount; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public String getCurrency() { return currency; }
    public String getPayerAccount() { return payerAccount; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getFailureReason() { return failureReason; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public BigDecimal getReleasedFarmerAmount() { return releasedFarmerAmount; }
    public BigDecimal getReleasedDriverAmount() { return releasedDriverAmount; }
    public BigDecimal getPlatformFeeRetained() { return platformFeeRetained; }
}
