package com.agrilink.farmer;

import com.agrilink.farmer.FarmerDtos.FarmerProfileResponse;
import com.agrilink.farmer.FarmerDtos.PublicFarmerResponse;
import com.agrilink.farmer.FarmerDtos.UpdateFarmerProfileRequest;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farmers")
public class FarmerController {

    private final FarmerService farmers;

    public FarmerController(FarmerService farmers) {
        this.farmers = farmers;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('FARMER')")
    public FarmerProfileResponse me() {
        return farmers.getMine(AuthContext.userId());
    }

    /** Partial update; send only the fields that changed. */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('FARMER')")
    public FarmerProfileResponse update(@Valid @RequestBody UpdateFarmerProfileRequest request) {
        return farmers.updateMine(AuthContext.userId(), request);
    }

    /** Public trust profile shown on listings. */
    @GetMapping("/{userId}/public")
    public PublicFarmerResponse publicProfile(@PathVariable UUID userId) {
        return farmers.getPublic(userId);
    }
}
