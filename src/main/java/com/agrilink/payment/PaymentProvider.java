package com.agrilink.payment;

import com.agrilink.wallet.PayoutMethod;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Port to a payment service provider (Chapa, Telebirr, CBE Birr...). The order workflow only ever talks to
 * {@link PaymentService}, which talks to this interface, so adding a real provider means adding one
 * implementation and no changes to orders, deliveries or disputes.
 */
public interface PaymentProvider {

    /** Stable lower-case code stored on payments and used in the webhook URL, e.g. "mock", "chapa". */
    String code();

    boolean supports(PaymentMethod method);

    ChargeResult charge(ChargeRequest request);

    RefundResult refund(RefundRequest request);

    PayoutResult payout(PayoutRequest request);

    /**
     * Verifies the authenticity of an asynchronous callback and converts it to a neutral event.
     * Return empty when the signature is invalid or the payload is not understood.
     */
    Optional<WebhookEvent> parseWebhook(Map<String, String> headers, String body);

    record ChargeRequest(String transactionReference, BigDecimal amount, String currency, PaymentMethod method,
                         String payerAccount, String description) {}

    record ChargeResult(ProviderStatus status, String providerReference, String checkoutUrl, String failureReason) {}

    record RefundRequest(String transactionReference, String providerReference, BigDecimal amount, String reason) {}

    record RefundResult(ProviderStatus status, String providerReference, String failureReason) {}

    record PayoutRequest(String reference, BigDecimal amount, PayoutMethod method, String accountNumber,
                         String accountName) {}

    record PayoutResult(ProviderStatus status, String providerReference, String failureReason) {}

    record WebhookEvent(String transactionReference, ProviderStatus status, String providerReference,
                        String failureReason) {}
}
