package com.agrilink.notification;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends a push to every registered device of the user through the replaceable {@link PushGateway}. */
@Component
public class PushNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);

    private final DeviceTokenRepository devices;
    private final PushGateway gateway;

    public PushNotificationChannel(DeviceTokenRepository devices, PushGateway gateway) {
        this.devices = devices;
        this.gateway = gateway;
    }

    @Override
    public void deliver(Delivery d) {
        Map<String, String> data = new HashMap<>();
        data.put("type", d.type().name());
        data.put("notificationId", d.notificationId().toString());
        if (d.referenceType() != null && d.referenceId() != null) {
            data.put("referenceType", d.referenceType());
            data.put("referenceId", d.referenceId().toString());
        }
        for (DeviceToken device : devices.findByUserId(d.userId())) {
            try {
                gateway.send(device.getToken(), device.getPlatform(), d.title(), d.body(), data);
            } catch (RuntimeException ex) {
                log.warn("Push to device {} failed: {}", device.getId(), ex.getMessage());
            }
        }
    }
}
