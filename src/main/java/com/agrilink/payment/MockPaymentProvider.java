package com.agrilink.payment;

import com.agrilink.config.AgriLinkProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Development provider. No money moves.
 * Account numbers ending in 0000 fail, so clients can exercise the "payment failed" screens.
 * With agrilink.payment.mock.auto-confirm=true charges succeed immediately; otherwise they stay
 * PENDING until a webhook (or the dev confirm endpoint) completes them.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    private final AgriLinkProperties.Payment.Mock config;
    private final JsonMapper mapper;

    public MockPaymentProvider(AgriLinkProperties properties, JsonMapper mapper) {
        this.config = properties.payment().mock();
        this.mapper = mapper;
    }

    @Override
    public String code() {
        return "mock";
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return true;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String ref = "MOCK-" + UUID.randomUUID();
        if (shouldFail(request.payerAccount())) {
            log.info("[mock-payment] charge {} FAILED", request.transactionReference());
            return new ChargeResult(ProviderStatus.FAILED, ref, null, "Mock provider: insufficient funds");
        }
        ProviderStatus status = config.autoConfirm() ? ProviderStatus.SUCCEEDED : ProviderStatus.PENDING;
        log.info("[mock-payment] charge {} {} ETB {}", request.transactionReference(), status, request.amount());
        return new ChargeResult(status, ref, null, null);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("[mock-payment] refund {} ETB {}", request.transactionReference(), request.amount());
        return new RefundResult(ProviderStatus.SUCCEEDED, "MOCK-RF-" + UUID.randomUUID(), null);
    }

    @Override
    public PayoutResult payout(PayoutRequest request) {
        if (shouldFail(request.accountNumber())) {
            return new PayoutResult(ProviderStatus.FAILED, null, "Mock provider: payout account inactive");
        }
        log.info("[mock-payment] payout {} ETB {} to {}", request.reference(), request.amount(),
                request.accountNumber());
        return new PayoutResult(ProviderStatus.SUCCEEDED, "MOCK-PO-" + UUID.randomUUID(), null);
    }

    /** Expects header X-Webhook-Secret and JSON with transactionReference, status and optional failureReason. */
    @Override
    public Optional<WebhookEvent> parseWebhook(Map<String, String> headers, String body) {
        String secret = headers.get("x-webhook-secret");
        if (secret == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                config.webhookSecret().getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        JsonNode json = mapper.readTree(body);
        String reference = json.path("transactionReference").asString("");
        if (reference.isBlank()) {
            return Optional.empty();
        }
        ProviderStatus status;
        try {
            status = ProviderStatus.valueOf(json.path("status").asString("").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(new WebhookEvent(reference, status, json.path("providerReference").asString(null),
                json.path("failureReason").asString(null)));
    }

    private static boolean shouldFail(String account) {
        return account != null && account.endsWith("0000");
    }
}
