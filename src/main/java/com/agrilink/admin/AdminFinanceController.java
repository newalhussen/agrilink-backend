package com.agrilink.admin;

import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.dispute.DisputeDtos.DisputeResponse;
import com.agrilink.dispute.DisputeDtos.ResolveDisputeRequest;
import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import com.agrilink.dispute.DisputeMapper;
import com.agrilink.dispute.DisputeService;
import com.agrilink.order.OrderDtos.OrderResponse;
import com.agrilink.order.OrderMapper;
import com.agrilink.order.OrderService;
import com.agrilink.payment.PaymentDtos.AdminPaymentResponse;
import com.agrilink.payment.PaymentService;
import com.agrilink.payment.PaymentStatus;
import com.agrilink.security.AuthContext;
import com.agrilink.user.Role;
import com.agrilink.wallet.Payout;
import com.agrilink.wallet.PayoutRepository;
import com.agrilink.wallet.PayoutStatus;
import com.agrilink.wallet.WalletController.PayoutResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Money and disputes: payments, withdrawals and the dispute desk. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminFinanceController {

    private final PaymentService payments;
    private final PayoutRepository payouts;
    private final DisputeService disputes;
    private final DisputeMapper disputeMapper;
    private final OrderService orders;
    private final OrderMapper orderMapper;

    public AdminFinanceController(PaymentService payments, PayoutRepository payouts, DisputeService disputes,
                                  DisputeMapper disputeMapper, OrderService orders, OrderMapper orderMapper) {
        this.payments = payments;
        this.payouts = payouts;
        this.disputes = disputes;
        this.disputeMapper = disputeMapper;
        this.orders = orders;
        this.orderMapper = orderMapper;
    }

    public record DisputeDetail(DisputeResponse dispute, OrderResponse order) {}

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> payments(@RequestParam(required = false) PaymentStatus status,
                                                       Pageable pageable) {
        return PageResponse.of(payments.adminList(status, pageable), AdminPaymentResponse::from);
    }

    @GetMapping("/payments/{id}")
    @Transactional(readOnly = true)
    public AdminPaymentResponse payment(@PathVariable UUID id) {
        return AdminPaymentResponse.from(payments.get(id));
    }

    @GetMapping("/payouts")
    @Transactional(readOnly = true)
    public PageResponse<AdminPayout> payouts(@RequestParam(required = false) PayoutStatus status, Pageable pageable) {
        var page = status == null ? payouts.findAllByOrderByCreatedAtDesc(pageable)
                : payouts.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.of(page, p -> new AdminPayout(PayoutResponse.from(p), p.getUserId(),
                p.getDestinationName(), p.getProvider(), p.getProviderReference()));
    }

    public record AdminPayout(PayoutResponse payout, UUID userId, String destinationName, String provider,
                              String providerReference) {}

    @GetMapping("/disputes")
    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> disputes(@RequestParam(required = false) Set<DisputeStatus> status,
                                                  @RequestParam(required = false) Boolean overdue,
                                                  @RequestParam(required = false) String q, Pageable pageable) {
        return PageResponse.of(disputes.adminSearch(status, overdue, q, pageable),
                d -> disputeMapper.toResponse(d, false));
    }

    @GetMapping("/disputes/{id}")
    @Transactional(readOnly = true)
    public DisputeDetail dispute(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        var d = disputes.getForUser(id, me, Role.ADMIN);
        return new DisputeDetail(disputeMapper.toResponse(d, true),
                orderMapper.toResponse(orders.getForUser(d.getOrder().getId(), me, Role.ADMIN), me, Role.ADMIN));
    }

    @PostMapping("/disputes/{id}/assign")
    @Transactional
    public DisputeResponse assign(@PathVariable UUID id) {
        return disputeMapper.toResponse(disputes.assign(AuthContext.userId(), id), true);
    }

    /** Settles the held money (release, full refund or custom split) and closes the order. */
    @PostMapping("/disputes/{id}/resolve")
    @Transactional
    public DisputeResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveDisputeRequest request) {
        var d = disputes.resolve(AuthContext.userId(), id, request.resolution(), request.farmerAmount(),
                request.driverAmount(), request.buyerRefundAmount(), request.notes());
        return disputeMapper.toResponse(d, true);
    }
}
