package com.agrilink.notification;

import java.util.UUID;

/**
 * Out-of-app delivery of a stored notification (push, SMS, later USSD or WhatsApp). The in-app copy is always
 * written first; channels run after the transaction commits and must never throw into the business flow.
 */
public interface NotificationChannel {

    void deliver(Delivery delivery);

    record Delivery(UUID notificationId, UUID userId, NotificationType type, String title, String body,
                    String referenceType, UUID referenceId) {}
}
