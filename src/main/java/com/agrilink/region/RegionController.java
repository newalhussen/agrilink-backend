package com.agrilink.region;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionCatalog regions;

    public RegionController(RegionCatalog regions) {
        this.regions = regions;
    }

    public record RegionResponse(UUID id, String code, String nameEn, String nameAm, String nameOm) {}

    @GetMapping
    public List<RegionResponse> list() {
        return regions.all().stream()
                .map(r -> new RegionResponse(r.getId(), r.getCode(), r.getNameEn(), r.getNameAm(), r.getNameOm()))
                .toList();
    }
}
