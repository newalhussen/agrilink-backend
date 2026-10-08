package com.agrilink.delivery;

import com.agrilink.common.AddressDto;
import com.agrilink.delivery.DeliveryDtos.DeliveryEventResponse;
import com.agrilink.delivery.DeliveryDtos.DeliveryResponse;
import com.agrilink.delivery.DeliveryDtos.JobResponse;
import com.agrilink.delivery.DeliveryDtos.LoadLine;
import com.agrilink.delivery.DeliveryDtos.Stop;
import com.agrilink.order.Order;
import com.agrilink.order.OrderDtos.PartyView;
import com.agrilink.order.OrderStatus;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {

    private final RegionCatalog regions;

    public DeliveryMapper(RegionCatalog regions) {
        this.regions = regions;
    }

    /** Job board card: town and region only, coordinates rounded to ~1 km so exact farm gates stay private. */
    public JobResponse toJob(Delivery d) {
        Order o = d.getOrder();
        return new JobResponse(d.getId(), o.getOrderNumber(), d.getStatus(), coarse(d.getPickupAddress()),
                coarse(d.getDropoffAddress()), d.getDistanceKm(), d.getTotalWeightKg(), d.getDriverFee(),
                d.getScheduledPickupDate(), load(o), d.getCreatedAt());
    }

    public DeliveryResponse toResponse(Delivery d, UUID viewerId, Role role) {
        Order o = d.getOrder();
        boolean admin = role == Role.ADMIN;
        boolean farmer = admin || o.getFarmer().getId().equals(viewerId);
        boolean buyer = admin || o.getBuyer().getId().equals(viewerId);
        boolean driverViewer = d.getDriver() != null && d.getDriver().getId().equals(viewerId);
        boolean showAddresses = admin || farmer || buyer || driverViewer;
        Stop pickup = showAddresses ? new Stop(AddressDto.from(d.getPickupAddress(), regions),
                d.getPickupContactName(), d.getPickupContactPhone())
                : new Stop(coarse(d.getPickupAddress()), null, null);
        Stop dropoff = showAddresses ? new Stop(AddressDto.from(d.getDropoffAddress(), regions),
                d.getDropoffContactName(), d.getDropoffContactPhone())
                : new Stop(coarse(d.getDropoffAddress()), null, null);
        PartyView driver = d.getDriver() == null ? null : party(d.getDriver());
        return new DeliveryResponse(d.getId(), o.getId(), o.getOrderNumber(), o.getStatus().name(), d.getStatus(),
                pickup, dropoff, d.getDistanceKm(), d.getTotalWeightKg(), d.getDriverFee(),
                d.getScheduledPickupDate(), load(o), driver,
                farmer ? d.getPickupCode() : null, farmer ? d.getPickupQrToken() : null,
                buyer ? d.getDeliveryCode() : null, buyer ? d.getDeliveryQrToken() : null,
                d.getPickedUpWeightKg(), d.getPickedUpCrateCount(), d.getPickupNote(), d.getDeliveryNote(),
                d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(),
                admin ? d.getPickupFailedAttempts() : 0, admin ? d.getDeliveryFailedAttempts() : 0,
                driverActions(d, o, driverViewer));
    }

    public DeliveryEventResponse toEvent(DeliveryEvent e) {
        return new DeliveryEventResponse(e.getType().name(), e.getLatitude(), e.getLongitude(), e.getNote(),
                e.getCreatedAt());
    }

    private List<String> driverActions(Delivery d, Order o, boolean driverViewer) {
        List<String> actions = new ArrayList<>();
        if (driverViewer) {
            switch (d.getStatus()) {
                case ASSIGNED -> {
                    actions.add("RELEASE");
                    if (o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.READY_FOR_PICKUP) {
                        actions.add("CONFIRM_PICKUP");
                    }
                }
                case PICKED_UP -> {
                    actions.add("START_TRIP");
                    actions.add("CONFIRM_DELIVERY");
                    actions.add("SEND_LOCATION");
                }
                case IN_TRANSIT -> {
                    actions.add("CONFIRM_DELIVERY");
                    actions.add("SEND_LOCATION");
                }
                default -> { }
            }
        } else if (d.getStatus() == DeliveryStatus.OPEN) {
            actions.add("ACCEPT");
        }
        return actions;
    }

    private AddressDto coarse(com.agrilink.common.Address a) {
        AddressDto full = AddressDto.from(a, regions);
        if (full == null) {
            return null;
        }
        Double lat = full.latitude() == null ? null : Math.round(full.latitude() * 100.0) / 100.0;
        Double lon = full.longitude() == null ? null : Math.round(full.longitude() * 100.0) / 100.0;
        return new AddressDto(full.regionId(), full.regionName(), full.zone(), full.woreda(), full.town(), null, lat, lon);
    }

    private List<LoadLine> load(Order o) {
        return o.getItems().stream()
                .map(i -> new LoadLine(i.getProductName(), i.getQuantity(), i.getUnit(), i.weightKg())).toList();
    }

    private PartyView party(User u) {
        return new PartyView(u.getId(), u.getFullName(), null, u.getPhone(), u.getRatingAverage(),
                u.getRatingCount(), u.isVerified());
    }
}
