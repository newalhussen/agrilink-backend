package com.agrilink.wallet;

import com.agrilink.common.PageResponse;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Farmer and driver earnings: balance, ledger and withdrawals ("Held for you" / "Ready to withdraw"). */
@RestController
@RequestMapping("/api/v1/wallet")
@PreAuthorize("hasAnyRole('FARMER','DRIVER')")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    public record WalletResponse(BigDecimal balance, BigDecimal heldForRelease, String currency) {}

    public record TransactionResponse(UUID id, WalletTransaction.Type type, WalletTransaction.Direction direction,
                                      BigDecimal amount, BigDecimal balanceAfter, UUID orderId, String description,
                                      Instant createdAt) {}

    public record WithdrawRequest(@NotNull @DecimalMin(value = "10.00", message = "Minimum withdrawal is ETB 10") BigDecimal amount,
                                  PayoutMethod method, @Size(max = 40) String accountNumber,
                                  @Size(max = 150) String accountName) {}

    public record PayoutResponse(UUID id, BigDecimal amount, PayoutMethod method, String destinationAccount,
                                 PayoutStatus status, String failureReason, Instant createdAt, Instant processedAt) {
        public static PayoutResponse from(Payout p) {
            return new PayoutResponse(p.getId(), p.getAmount(), p.getMethod(), p.getDestinationAccount(),
                    p.getStatus(), p.getFailureReason(), p.getCreatedAt(), p.getProcessedAt());
        }
    }

    @GetMapping
    public WalletResponse summary() {
        WalletService.Summary s = wallets.summary(AuthContext.userId());
        return new WalletResponse(s.balance(), s.heldForRelease(), s.currency());
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(Pageable pageable) {
        return PageResponse.of(wallets.transactions(AuthContext.userId(), pageable),
                t -> new TransactionResponse(t.getId(), t.getType(), t.getDirection(), t.getAmount(),
                        t.getBalanceAfter(), t.getOrderId(), t.getDescription(), t.getCreatedAt()));
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutResponse withdraw(@Valid @RequestBody WithdrawRequest request) {
        return PayoutResponse.from(wallets.withdraw(AuthContext.userId(), request.amount(), request.method(),
                request.accountNumber(), request.accountName()));
    }

    @GetMapping("/withdrawals")
    public PageResponse<PayoutResponse> withdrawals(Pageable pageable) {
        return PageResponse.of(wallets.payoutsFor(AuthContext.userId(), pageable), PayoutResponse::from);
    }
}
