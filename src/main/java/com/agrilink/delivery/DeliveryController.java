package com.agrilink.delivery;

import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.delivery.DeliveryDtos.DeliverRequest;
import com.agrilink.delivery.DeliveryDtos.DeliveryEventResponse;
import com.agrilink.delivery.DeliveryDtos.DeliveryResponse;
import com.agrilink.delivery.DeliveryDtos.JobResponse;
import com.agrilink.delivery.DeliveryDtos.LocationPingRequest;
import com.agrilink.delivery.DeliveryDtos.PickupRequest;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
public class DeliveryController {

    private final DeliveryService deliveries;
    private final DeliveryMapper mapper;

    public DeliveryController(DeliveryService deliveries, DeliveryMapper mapper) {
        this.deliveries = deliveries;
        this.mapper = mapper;
    }

    /** Driver job board: open jobs that fit the driver's vehicle capacity. */
    @GetMapping("/deliveries/available")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> available(@RequestParam(required = false) UUID regionId,
                                               @RequestParam(required = false) BigDecimal minFee,
                                               Pageable pageable) {
        return PageResponse.of(deliveries.availableJobs(AuthContext.userId(), regionId, minFee, pageable),
                mapper::toJob);
    }

    /** The driver's own jobs; pass status (repeatable) to filter, e.g. status=DELIVERED for history. */
    @GetMapping("/deliveries/mine")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> mine(@RequestParam(required = false) Set<DeliveryStatus> status,
                                               Pageable pageable) {
        UUID me = AuthContext.userId();
        return PageResponse.of(deliveries.myDeliveries(me, status, pageable),
                d -> mapper.toResponse(d, me, AuthContext.role()));
    }

    @GetMapping("/deliveries/{id}")
    @Transactional(readOnly = true)
    public DeliveryResponse get(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(deliveries.getForUser(id, me, AuthContext.role()), me, AuthContext.role());
    }

    @GetMapping("/orders/{orderId}/delivery")
    @Transactional(readOnly = true)
    public DeliveryResponse forOrder(@PathVariable UUID orderId) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(deliveries.getForOrder(orderId, me, AuthContext.role()), me, AuthContext.role());
    }

    /** Tracking timeline (status steps and GPS pings) visible to the buyer, farmer, driver and admins. */
    @GetMapping("/deliveries/{id}/events")
    @Transactional(readOnly = true)
    public List<DeliveryEventResponse> events(@PathVariable UUID id) {
        deliveries.getForUser(id, AuthContext.userId(), AuthContext.role());
        return deliveries.timeline(id).stream().map(mapper::toEvent).toList();
    }

    @PostMapping("/deliveries/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public DeliveryResponse accept(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(deliveries.accept(me, id), me, AuthContext.role());
    }

    @PostMapping("/deliveries/{id}/release")
    @PreAuthorize("hasRole('DRIVER')")
    public DeliveryResponse release(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(deliveries.release(me, id), me, AuthContext.role());
    }

    /** Confirms pickup with the farmer's code (or scanned QR token), the weighed quantity and optional photos. */
    @PostMapping("/deliveries/{id}/pickup")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(noRollbackFor = ApiException.class)
    public DeliveryResponse pickup(@PathVariable UUID id, @Valid @RequestBody PickupRequest request) {
        UUID me = AuthContext.userId();
        Delivery d = deliveries.pickup(me, id, request.code(), request.weighedKg(), request.crateCount(),
                request.note(), request.evidenceFileIds());
        return mapper.toResponse(d, me, AuthContext.role());
    }

    @PostMapping("/deliveries/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public DeliveryResponse start(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(deliveries.startTrip(me, id), me, AuthContext.role());
    }

    /** GPS ping during a trip; shows up on the buyer's tracking screen. */
    @PostMapping("/deliveries/{id}/location")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void location(@PathVariable UUID id, @Valid @RequestBody LocationPingRequest request) {
        deliveries.recordLocation(AuthContext.userId(), id, request.latitude(), request.longitude(), request.note());
    }

    /** Confirms delivery with the buyer's code (or scanned QR token). Starts the buyer's check window. */
    @PostMapping("/deliveries/{id}/deliver")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(noRollbackFor = ApiException.class)
    public DeliveryResponse deliver(@PathVariable UUID id, @Valid @RequestBody DeliverRequest request) {
        UUID me = AuthContext.userId();
        Delivery d = deliveries.deliver(me, id, request.code(), request.note(), request.evidenceFileIds());
        return mapper.toResponse(d, me, AuthContext.role());
    }
}
