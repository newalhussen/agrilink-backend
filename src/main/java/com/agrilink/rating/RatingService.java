package com.agrilink.rating;

import com.agrilink.common.ApiException;
import com.agrilink.order.Order;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One rating per participant per target, only after an order is COMPLETED. Feeds user rating averages. */
@Service
public class RatingService {

    private final RatingRepository ratings;
    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    public RatingService(RatingRepository ratings, OrderRepository orders, ApplicationEventPublisher events) {
        this.ratings = ratings;
        this.orders = orders;
        this.events = events;
    }

    /** What each role may rate: buyers rate the farmer, the delivery and the order; the others rate people. */
    public static Set<RatingTarget> allowedTargets(Role role) {
        return switch (role) {
            case BUYER -> EnumSet.of(RatingTarget.FARMER, RatingTarget.DELIVERY, RatingTarget.ORDER);
            case FARMER -> EnumSet.of(RatingTarget.BUYER, RatingTarget.DRIVER);
            case DRIVER -> EnumSet.of(RatingTarget.BUYER, RatingTarget.FARMER);
            case ADMIN -> EnumSet.noneOf(RatingTarget.class);
        };
    }

    @Transactional
    public Rating rate(UUID raterId, Role role, UUID orderId, RatingTarget target, int score, String comment) {
        Order order = orders.findWithDetailsById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.isParticipant(raterId)) {
            throw ApiException.notFound("Order");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.invalidTransition("You can rate once the order is completed");
        }
        if (!allowedTargets(role).contains(target)) {
            throw ApiException.badRequest("As a " + role + " you cannot rate: " + target);
        }
        User ratee = switch (target) {
            case FARMER -> order.getFarmer();
            case BUYER -> order.getBuyer();
            case DRIVER, DELIVERY -> order.getDriver();
            case ORDER -> null;
        };
        if (target != RatingTarget.ORDER && ratee == null) {
            throw ApiException.badRequest("This order had no one to rate for " + target);
        }
        if (ratings.existsByOrderIdAndRaterIdAndTarget(orderId, raterId, target)) {
            throw ApiException.conflict("You already rated this");
        }
        Rating rating = ratings.save(new Rating(orderId, raterId, ratee == null ? null : ratee.getId(), target, score,
                comment));
        if (ratee != null) {
            ratee.addRating(score);
            events.publishEvent(new RatingCreatedEvent(ratee.getId(), order.getOrderNumber(), score));
        }
        return rating;
    }

    @Transactional(readOnly = true)
    public List<Rating> forOrder(UUID orderId, UUID viewerId, Role role) {
        Order order = orders.findWithDetailsById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (role != Role.ADMIN && !order.isParticipant(viewerId)) {
            throw ApiException.notFound("Order");
        }
        return ratings.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional(readOnly = true)
    public Page<Rating> forUser(UUID userId, Pageable pageable) {
        return ratings.findByRateeIdOrderByCreatedAtDesc(userId, pageable);
    }
}
