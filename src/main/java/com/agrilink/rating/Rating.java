package com.agrilink.rating;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ratings")
public class Rating extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "rater_id", nullable = false)
    private UUID raterId;
    @Column(name = "ratee_id")
    private UUID rateeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatingTarget target;
    @Column(nullable = false)
    private int score;
    private String comment;

    protected Rating() {
    }

    public Rating(UUID orderId, UUID raterId, UUID rateeId, RatingTarget target, int score, String comment) {
        this.orderId = orderId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.target = target;
        this.score = score;
        this.comment = comment;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getRaterId() { return raterId; }
    public UUID getRateeId() { return rateeId; }
    public RatingTarget getTarget() { return target; }
    public int getScore() { return score; }
    public String getComment() { return comment; }
}
