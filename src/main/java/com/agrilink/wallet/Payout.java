package com.agrilink.wallet;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A withdrawal of wallet money to the user's mobile-money or bank account. */
@Entity
@Table(name = "payouts")
public class Payout extends BaseEntity {

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutMethod method;
    @Column(name = "destination_account", nullable = false)
    private String destinationAccount;
    @Column(name = "destination_name")
    private String destinationName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status = PayoutStatus.PENDING;
    @Column(nullable = false)
    private String provider;
    @Column(name = "provider_reference")
    private String providerReference;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected Payout() {
    }

    public Payout(UUID walletId, UUID userId, BigDecimal amount, PayoutMethod method, String destinationAccount,
                  String destinationName, String provider) {
        this.walletId = walletId;
        this.userId = userId;
        this.amount = amount;
        this.method = method;
        this.destinationAccount = destinationAccount;
        this.destinationName = destinationName;
        this.provider = provider;
    }

    public void markPaid(String providerReference, Instant now) {
        this.status = PayoutStatus.PAID;
        this.providerReference = providerReference;
        this.processedAt = now;
    }

    public void markProcessing(String providerReference) {
        this.status = PayoutStatus.PROCESSING;
        this.providerReference = providerReference;
    }

    public void markFailed(String reason, Instant now) {
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = now;
    }

    public UUID getWalletId() { return walletId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public PayoutMethod getMethod() { return method; }
    public String getDestinationAccount() { return destinationAccount; }
    public String getDestinationName() { return destinationName; }
    public PayoutStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public String getFailureReason() { return failureReason; }
    public Instant getProcessedAt() { return processedAt; }
}
