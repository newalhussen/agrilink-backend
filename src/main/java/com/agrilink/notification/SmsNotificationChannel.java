package com.agrilink.notification;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.user.UserRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMS copy for the notification types listed in agrilink.notifications.sms-types. Delivery alerts also arrive
 * by SMS because many farmers and drivers run basic phones with intermittent data.
 */
@Component
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    private final UserRepository users;
    private final SmsGateway gateway;
    private final Set<NotificationType> smsTypes;

    public SmsNotificationChannel(UserRepository users, SmsGateway gateway, AgriLinkProperties properties) {
        this.users = users;
        this.gateway = gateway;
        this.smsTypes = properties.notifications().smsTypes().stream().map(NotificationType::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void deliver(Delivery d) {
        if (!smsTypes.contains(d.type())) {
            return;
        }
        users.findById(d.userId()).ifPresent(user -> {
            try {
                gateway.send(user.getPhone(), "AgriLink: " + d.title() + ". " + d.body());
            } catch (RuntimeException ex) {
                log.warn("SMS to user {} failed: {}", d.userId(), ex.getMessage());
            }
        });
    }
}
