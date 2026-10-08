package com.agrilink.rating;

import java.util.UUID;

public record RatingCreatedEvent(UUID rateeId, String orderNumber, int score) {
}
