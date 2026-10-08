package com.agrilink.dispute;

import com.agrilink.common.PageResponse;
import com.agrilink.dispute.DisputeDtos.AddEvidenceRequest;
import com.agrilink.dispute.DisputeDtos.DisputeResponse;
import com.agrilink.dispute.DisputeDtos.OpenDisputeRequest;
import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class DisputeController {

    private final DisputeService disputes;
    private final DisputeMapper mapper;

    public DisputeController(DisputeService disputes, DisputeMapper mapper) {
        this.disputes = disputes;
        this.mapper = mapper;
    }

    /** "Report a problem": freezes the order and holds the money until an agent decides. */
    @PostMapping("/orders/{orderId}/disputes")
    @PreAuthorize("hasAnyRole('BUYER','FARMER','DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse open(@PathVariable UUID orderId, @Valid @RequestBody OpenDisputeRequest request) {
        Dispute d = disputes.open(AuthContext.userId(), AuthContext.role(), orderId, request.type(),
                request.description(), request.receivedQuantityKg(), request.evidenceFileIds());
        return mapper.toResponse(d, true);
    }

    @GetMapping("/orders/{orderId}/disputes")
    @Transactional(readOnly = true)
    public List<DisputeResponse> forOrder(@PathVariable UUID orderId) {
        return disputes.forOrder(orderId, AuthContext.userId(), AuthContext.role()).stream()
                .map(d -> mapper.toResponse(d, true)).toList();
    }

    @GetMapping("/disputes")
    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> mine(@RequestParam(required = false) Set<DisputeStatus> status,
                                              Pageable pageable) {
        return PageResponse.of(disputes.mine(AuthContext.userId(), status, pageable), d -> mapper.toResponse(d, false));
    }

    @GetMapping("/disputes/{id}")
    @Transactional(readOnly = true)
    public DisputeResponse get(@PathVariable UUID id) {
        return mapper.toResponse(disputes.getForUser(id, AuthContext.userId(), AuthContext.role()), true);
    }

    @PostMapping("/disputes/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse addEvidence(@PathVariable UUID id, @Valid @RequestBody AddEvidenceRequest request) {
        disputes.addEvidence(AuthContext.userId(), AuthContext.role(), id, request.kind(), request.fileId(),
                request.note());
        return mapper.toResponse(disputes.getForUser(id, AuthContext.userId(), AuthContext.role()), true);
    }
}
