package com.agrilink.wallet;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.common.Money;
import com.agrilink.driver.DriverProfile;
import com.agrilink.driver.DriverProfileRepository;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentProvider;
import com.agrilink.payment.PaymentProvider.PayoutRequest;
import com.agrilink.payment.PaymentProvider.PayoutResult;
import com.agrilink.payment.PaymentProviderRegistry;
import com.agrilink.payment.ProviderStatus;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wallet ledger and withdrawals for farmers and drivers. */
@Service
public class WalletService {

    static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("10.00");
    private static final Set<OrderStatus> HELD_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED, OrderStatus.DISPUTED);

    public record Summary(BigDecimal balance, BigDecimal heldForRelease, String currency) {}

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final PayoutRepository payouts;
    private final UserRepository users;
    private final FarmerProfileRepository farmerProfiles;
    private final DriverProfileRepository driverProfiles;
    private final OrderRepository orders;
    private final PaymentProviderRegistry providers;
    private final Clock clock;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions, PayoutRepository payouts,
                         UserRepository users, FarmerProfileRepository farmerProfiles,
                         DriverProfileRepository driverProfiles, OrderRepository orders,
                         PaymentProviderRegistry providers, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.payouts = payouts;
        this.users = users;
        this.farmerProfiles = farmerProfiles;
        this.driverProfiles = driverProfiles;
        this.orders = orders;
        this.providers = providers;
        this.clock = clock;
    }

    @Transactional
    public Wallet ensureWallet(UUID userId) {
        return wallets.findByUserId(userId).orElseGet(() -> wallets.save(new Wallet(userId)));
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        Wallet wallet = wallets.findByUserId(userId).orElse(null);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        BigDecimal held = switch (user.getRole()) {
            case FARMER -> orders.sumSubtotalForFarmer(userId, HELD_STATUSES);
            case DRIVER -> orders.sumDeliveryFeeForDriver(userId, HELD_STATUSES);
            default -> Money.ZERO;
        };
        return new Summary(wallet == null ? Money.ZERO : wallet.getBalance(), Money.scale(held), Money.CURRENCY);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> transactions(UUID userId, Pageable pageable) {
        Wallet wallet = wallets.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return Page.empty(pageable);
        }
        return transactions.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payout> payoutsFor(UUID userId, Pageable pageable) {
        return payouts.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /** Adds earned money to a wallet (called when escrow is released). */
    @Transactional
    public WalletTransaction creditEscrow(UUID userId, BigDecimal amount, UUID orderId, UUID paymentId,
                                          String description) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        Wallet wallet = lockOrCreate(userId);
        wallet.credit(amount);
        return transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.ESCROW_RELEASE,
                WalletTransaction.Direction.CREDIT, amount, wallet.getBalance(), orderId, paymentId, null,
                null, description));
    }

    /** Withdraws to the given destination, or to the payout account saved on the user's profile. */
    @Transactional
    public Payout withdraw(UUID userId, BigDecimal amount, PayoutMethod method, String account, String accountName) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() != Role.FARMER && user.getRole() != Role.DRIVER) {
            throw ApiException.forbidden("Only farmers and drivers have a wallet");
        }
        if (!user.isVerified()) {
            throw new ApiException(ErrorCode.VERIFICATION_REQUIRED, "Your account must be verified to withdraw money");
        }
        BigDecimal value = Money.scale(amount);
        if (value.compareTo(MIN_WITHDRAWAL) < 0) {
            throw ApiException.badRequest("The minimum withdrawal is ETB " + MIN_WITHDRAWAL.toPlainString());
        }
        Destination destination = resolveDestination(user, method, account, accountName);
        Wallet wallet = lockOrCreate(userId);
        wallet.debit(value);
        PaymentProvider provider = providers.payoutProvider();
        Payout payout = payouts.save(new Payout(wallet.getId(), userId, value, destination.method(),
                destination.account(), destination.name(), provider.code()));
        transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.WITHDRAWAL,
                WalletTransaction.Direction.DEBIT, value, wallet.getBalance(), null, null, payout.getId(),
                null, "Withdrawal to " + destination.method()));

        Instant now = Instant.now(clock);
        PayoutResult result = provider.payout(new PayoutRequest("PO-" + payout.getId(), value, destination.method(),
                destination.account(), destination.name()));
        if (result.status() == ProviderStatus.SUCCEEDED) {
            payout.markPaid(result.providerReference(), now);
        } else if (result.status() == ProviderStatus.PENDING) {
            payout.markProcessing(result.providerReference());
        } else {
            payout.markFailed(result.failureReason(), now);
            wallet.credit(value);
            transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.WITHDRAWAL_REVERSAL,
                    WalletTransaction.Direction.CREDIT, value, wallet.getBalance(), null, null, payout.getId(),
                    null, "Withdrawal failed, money returned to wallet"));
        }
        return payout;
    }

    private Wallet lockOrCreate(UUID userId) {
        return wallets.lockByUserId(userId).orElseGet(() -> {
            wallets.saveAndFlush(new Wallet(userId));
            return wallets.lockByUserId(userId).orElseThrow();
        });
    }

    private record Destination(PayoutMethod method, String account, String name) {}

    private Destination resolveDestination(User user, PayoutMethod method, String account, String name) {
        PayoutMethod resolvedMethod = method;
        String resolvedAccount = account;
        String resolvedName = name;
        if (user.getRole() == Role.FARMER) {
            FarmerProfile p = farmerProfiles.findByUserId(user.getId()).orElse(null);
            if (p != null) {
                resolvedMethod = resolvedMethod != null ? resolvedMethod : p.getPayoutMethod();
                resolvedAccount = resolvedAccount != null ? resolvedAccount : p.getPayoutAccountNumber();
                resolvedName = resolvedName != null ? resolvedName : p.getPayoutAccountName();
            }
        } else {
            DriverProfile p = driverProfiles.findByUserId(user.getId()).orElse(null);
            if (p != null) {
                resolvedMethod = resolvedMethod != null ? resolvedMethod : p.getPayoutMethod();
                resolvedAccount = resolvedAccount != null ? resolvedAccount : p.getPayoutAccountNumber();
                resolvedName = resolvedName != null ? resolvedName : p.getPayoutAccountName();
            }
        }
        if (resolvedMethod == null || resolvedAccount == null || resolvedAccount.isBlank()) {
            throw ApiException.badRequest("Add a payout account (telebirr, CBE Birr or bank) first");
        }
        return new Destination(resolvedMethod, resolvedAccount.trim(), resolvedName);
    }
}
