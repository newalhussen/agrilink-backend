package com.agrilink.notification;

import java.util.Map;

/**
 * Outbound push notification to one device token (FCM for Android, APNs for iOS in later phases).
 * The default implementation only logs.
 */
public interface PushGateway {

    void send(String deviceToken, DevicePlatform platform, String title, String body, Map<String, String> data);
}
