package com.agrilink.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrilink.TestSupport;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.order.Order;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentProvider.ChargeResult;
import com.agrilink.payment.PaymentProvider.RefundResult;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.wallet.WalletService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock OrderRepository orders;
    @Mock PaymentProviderRegistry providers;
    @Mock PaymentProvider provider;
    @Mock WalletService wallets;
    @Mock ApplicationEventPublisher events;

    PaymentService service;
    User buyer;
    User farmer;
    User driver;
    Order order;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, orders, providers, wallets, events, TestSupport.properties(),
                TestSupport.clock());
        buyer = TestSupport.user(Role.BUYER, "+251922000001", "Selam");
        farmer = TestSupport.user(Role.FARMER, "+251911000001", "Tolosa");
        driver = TestSupport.user(Role.DRIVER, "+251933000001", "Abebe");
        order = TestSupport.withId(new Order("AL-20481", buyer, farmer));
        order.setSubtotalAmount(new BigDecimal("18400.00"));
        order.setDeliveryFee(new BigDecimal("3200.00"));
        order.setPlatformFee(new BigDecimal("368.00"));
        order.setTotalAmount(new BigDecimal("21968.00"));
        order.setDriver(driver);
        order.setPaymentDeadline(TestSupport.NOW.plusSeconds(1800));
        com.agrilink.order.TestOrders.setStatus(order, OrderStatus.ACCEPTED);

        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(providers.forMethod(any())).thenReturn(provider);
        when(providers.byCode(anyString())).thenReturn(provider);
        when(provider.code()).thenReturn("mock");
        when(payments.save(any(Payment.class))).thenAnswer(i -> TestSupport.withId(i.getArgument(0)));
    }

    @Test
    void successfulChargeIsHeldInEscrowForTheFullTotal() {
        when(provider.charge(any())).thenReturn(new ChargeResult(ProviderStatus.SUCCEEDED, "P-1", null, null));

        Payment p = service.initiate(buyer.getId(), order.getId(), PaymentMethod.TELEBIRR, "0922000001");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.HELD);
        assertThat(p.getAmount()).isEqualByComparingTo("21968.00");
        assertThat(p.getTransactionReference()).startsWith("AGP-");
        assertThat(statusSequence()).containsExactly(PaymentStatus.PENDING, PaymentStatus.HELD);
    }

    @Test
    void failedChargeReportsFailureAndKeepsOrderRetryable() {
        when(provider.charge(any())).thenReturn(new ChargeResult(ProviderStatus.FAILED, "P-2", null, "no funds"));

        Payment p = service.initiate(buyer.getId(), order.getId(), PaymentMethod.TELEBIRR, "0922000000");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureReason()).isEqualTo("no funds");
        assertThat(statusSequence()).containsExactly(PaymentStatus.PENDING, PaymentStatus.FAILED);
    }

    @Test
    void asynchronousProviderLeavesPaymentPending() {
        when(provider.charge(any())).thenReturn(new ChargeResult(ProviderStatus.PENDING, "P-3", "https://pay", null));
        Payment p = service.initiate(buyer.getId(), order.getId(), PaymentMethod.CBE_BIRR, null);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getCheckoutUrl()).isEqualTo("https://pay");
    }

    @Test
    void onlyTheBuyerOfAnAcceptedOrderCanPay() {
        assertThatThrownBy(() -> service.initiate(farmer.getId(), order.getId(), PaymentMethod.TELEBIRR, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        com.agrilink.order.TestOrders.setStatus(order, OrderStatus.PENDING);
        assertThatThrownBy(() -> service.initiate(buyer.getId(), order.getId(), PaymentMethod.TELEBIRR, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(provider, never()).charge(any());
    }

    @Test
    void paymentAfterTheDeadlineIsRefused() {
        order.setPaymentDeadline(TestSupport.NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.initiate(buyer.getId(), order.getId(), PaymentMethod.TELEBIRR, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("closed");
    }

    @Test
    void releasePaysFarmerAndDriverAndKeepsThePlatformFee() {
        Payment held = heldPayment();
        when(payments.findFirstByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(Optional.of(held));

        service.releaseEscrow(order);

        verify(wallets).creditEscrow(eq(farmer.getId()), eq(new BigDecimal("18400.00")), eq(order.getId()), any(),
                anyString());
        verify(wallets).creditEscrow(eq(driver.getId()), eq(new BigDecimal("3200.00")), eq(order.getId()), any(),
                anyString());
        assertThat(held.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(held.getPlatformFeeRetained()).isEqualByComparingTo("368.00");
        assertThat(held.getReleasedFarmerAmount()).isEqualByComparingTo("18400.00");
        verify(events).publishEvent(any(EscrowReleasedEvent.class));
    }

    @Test
    void fullRefundReturnsEverythingToTheBuyer() {
        Payment held = heldPayment();
        when(payments.findFirstByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(Optional.of(held));
        when(provider.refund(any())).thenReturn(new RefundResult(ProviderStatus.SUCCEEDED, "R-1", null));

        service.refundHeld(order, "Cancelled");

        assertThat(held.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(held.getRefundedAmount()).isEqualByComparingTo("21968.00");
        verify(wallets, never()).creditEscrow(any(), any(), any(), any(), anyString());
    }

    @Test
    void partialSettlementSplitsMoneyAndKeepsTheRemainderAsFee() {
        Payment held = heldPayment();
        when(payments.findFirstByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(Optional.of(held));
        when(provider.refund(any())).thenReturn(new RefundResult(ProviderStatus.SUCCEEDED, "R-2", null));

        // The design's dispute example: farmer 18,032, driver 440, refund 2,760.
        service.settle(order, new BigDecimal("18032"), new BigDecimal("440"), new BigDecimal("2760"), "Short weight");

        assertThat(held.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(held.getRefundedAmount()).isEqualByComparingTo("2760.00");
        assertThat(held.getPlatformFeeRetained()).isEqualByComparingTo("736.00");
        ArgumentCaptor<BigDecimal> credits = ArgumentCaptor.forClass(BigDecimal.class);
        verify(wallets, org.mockito.Mockito.times(2)).creditEscrow(any(), credits.capture(), any(), any(), anyString());
        assertThat(credits.getAllValues()).containsExactly(new BigDecimal("18032.00"), new BigDecimal("440.00"));
    }

    @Test
    void settlementCannotExceedTheHeldAmount() {
        Payment held = heldPayment();
        when(payments.findFirstByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(Optional.of(held));
        assertThatThrownBy(() -> service.settle(order, new BigDecimal("20000"), new BigDecimal("3000"),
                BigDecimal.ZERO, "x")).isInstanceOf(ApiException.class).hasMessageContaining("exceeds");
        verify(wallets, never()).creditEscrow(any(), any(), any(), any(), anyString());
    }

    @Test
    void latePaymentOnACancelledChargeIsRefundedAutomatically() {
        Payment cancelled = new Payment(order.getId(), buyer.getId(), PaymentMethod.TELEBIRR, "mock", "AGP-LATE",
                order.getSubtotalAmount(), order.getDeliveryFee(), order.getPlatformFee(), null, TestSupport.NOW, null);
        TestSupport.withId(cancelled);
        cancelled.markCancelled("window closed");
        when(payments.findByTransactionReference("AGP-LATE")).thenReturn(Optional.of(cancelled));
        when(payments.lockById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        when(provider.refund(any())).thenReturn(new RefundResult(ProviderStatus.SUCCEEDED, "R-3", null));

        service.handleProviderResult("AGP-LATE", ProviderStatus.SUCCEEDED, null);

        verify(provider).refund(any());
        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void duplicateWebhookIsIgnored() {
        Payment held = heldPayment();
        when(payments.findByTransactionReference("AGP-DUP")).thenReturn(Optional.of(held));
        when(payments.lockById(held.getId())).thenReturn(Optional.of(held));

        service.handleProviderResult("AGP-DUP", ProviderStatus.SUCCEEDED, null);

        assertThat(held.getStatus()).isEqualTo(PaymentStatus.HELD);
        verify(events, never()).publishEvent(any(PaymentStatusChangedEvent.class));
    }

    private Payment heldPayment() {
        Payment p = TestSupport.withId(new Payment(order.getId(), buyer.getId(), PaymentMethod.TELEBIRR, "mock",
                "AGP-TEST", order.getSubtotalAmount(), order.getDeliveryFee(), order.getPlatformFee(), null,
                TestSupport.NOW, null));
        p.markHeld(TestSupport.NOW);
        return p;
    }

    private List<PaymentStatus> statusSequence() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(PaymentStatusChangedEvent.class::isInstance)
                .map(e -> ((PaymentStatusChangedEvent) e).to()).toList();
    }
}
