package com.agrilink.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs the out-of-app channels asynchronously once the business transaction has committed. */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void dispatch(NotificationChannel.Delivery delivery) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(delivery);
            } catch (RuntimeException ex) {
                log.warn("Notification channel {} failed for {}", channel.getClass().getSimpleName(),
                        delivery.notificationId(), ex);
            }
        }
    }
}
