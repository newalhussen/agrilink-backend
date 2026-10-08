package com.agrilink.wallet;

import com.agrilink.common.ApiException;
import com.agrilink.common.BaseEntity;
import com.agrilink.common.ErrorCode;
import com.agrilink.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Spendable balance of a farmer or driver. Credited when escrow is released, debited by withdrawals. */
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Column(nullable = false)
    private BigDecimal balance = Money.ZERO;
    @Column(nullable = false)
    private String currency = Money.CURRENCY;

    protected Wallet() {
    }

    public Wallet(UUID userId) {
        this.userId = userId;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Your wallet balance is too low for this withdrawal");
        }
        balance = balance.subtract(amount);
    }

    public UUID getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
}
