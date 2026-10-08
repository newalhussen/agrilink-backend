package com.agrilink.payment;

import com.agrilink.common.ApiException;
import com.agrilink.security.AuthContext;
import com.agrilink.user.Role;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates the asynchronous provider callback while developing clients with
 * agrilink.payment.mock.auto-confirm=false. Only exists under the "dev" profile.
 */
@RestController
@RequestMapping("/api/v1/dev/payments")
@Profile("dev")
public class DevPaymentController {

    private final PaymentService payments;

    public DevPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@PathVariable UUID id) {
        payments.handleProviderResult(authorised(id).getTransactionReference(), ProviderStatus.SUCCEEDED, null);
    }

    @PostMapping("/{id}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable UUID id) {
        payments.handleProviderResult(authorised(id).getTransactionReference(), ProviderStatus.FAILED,
                "Simulated failure");
    }

    private Payment authorised(UUID id) {
        Payment payment = payments.get(id);
        if (!payment.getPayerId().equals(AuthContext.userId()) && AuthContext.role() != Role.ADMIN) {
            throw ApiException.notFound("Payment");
        }
        return payment;
    }
}
