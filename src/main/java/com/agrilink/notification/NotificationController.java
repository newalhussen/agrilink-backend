package com.agrilink.notification;

import com.agrilink.common.PageResponse;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    public record NotificationResponse(UUID id, NotificationType type, String title, String body,
                                       String referenceType, UUID referenceId, boolean read, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getReferenceType(),
                    n.getReferenceId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record RegisterDeviceRequest(@NotBlank @Size(max = 512) String token, @NotNull DevicePlatform platform) {}

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   Pageable pageable) {
        return PageResponse.of(notifications.list(AuthContext.userId(), unreadOnly, pageable),
                NotificationResponse::from);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notifications.unreadCount(AuthContext.userId()));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        notifications.markRead(AuthContext.userId(), id);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notifications.markAllRead(AuthContext.userId()));
    }

    /** Registers (or re-binds) this device's FCM/APNs token for push notifications. */
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        notifications.registerDevice(AuthContext.userId(), request.token(), request.platform());
    }

    @DeleteMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterDevice(@RequestParam String token) {
        notifications.unregisterDevice(AuthContext.userId(), token);
    }
}
