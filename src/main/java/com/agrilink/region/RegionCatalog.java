package com.agrilink.region;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** In-memory lookup of the (small, rarely changing) region table. */
@Component
public class RegionCatalog {

    private final RegionRepository repository;
    private final AtomicReference<Map<UUID, Region>> cache = new AtomicReference<>();

    public RegionCatalog(RegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Region> all() {
        return List.copyOf(load().values()).stream()
                .sorted((a, b) -> a.getNameEn().compareToIgnoreCase(b.getNameEn()))
                .toList();
    }

    public String nameOf(UUID regionId) {
        if (regionId == null) {
            return null;
        }
        Region region = load().get(regionId);
        return region == null ? null : region.getNameEn();
    }

    public void requireExists(UUID regionId) {
        if (!load().containsKey(regionId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unknown region: " + regionId);
        }
    }

    public void invalidate() {
        cache.set(null);
    }

    private Map<UUID, Region> load() {
        Map<UUID, Region> current = cache.get();
        if (current == null) {
            current = repository.findByActiveTrueOrderByNameEnAsc().stream()
                    .collect(Collectors.toUnmodifiableMap(Region::getId, Function.identity()));
            cache.set(current);
        }
        return current;
    }
}
