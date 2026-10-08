package com.agrilink.rating;

import com.agrilink.common.PageResponse;
import com.agrilink.security.AuthContext;
import com.agrilink.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RatingController {

    private final RatingService ratings;
    private final UserRepository users;

    public RatingController(RatingService ratings, UserRepository users) {
        this.ratings = ratings;
        this.users = users;
    }

    public record CreateRatingRequest(@NotNull RatingTarget target, @Min(1) @Max(5) int score,
                                      @Size(max = 1000) String comment) {}

    /** Rater is shown by first name only on public lists. */
    public record RatingResponse(UUID id, UUID orderId, RatingTarget target, int score, String comment,
                                 String raterName, Instant createdAt) {}

    @PostMapping("/orders/{orderId}/ratings")
    @PreAuthorize("hasAnyRole('BUYER','FARMER','DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RatingResponse create(@PathVariable UUID orderId, @Valid @RequestBody CreateRatingRequest request) {
        Rating r = ratings.rate(AuthContext.userId(), AuthContext.role(), orderId, request.target(), request.score(),
                request.comment());
        return toResponse(List.of(r)).get(0);
    }

    @GetMapping("/orders/{orderId}/ratings")
    @Transactional(readOnly = true)
    public List<RatingResponse> forOrder(@PathVariable UUID orderId) {
        return toResponse(ratings.forOrder(orderId, AuthContext.userId(), AuthContext.role()));
    }

    /** Public reviews of a farmer, buyer or driver. */
    @GetMapping("/users/{userId}/ratings")
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> forUser(@PathVariable UUID userId, Pageable pageable) {
        var page = ratings.forUser(userId, pageable);
        List<RatingResponse> items = toResponse(page.getContent());
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }

    private List<RatingResponse> toResponse(List<Rating> list) {
        Map<UUID, String> names = users.findAllById(list.stream().map(Rating::getRaterId).distinct().toList()).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> firstName(u.getFullName()), (a, b) -> a));
        return list.stream().map(r -> new RatingResponse(r.getId(), r.getOrderId(), r.getTarget(), r.getScore(),
                r.getComment(), names.getOrDefault(r.getRaterId(), "AgriLink user"), r.getCreatedAt())).toList();
    }

    private static String firstName(String fullName) {
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }
}
