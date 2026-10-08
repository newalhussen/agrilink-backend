package com.agrilink.notification;

import com.agrilink.common.ApiException;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates localised in-app notifications and hands them to the out-of-app channels after commit.
 * Callers never wait on push or SMS and never see their failures.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final UserRepository users;
    private final NotificationMessages messages;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, DeviceTokenRepository devices,
                               UserRepository users, NotificationMessages messages,
                               ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.devices = devices;
        this.users = users;
        this.messages = messages;
        this.events = events;
        this.clock = clock;
    }

    /** Renders the template for the recipient's language and stores + dispatches it. */
    @Transactional
    public void send(UUID userId, NotificationType type, String referenceType, UUID referenceId, Object... args) {
        if (userId == null) {
            return;
        }
        users.findById(userId).ifPresent(user -> {
            String title = messages.render(user.language(), "notif." + type + ".title", args);
            String body = messages.render(user.language(), "notif." + type + ".body", args);
            store(user, type, title, body, referenceType, referenceId);
        });
    }

    /** Free-text notification (admin announcements). */
    @Transactional
    public void sendCustom(UUID userId, String title, String body) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        store(user, NotificationType.SYSTEM, title, body, null, null);
    }

    @Transactional
    public int sendCustomToUsers(Collection<UUID> userIds, String title, String body) {
        int sent = 0;
        for (UUID id : userIds) {
            users.findById(id).ifPresent(u -> store(u, NotificationType.SYSTEM, title, body, null, null));
            sent++;
        }
        return sent;
    }

    private void store(User user, NotificationType type, String title, String body, String refType, UUID refId) {
        Notification n = notifications.save(new Notification(user.getId(), type, title, body, refType, refId));
        events.publishEvent(new NotificationChannel.Delivery(n.getId(), user.getId(), type, n.getTitle(), n.getBody(),
                refType, refId));
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead(Instant.now(clock));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now(clock));
    }

    @Transactional
    public void registerDevice(UUID userId, String token, DevicePlatform platform) {
        Instant now = Instant.now(clock);
        devices.findByToken(token).ifPresentOrElse(d -> d.rebind(userId, platform, now),
                () -> devices.save(new DeviceToken(userId, token, platform, now)));
    }

    @Transactional
    public void unregisterDevice(UUID userId, String token) {
        devices.deleteByTokenAndUserId(token, userId);
    }

    @Transactional(readOnly = true)
    public Page<Notification> adminLog(Pageable pageable) {
        return notifications.findAllByOrderByCreatedAtDesc(pageable);
    }
}
