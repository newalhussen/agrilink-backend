package com.agrilink.payment;

import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.order.Order;
import com.agrilink.order.OrderRepository;
import com.agrilink.payment.PaymentDtos.InitiatePaymentRequest;
import com.agrilink.payment.PaymentDtos.PaymentMethodInfo;
import com.agrilink.payment.PaymentDtos.PaymentResponse;
import com.agrilink.security.AuthContext;
import com.agrilink.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService payments;
    private final OrderRepository orders;

    public PaymentController(PaymentService payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }

    @GetMapping("/payments/methods")
    public List<PaymentMethodInfo> methods() {
        return List.of(
                new PaymentMethodInfo(PaymentMethod.TELEBIRR, "telebirr"),
                new PaymentMethodInfo(PaymentMethod.CBE_BIRR, "CBE Birr"),
                new PaymentMethodInfo(PaymentMethod.BANK_TRANSFER, "Bank transfer"),
                new PaymentMethodInfo(PaymentMethod.CARD, "Card"));
    }

    /** Buyer pays an accepted order. The money is held in escrow until delivery is confirmed. */
    @PostMapping("/orders/{orderId}/payments")
    @PreAuthorize("hasRole('BUYER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiate(@PathVariable UUID orderId, @Valid @RequestBody InitiatePaymentRequest request) {
        Payment payment = payments.initiate(AuthContext.userId(), orderId, request.method(), request.payerAccount());
        return PaymentResponse.from(payment);
    }

    @GetMapping("/orders/{orderId}/payments")
    @Transactional(readOnly = true)
    public List<PaymentResponse> forOrder(@PathVariable UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        UUID me = AuthContext.userId();
        if (!order.getBuyer().getId().equals(me) && !AuthContext.isAdmin()) {
            throw ApiException.notFound("Order");
        }
        return payments.forOrder(orderId).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/payments/mine")
    @PreAuthorize("hasRole('BUYER')")
    public PageResponse<PaymentResponse> mine(Pageable pageable) {
        return PageResponse.of(payments.mine(AuthContext.userId(), pageable), PaymentResponse::from);
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        Payment payment = payments.get(id);
        if (!payment.getPayerId().equals(AuthContext.userId()) && AuthContext.role() != Role.ADMIN) {
            throw ApiException.notFound("Payment");
        }
        return PaymentResponse.from(payment);
    }

    /**
     * Provider callback. Unauthenticated by design; each provider implementation verifies its own signature.
     * Always answers 200 for authentic events (even duplicates) so the provider stops retrying.
     */
    @PostMapping("/payments/webhooks/{provider}")
    public ResponseEntity<Void> webhook(@PathVariable String provider, @RequestBody byte[] body,
                                        HttpServletRequest request) throws IOException {
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        boolean accepted = payments.processWebhook(provider, headers, new String(body, StandardCharsets.UTF_8));
        return ResponseEntity.status(accepted ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).build();
    }
}
