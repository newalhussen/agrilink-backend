package com.agrilink.driver;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.driver.DriverDtos.DriverProfileResponse;
import com.agrilink.driver.DriverDtos.UpdateDriverProfileRequest;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

    private final DriverProfileRepository profiles;
    private final UserRepository users;
    private final RegionCatalog regions;
    private final Clock clock;

    public DriverService(DriverProfileRepository profiles, UserRepository users, RegionCatalog regions, Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.regions = regions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getMine(UUID userId) {
        return toResponse(requireDriver(userId), profile(userId));
    }

    @Transactional
    public DriverProfileResponse updateMine(UUID userId, UpdateDriverProfileRequest r) {
        User user = requireDriver(userId);
        DriverProfile p = profile(userId);
        if (r.licenseNumber() != null) p.setLicenseNumber(r.licenseNumber().trim());
        if (r.licenseExpiryDate() != null) p.setLicenseExpiryDate(r.licenseExpiryDate());
        if (r.vehicleType() != null) p.setVehicleType(r.vehicleType());
        if (r.vehiclePlate() != null) {
            String plate = r.vehiclePlate().trim();
            if (profiles.existsByVehiclePlateIgnoreCaseAndUserIdNot(plate, userId)) {
                throw ApiException.conflict("This plate number is already registered to another driver");
            }
            p.setVehiclePlate(plate);
        }
        if (r.vehicleMakeModel() != null) p.setVehicleMakeModel(r.vehicleMakeModel().trim());
        if (r.vehicleYear() != null) p.setVehicleYear(r.vehicleYear());
        if (r.capacityKg() != null) p.setCapacityKg(r.capacityKg());
        if (r.refrigerated() != null) p.setRefrigerated(r.refrigerated());
        if (r.regionId() != null) {
            regions.requireExists(r.regionId());
            p.setRegionId(r.regionId());
        }
        if (r.payoutMethod() != null) p.setPayoutMethod(r.payoutMethod());
        if (r.payoutAccountName() != null) p.setPayoutAccountName(r.payoutAccountName().trim());
        if (r.payoutAccountNumber() != null) p.setPayoutAccountNumber(r.payoutAccountNumber().trim());
        return toResponse(user, p);
    }

    /** Drivers choose AVAILABLE or OFFLINE; BUSY is managed by the delivery workflow only. */
    @Transactional
    public DriverProfileResponse setAvailability(UUID userId, DriverAvailability availability) {
        User user = requireDriver(userId);
        DriverProfile p = profile(userId);
        if (availability == DriverAvailability.BUSY) {
            throw ApiException.badRequest("BUSY is set automatically while you have active deliveries");
        }
        if (availability == DriverAvailability.AVAILABLE) {
            if (!user.isVerified()) {
                throw new ApiException(ErrorCode.VERIFICATION_REQUIRED,
                        "Your account must be verified before you can go available");
            }
            if (!p.hasVehicle()) {
                throw ApiException.badRequest("Add your vehicle type, plate number and capacity first");
            }
        }
        p.setAvailability(availability);
        return toResponse(user, p);
    }

    @Transactional
    public void updateLocation(UUID userId, double latitude, double longitude) {
        requireDriver(userId);
        profile(userId).updateLocation(latitude, longitude, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public DriverProfile requireProfile(UUID userId) {
        return profile(userId);
    }

    private User requireDriver(UUID userId) {
        return users.findById(userId).filter(u -> u.getRole() == Role.DRIVER)
                .orElseThrow(() -> ApiException.forbidden("Only drivers can use this endpoint"));
    }

    private DriverProfile profile(UUID userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Driver profile"));
    }

    private DriverProfileResponse toResponse(User user, DriverProfile p) {
        return new DriverProfileResponse(user.getId(), p.getLicenseNumber(), p.getLicenseExpiryDate(),
                p.getVehicleType(), p.getVehiclePlate(), p.getVehicleMakeModel(), p.getVehicleYear(),
                p.getCapacityKg(), p.isRefrigerated(), p.getRegionId(), regions.nameOf(p.getRegionId()),
                p.getAvailability(), p.getCurrentLatitude(), p.getCurrentLongitude(), p.getLocationUpdatedAt(),
                p.getPayoutMethod(), p.getPayoutAccountName(), p.getPayoutAccountNumber(),
                p.getCompletedDeliveries(), user.getVerificationStatus());
    }
}
