package com.agrilink.driver;

import com.agrilink.driver.DriverDtos.AvailabilityRequest;
import com.agrilink.driver.DriverDtos.DriverProfileResponse;
import com.agrilink.driver.DriverDtos.LocationRequest;
import com.agrilink.driver.DriverDtos.UpdateDriverProfileRequest;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@PreAuthorize("hasRole('DRIVER')")
public class DriverController {

    private final DriverService drivers;

    public DriverController(DriverService drivers) {
        this.drivers = drivers;
    }

    @GetMapping("/me")
    public DriverProfileResponse me() {
        return drivers.getMine(AuthContext.userId());
    }

    /** Partial update; send only the fields that changed. */
    @PatchMapping("/me")
    public DriverProfileResponse update(@Valid @RequestBody UpdateDriverProfileRequest request) {
        return drivers.updateMine(AuthContext.userId(), request);
    }

    @PutMapping("/me/availability")
    public DriverProfileResponse availability(@Valid @RequestBody AvailabilityRequest request) {
        return drivers.setAvailability(AuthContext.userId(), request.availability());
    }

    /** Periodic position ping while the app is open. Trip tracking pings go to /deliveries/{id}/location. */
    @PostMapping("/me/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void location(@Valid @RequestBody LocationRequest request) {
        drivers.updateLocation(AuthContext.userId(), request.latitude(), request.longitude());
    }
}
