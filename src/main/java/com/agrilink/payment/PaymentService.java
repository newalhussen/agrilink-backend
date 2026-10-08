package com.agrilink.payment;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.common.Money;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.order.Order;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentProvider.ChargeRequest;
import com.agrilink.payment.PaymentProvider.ChargeResult;
import com.agrilink.payment.PaymentProvider.RefundRequest;
import com.agrilink.payment.PaymentProvider.RefundResult;
import com.agrilink.payment.PaymentProvider.WebhookEvent;
import com.agrilink.wallet.WalletService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrow payments. The order workflow depends on this class and never on a concrete provider.
 * Lock order is always order row first, then payment row.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentProviderRegistry providers;
    private final WalletService wallets;
    private final ApplicationEventPublisher events;
    private final AgriLinkProperties.Orders orderProps;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, PaymentProviderRegistry providers,
                          WalletService wallets, ApplicationEventPublisher events, AgriLinkProperties properties,
                          Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.providers = providers;
        this.wallets = wallets;
        this.events = events;
        this.orderProps = properties.orders();
        this.clock = clock;
    }

    /**
     * Starts a charge for an accepted order. Returns the existing pending payment when the buyer retries
     * while one is still outstanding, so a double tap never charges twice.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Payment initiate(UUID buyerId, UUID orderId, PaymentMethod method, String payerAccount) {
        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw ApiException.notFound("Order");
        }
        Instant now = Instant.now(clock);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            return payments.findFirstByOrderIdAndStatusIn(orderId, EnumSet.of(PaymentStatus.PENDING))
                    .orElseThrow(() -> ApiException.conflict("A payment is already being processed for this order"));
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw ApiException.invalidTransition("This order cannot be paid right now (status "
                    + order.getStatus() + ")");
        }
        if (order.getPaymentDeadline() != null && !order.getPaymentDeadline().isAfter(now)) {
            throw ApiException.conflict("The payment window for this order has closed");
        }
        PaymentProvider provider = providers.forMethod(method);
        Instant expires = now.plus(orderProps.paymentWindow());
        if (order.getPaymentDeadline() != null && order.getPaymentDeadline().isBefore(expires)) {
            expires = order.getPaymentDeadline();
        }
        Payment payment = payments.save(new Payment(orderId, buyerId, method, provider.code(), newReference(),
                order.getSubtotalAmount(), order.getDeliveryFee(), order.getPlatformFee(), payerAccount, now, expires));
        publish(payment, order, null, PaymentStatus.PENDING, null);

        ChargeResult result = provider.charge(new ChargeRequest(payment.getTransactionReference(),
                payment.getAmount(), payment.getCurrency(), method, payerAccount,
                "AgriLink order " + order.getOrderNumber()));
        payment.setProviderResult(result.providerReference(), result.checkoutUrl());
        switch (result.status()) {
            case SUCCEEDED -> hold(payment, order);
            case FAILED -> fail(payment, order, result.failureReason());
            case PENDING -> { }
        }
        return payment;
    }

    /** Applies an asynchronous provider outcome (webhook or the dev confirm endpoint). Idempotent. */
    @Transactional
    public void handleProviderResult(String transactionReference, ProviderStatus outcome, String failureReason) {
        Payment peek = payments.findByTransactionReference(transactionReference)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        Order order = orders.lockById(peek.getOrderId()).orElseThrow(() -> ApiException.notFound("Order"));
        Payment payment = payments.lockById(peek.getId()).orElseThrow();
        if (payment.getStatus() == PaymentStatus.PENDING) {
            if (outcome == ProviderStatus.SUCCEEDED) {
                hold(payment, order);
            } else if (outcome == ProviderStatus.FAILED) {
                fail(payment, order, failureReason);
            }
            return;
        }
        boolean closed = payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.EXPIRED
                || payment.getStatus() == PaymentStatus.FAILED;
        if (closed && outcome == ProviderStatus.SUCCEEDED) {
            // The customer paid after we gave up on the charge: give the money straight back.
            log.warn("Late successful payment {} on closed payment; refunding", transactionReference);
            PaymentProvider provider = providers.byCode(payment.getProvider());
            RefundResult refund = provider.refund(new RefundRequest(payment.getTransactionReference(),
                    payment.getProviderReference(), payment.getAmount(), "Payment arrived after order closed"));
            if (refund.status() != ProviderStatus.FAILED) {
                PaymentStatus before = payment.getStatus();
                payment.markReleased(Money.ZERO, Money.ZERO, Money.ZERO, payment.getAmount(), Instant.now(clock));
                publish(payment, order, before, PaymentStatus.REFUNDED, "Late payment refunded");
            }
        }
    }

    /** Called when a funded order completes: farmer gets goods value, driver the delivery fee, platform keeps its fee. */
    @Transactional
    public Payment releaseEscrow(Order order) {
        Payment payment = requireHeld(order);
        BigDecimal farmerAmount = order.getSubtotalAmount();
        BigDecimal driverAmount = order.getDeliveryFee();
        settleInternal(payment, order, farmerAmount, driverAmount, Money.ZERO, "Delivery confirmed");
        return payment;
    }

    /** Returns the whole payment to the buyer (cancellation before pickup, or dispute decided for the buyer). */
    @Transactional
    public Payment refundHeld(Order order, String reason) {
        Payment payment = requireHeld(order);
        settleInternal(payment, order, Money.ZERO, Money.ZERO, payment.getAmount(), reason);
        return payment;
    }

    /** Dispute settlement with explicit amounts. Whatever is not allocated is kept as platform fee. */
    @Transactional
    public Payment settle(Order order, BigDecimal farmerAmount, BigDecimal driverAmount, BigDecimal buyerRefund,
                          String reason) {
        Payment payment = requireHeld(order);
        BigDecimal farmer = Money.scale(farmerAmount);
        BigDecimal driver = Money.scale(driverAmount);
        BigDecimal refund = Money.scale(buyerRefund);
        if (farmer.signum() < 0 || driver.signum() < 0 || refund.signum() < 0) {
            throw ApiException.badRequest("Amounts cannot be negative");
        }
        if (farmer.add(driver).add(refund).compareTo(payment.getAmount()) > 0) {
            throw ApiException.badRequest("Settlement exceeds the held amount of ETB "
                    + payment.getAmount().toPlainString());
        }
        if (driver.signum() > 0 && order.getDriver() == null) {
            throw ApiException.badRequest("This order has no driver to pay");
        }
        settleInternal(payment, order, farmer, driver, refund, reason);
        return payment;
    }

    /** Cancels an unconfirmed charge (order cancelled or expired before the buyer paid). */
    @Transactional
    public void cancelPending(Order order, String reason) {
        payments.findFirstByOrderIdAndStatusIn(order.getId(), EnumSet.of(PaymentStatus.PENDING)).ifPresent(p -> {
            PaymentStatus before = p.getStatus();
            p.markCancelled(reason);
            publish(p, order, before, PaymentStatus.CANCELLED, reason);
        });
    }

    @Transactional(readOnly = true)
    public List<Payment> forOrder(UUID orderId) {
        return payments.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
    }

    @Transactional(readOnly = true)
    public Page<Payment> mine(UUID buyerId, Pageable pageable) {
        return payments.findByPayerIdOrderByCreatedAtDesc(buyerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payment> adminList(PaymentStatus status, Pageable pageable) {
        return status == null ? payments.findAllByOrderByCreatedAtDesc(pageable)
                : payments.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Payment> activePayment(UUID orderId) {
        return payments.findFirstByOrderIdAndStatusIn(orderId, EnumSet.of(PaymentStatus.PENDING, PaymentStatus.HELD));
    }

    private void hold(Payment payment, Order order) {
        PaymentStatus before = payment.getStatus();
        payment.markHeld(Instant.now(clock));
        publish(payment, order, before, PaymentStatus.HELD, null);
    }

    private void fail(Payment payment, Order order, String reason) {
        PaymentStatus before = payment.getStatus();
        payment.markFailed(reason);
        publish(payment, order, before, PaymentStatus.FAILED, reason);
    }

    private Payment requireHeld(Order order) {
        return payments.findFirstByOrderIdAndStatusIn(order.getId(), EnumSet.of(PaymentStatus.HELD))
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_FAILED,
                        "No held payment found for order " + order.getOrderNumber()));
    }

    private void settleInternal(Payment payment, Order order, BigDecimal farmerAmount, BigDecimal driverAmount,
                                BigDecimal refund, String reason) {
        Instant now = Instant.now(clock);
        PaymentStatus before = payment.getStatus();
        if (refund.signum() > 0) {
            PaymentProvider provider = providers.byCode(payment.getProvider());
            RefundResult result = provider.refund(new RefundRequest(payment.getTransactionReference(),
                    payment.getProviderReference(), refund, reason));
            if (result.status() == ProviderStatus.FAILED) {
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "The refund could not be processed: "
                        + result.failureReason());
            }
        }
        String description = "Order " + order.getOrderNumber();
        if (farmerAmount.signum() > 0) {
            wallets.creditEscrow(order.getFarmer().getId(), farmerAmount, order.getId(), payment.getId(), description);
        }
        if (driverAmount.signum() > 0) {
            wallets.creditEscrow(order.getDriver().getId(), driverAmount, order.getId(), payment.getId(),
                    description + " delivery");
        }
        BigDecimal retained = payment.getAmount().subtract(farmerAmount).subtract(driverAmount).subtract(refund);
        payment.markReleased(farmerAmount, driverAmount, retained, refund, now);
        publish(payment, order, before, payment.getStatus(), reason);
        if (farmerAmount.signum() > 0 || driverAmount.signum() > 0) {
            events.publishEvent(new EscrowReleasedEvent(order.getId(), order.getOrderNumber(),
                    order.getFarmer().getId(), farmerAmount,
                    order.getDriver() == null ? null : order.getDriver().getId(), driverAmount));
        }
    }

    private void publish(Payment payment, Order order, PaymentStatus from, PaymentStatus to, String reason) {
        events.publishEvent(new PaymentStatusChangedEvent(payment.getId(), order.getId(), order.getOrderNumber(),
                order.getBuyer().getId(), order.getFarmer().getId(), from, to, payment.getAmount(), reason));
    }

    private static String newReference() {
        StringBuilder sb = new StringBuilder("AGP-");
        for (int i = 0; i < 12; i++) {
            sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Parses and applies a webhook for the named provider; returns false when the signature check fails. */
    @Transactional
    public boolean processWebhook(String providerCode, java.util.Map<String, String> headers, String body) {
        PaymentProvider provider = providers.byCode(providerCode);
        java.util.Optional<WebhookEvent> event = provider.parseWebhook(headers, body);
        if (event.isEmpty()) {
            return false;
        }
        WebhookEvent e = event.get();
        handleProviderResult(e.transactionReference(), e.status(), e.failureReason());
        return true;
    }
}
