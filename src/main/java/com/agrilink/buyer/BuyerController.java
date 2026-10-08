package com.agrilink.buyer;

import com.agrilink.buyer.BuyerDtos.BuyerProfileResponse;
import com.agrilink.buyer.BuyerDtos.UpdateBuyerProfileRequest;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buyers")
@PreAuthorize("hasRole('BUYER')")
public class BuyerController {

    private final BuyerService buyers;

    public BuyerController(BuyerService buyers) {
        this.buyers = buyers;
    }

    @GetMapping("/me")
    public BuyerProfileResponse me() {
        return buyers.get(AuthContext.userId());
    }

    /** Partial update; send only the fields that changed. */
    @PatchMapping("/me")
    public BuyerProfileResponse update(@Valid @RequestBody UpdateBuyerProfileRequest request) {
        return buyers.update(AuthContext.userId(), request);
    }
}
