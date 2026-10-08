package com.agrilink.payment;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.config.AgriLinkProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves which provider handles a payment method, honouring {@code agrilink.payment.default-provider}. */
@Component
public class PaymentProviderRegistry {

    private final List<PaymentProvider> providers;
    private final String defaultProvider;

    public PaymentProviderRegistry(List<PaymentProvider> providers, AgriLinkProperties properties) {
        this.providers = providers;
        this.defaultProvider = properties.payment().defaultProvider();
    }

    public PaymentProvider forMethod(PaymentMethod method) {
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(defaultProvider) && p.supports(method))
                .findFirst()
                .or(() -> providers.stream().filter(p -> p.supports(method)).findFirst())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_FAILED,
                        "No payment provider is configured for " + method));
    }

    public PaymentProvider byCode(String code) {
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Payment provider"));
    }

    public PaymentProvider payoutProvider() {
        return providers.stream().filter(p -> p.code().equalsIgnoreCase(defaultProvider)).findFirst()
                .orElseGet(() -> providers.get(0));
    }
}
