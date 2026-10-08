package com.agrilink.order;

import com.agrilink.auth.OtpCodeRepository;
import com.agrilink.auth.RefreshTokenRepository;
import com.agrilink.marketplace.ListingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Timers of the order lifecycle: unanswered orders expire, unpaid orders expire, delivered orders complete
 * automatically once the buyer's check window passes. Each order is handled in its own transaction so one
 * failure cannot block the rest.
 */
@Component
@ConditionalOnProperty(prefix = "agrilink.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceJob.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final ListingService listingService;
    private final OtpCodeRepository otpCodes;
    private final RefreshTokenRepository refreshTokens;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderMaintenanceJob(OrderRepository orders, OrderService orderService, ListingService listingService,
                               OtpCodeRepository otpCodes, RefreshTokenRepository refreshTokens,
                               TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.listingService = listingService;
        this.otpCodes = otpCodes;
        this.refreshTokens = refreshTokens;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${agrilink.scheduling.order-maintenance-interval:60s}")
    public void run() {
        Instant now = Instant.now(clock);
        process("expire pending", orders.findPendingPastDeadline(now), orderService::expirePending);
        process("expire unpaid", orders.findUnpaidPastDeadline(now), orderService::expireUnpaid);
        process("auto-complete", orders.findDeliveredPastCheckWindow(now), orderService::autoComplete);
    }

    /** Housekeeping that does not need to run every minute. */
    @Scheduled(cron = "0 15 * * * *")
    public void hourly() {
        try {
            int expired = listingService.expireOverdue();
            Instant now = Instant.now(clock);
            tx.executeWithoutResult(s -> {
                otpCodes.deleteOlderThan(now.minus(Duration.ofDays(2)));
                refreshTokens.deleteExpiredBefore(now.minus(Duration.ofDays(7)));
            });
            if (expired > 0) {
                log.info("Expired {} listings past their availability window", expired);
            }
        } catch (RuntimeException ex) {
            log.error("Hourly housekeeping failed", ex);
        }
    }

    private void process(String label, List<UUID> ids, Predicate<UUID> action) {
        for (UUID id : ids) {
            try {
                if (action.test(id)) {
                    log.info("Order maintenance [{}]: {}", label, id);
                }
            } catch (RuntimeException ex) {
                log.error("Order maintenance [{}] failed for order {}", label, id, ex);
            }
        }
    }
}
