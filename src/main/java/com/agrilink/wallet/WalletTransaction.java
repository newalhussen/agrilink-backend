package com.agrilink.wallet;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Immutable ledger line; {@code balanceAfter} makes the wallet history auditable on its own. */
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction extends BaseEntity {

    public enum Type { ESCROW_RELEASE, WITHDRAWAL, WITHDRAWAL_REVERSAL, ADJUSTMENT }

    public enum Direction { CREDIT, DEBIT }

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "payout_id")
    private UUID payoutId;
    private String reference;
    private String description;

    protected WalletTransaction() {
    }

    public WalletTransaction(UUID walletId, Type type, Direction direction, BigDecimal amount, BigDecimal balanceAfter,
                             UUID orderId, UUID paymentId, UUID payoutId, String reference, String description) {
        this.walletId = walletId;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.payoutId = payoutId;
        this.reference = reference;
        this.description = description;
    }

    public UUID getWalletId() { return walletId; }
    public Type getType() { return type; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getPayoutId() { return payoutId; }
    public String getReference() { return reference; }
    public String getDescription() { return description; }
}
