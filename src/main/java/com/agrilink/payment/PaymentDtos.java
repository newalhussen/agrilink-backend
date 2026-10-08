package com.agrilink.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record InitiatePaymentRequest(
            @NotNull PaymentMethod method,
            @Size(max = 40) String payerAccount) {}

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            PaymentStatus status,
            PaymentMethod method,
            String provider,
            String transactionReference,
            BigDecimal amount,
            BigDecimal goodsAmount,
            BigDecimal deliveryAmount,
            BigDecimal platformFeeAmount,
            String currency,
            String checkoutUrl,
            String failureReason,
            Instant initiatedAt,
            Instant paidAt,
            Instant expiresAt,
            BigDecimal refundedAmount) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getProvider(),
                    p.getTransactionReference(), p.getAmount(), p.getGoodsAmount(), p.getDeliveryAmount(),
                    p.getPlatformFeeAmount(), p.getCurrency(), p.getCheckoutUrl(), p.getFailureReason(),
                    p.getInitiatedAt(), p.getPaidAt(), p.getExpiresAt(), p.getRefundedAmount());
        }
    }

    public record AdminPaymentResponse(
            PaymentResponse payment,
            UUID payerId,
            String providerReference,
            BigDecimal releasedFarmerAmount,
            BigDecimal releasedDriverAmount,
            BigDecimal platformFeeRetained,
            Instant releasedAt) {

        public static AdminPaymentResponse from(Payment p) {
            return new AdminPaymentResponse(PaymentResponse.from(p), p.getPayerId(), p.getProviderReference(),
                    p.getReleasedFarmerAmount(), p.getReleasedDriverAmount(), p.getPlatformFeeRetained(),
                    p.getReleasedAt());
        }
    }

    public record PaymentMethodInfo(PaymentMethod method, String label) {}
}
